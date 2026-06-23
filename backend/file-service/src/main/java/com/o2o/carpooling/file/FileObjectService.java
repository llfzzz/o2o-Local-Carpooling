package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
class FileObjectService {

    private final FileObjectRepository fileObjectRepository;
    private final String bucket;

    FileObjectService(FileObjectRepository fileObjectRepository, @Value("${minio.bucket}") String bucket) {
        this.fileObjectRepository = fileObjectRepository;
        this.bucket = bucket;
    }

    FileObject createPrivateObject(String ownerId, String objectName, String contentType) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId is required");
        }
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("objectName is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        FileObject fileObject = new FileObject(
            "file-" + UUID.randomUUID(),
            ownerId,
            bucket,
            objectName,
            contentType,
            true,
            Instant.now()
        );
        fileObjectRepository.save(fileObject, sha256(ownerId + "|" + objectName + "|" + contentType));
        return fileObject;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to calculate object digest", exception);
        }
    }
}
