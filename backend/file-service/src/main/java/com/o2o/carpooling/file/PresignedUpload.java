package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;

import java.time.Instant;
import java.util.Map;

record PresignedUpload(
    FileObject fileObject,
    String uploadUrl,
    String method,
    Map<String, String> requiredHeaders,
    Instant expiresAt
) {
}
