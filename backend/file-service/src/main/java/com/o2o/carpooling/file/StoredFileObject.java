package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;

import java.time.Instant;

record StoredFileObject(
    FileObject fileObject,
    FileUploadStatus uploadStatus,
    Instant uploadExpiresAt,
    Instant uploadedAt
) {
}
