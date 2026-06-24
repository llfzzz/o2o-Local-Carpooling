package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;

import java.time.Instant;

record PresignedDownload(FileObject fileObject, String downloadUrl, Instant expiresAt) {
}
