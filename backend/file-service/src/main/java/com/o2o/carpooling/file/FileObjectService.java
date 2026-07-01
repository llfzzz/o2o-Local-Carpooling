package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
class FileObjectService {

    private final FileObjectRepository fileObjectRepository;
    private final ObjectStorageClient objectStorageClient;
    private final FileStorageProperties properties;
    private final AuditClient auditClient;

    FileObjectService(
        FileObjectRepository fileObjectRepository,
        ObjectStorageClient objectStorageClient,
        FileStorageProperties properties,
        AuditClient auditClient
    ) {
        this.fileObjectRepository = fileObjectRepository;
        this.objectStorageClient = objectStorageClient;
        this.properties = properties;
        this.auditClient = auditClient;
    }

    FileObject createMockPrivateObject(String ownerId, String objectName, String contentType) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId is required");
        }
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("objectName is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        requireAllowedContentType(contentType);
        FileObject fileObject = new FileObject(
            "file-" + UUID.randomUUID(),
            ownerId,
            properties.getBucket(),
            objectName,
            contentType,
            true,
            Instant.now()
        );
        fileObjectRepository.save(fileObject, sha256(ownerId + "|" + objectName + "|" + contentType));
        return fileObject;
    }

    @Transactional
    PresignedUpload presignUpload(FileAccessPrincipal principal, String objectName, String contentType, Long contentLength) {
        validatePrincipal(principal);
        validateObjectRequest(objectName, contentType, contentLength);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getPresignUploadExpiry());
        String objectKey = generatedObjectKey(principal.userId(), objectName);
        FileObject fileObject = new FileObject(
            "file-" + UUID.randomUUID(),
            principal.userId(),
            properties.getBucket(),
            objectKey,
            contentType,
            true,
            now
        );
        fileObjectRepository.savePendingUpload(fileObject, sha256(principal.userId() + "|" + objectKey + "|" + contentType), expiresAt);
        String uploadUrl = objectStorageClient.presignPutObject(fileObject.bucket(), fileObject.objectName(), fileObject.contentType(), properties.getPresignUploadExpiry());
        return new PresignedUpload(fileObject, uploadUrl, "PUT", Map.of("Content-Type", contentType), expiresAt);
    }

    @Transactional
    FileObject completeUpload(FileAccessPrincipal principal, String fileObjectId) {
        StoredFileObject stored = findAuthorized(principal, fileObjectId);
        FileObject fileObject = stored.fileObject();
        long size = objectStorageClient.objectSize(fileObject.bucket(), fileObject.objectName());
        if (size < 0) {
            throw new IllegalStateException("file object " + fileObjectId + " not found in object storage");
        }
        // Authoritative size enforcement: the client cannot bypass the limit by lying at presign time.
        if (size > properties.getMaxUploadBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "stored object exceeds the max upload size of " + properties.getMaxUploadBytes() + " bytes");
        }
        fileObjectRepository.markAvailable(fileObjectId, Instant.now());
        auditClient.append(principal.userId(), "FILE_UPLOAD_COMPLETED", "FILE", fileObjectId, Map.of("objectKey", fileObject.objectName()));
        return fileObjectRepository.findByFileObjectId(fileObjectId).orElseThrow();
    }

    PresignedDownload presignDownload(FileAccessPrincipal principal, String fileObjectId) {
        StoredFileObject stored = findAuthorized(principal, fileObjectId);
        if (stored.uploadStatus() != FileUploadStatus.AVAILABLE) {
            throw new IllegalStateException("file object " + fileObjectId + " is not available");
        }
        FileObject fileObject = stored.fileObject();
        Duration expiry = properties.getPresignDownloadExpiry();
        PresignedDownload download = new PresignedDownload(
            fileObject,
            objectStorageClient.presignGetObject(fileObject.bucket(), fileObject.objectName(), expiry),
            Instant.now().plus(expiry)
        );
        auditDownload(principal, fileObject);
        return download;
    }

    private void auditDownload(FileAccessPrincipal principal, FileObject fileObject) {
        auditClient.append(principal.userId(), "FILE_DOWNLOAD_PRESIGNED", "FILE", fileObject.fileObjectId(), Map.of("objectKey", fileObject.objectName()));
    }

    private StoredFileObject findAuthorized(FileAccessPrincipal principal, String fileObjectId) {
        validatePrincipal(principal);
        StoredFileObject stored = fileObjectRepository.findStoredByFileObjectId(fileObjectId)
            .orElseThrow(() -> new IllegalArgumentException("file object not found: " + fileObjectId));
        if (!stored.fileObject().ownerId().equals(principal.userId()) && !principal.hasOperatorAccess()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FILE_ACCESS_DENIED", "file access not allowed");
        }
        return stored;
    }

    private void validateObjectRequest(String objectName, String contentType, Long contentLength) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("objectName is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        requireAllowedContentType(contentType);
        if (contentLength != null && (contentLength <= 0 || contentLength > properties.getMaxUploadBytes())) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "file size must be between 1 and " + properties.getMaxUploadBytes() + " bytes");
        }
    }

    private void requireAllowedContentType(String contentType) {
        if (!properties.getAllowedContentTypes().contains(contentType.trim().toLowerCase(Locale.ROOT))) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_CONTENT_TYPE_NOT_ALLOWED",
                "content type not allowed: " + contentType);
        }
    }

    private void validatePrincipal(FileAccessPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId().isBlank()) {
            throw new IllegalArgumentException("ownerId is required");
        }
    }

    private String generatedObjectKey(String ownerId, String objectName) {
        String filename = Paths.get(objectName).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        if (filename.isBlank()) {
            filename = "upload.bin";
        }
        return "uploads/" + ownerId + "/" + UUID.randomUUID() + "-" + filename;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to calculate object digest", exception);
        }
    }
}
