package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;
import com.o2o.carpooling.common.domain.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
class FileController {

    private final FileObjectService fileObjectService;

    FileController(FileObjectService fileObjectService) {
        this.fileObjectService = fileObjectService;
    }

    @PostMapping("/mock-upload")
    FileObject mockUpload(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody MockUploadRequest request
    ) {
        return fileObjectService.createMockPrivateObject(resolveUserId(currentUserId, request.ownerId()), request.objectName(), request.contentType());
    }

    @PostMapping("/presign-upload")
    PresignedUpload presignUpload(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestHeader(value = "X-User-Roles", required = false) String currentRoles,
        @RequestBody MockUploadRequest request
    ) {
        return fileObjectService.presignUpload(principal(currentUserId, currentRoles, request.ownerId()),
            request.objectName(), request.contentType(), request.contentLength());
    }

    @PostMapping("/{fileId}/complete")
    FileObject completeUpload(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestHeader(value = "X-User-Roles", required = false) String currentRoles,
        @PathVariable String fileId,
        @RequestBody(required = false) CompleteUploadRequest request
    ) {
        String fallbackOwnerId = request == null ? null : request.ownerId();
        return fileObjectService.completeUpload(principal(currentUserId, currentRoles, fallbackOwnerId), fileId);
    }

    @GetMapping("/{fileId}/presign-download")
    PresignedDownload presignDownload(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestHeader(value = "X-User-Roles", required = false) String currentRoles,
        @PathVariable String fileId
    ) {
        return fileObjectService.presignDownload(principal(currentUserId, currentRoles, null), fileId);
    }

    private FileAccessPrincipal principal(String currentUserId, String currentRoles, String fallbackUserId) {
        return new FileAccessPrincipal(resolveUserId(currentUserId, fallbackUserId), roles(currentRoles));
    }

    private String resolveUserId(String currentUserId, String fallbackUserId) {
        return StringUtils.hasText(currentUserId) ? currentUserId : fallbackUserId;
    }

    private Set<UserRole> roles(String header) {
        if (!StringUtils.hasText(header)) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(UserRole::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    record MockUploadRequest(String ownerId, String objectName, String contentType, Long contentLength) {
    }

    record CompleteUploadRequest(String ownerId) {
    }
}
