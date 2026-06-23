package com.o2o.carpooling.common.domain;

import java.time.Instant;

public record FileObject(
    String fileObjectId,
    String ownerId,
    String bucket,
    String objectName,
    String contentType,
    boolean privateObject,
    Instant createdAt
) {
}
