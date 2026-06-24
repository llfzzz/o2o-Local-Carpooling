package com.o2o.carpooling.file;

import java.time.Duration;

interface ObjectStorageClient {
    String presignPutObject(String bucket, String objectKey, String contentType, Duration expiry);

    String presignGetObject(String bucket, String objectKey, Duration expiry);

    boolean objectExists(String bucket, String objectKey);
}
