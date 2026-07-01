package com.o2o.carpooling.file;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class MinioObjectStorageClient implements ObjectStorageClient {

    private final MinioClient minioClient;

    MinioObjectStorageClient(FileStorageProperties properties) {
        this.minioClient = MinioClient.builder()
            .endpoint(properties.getEndpoint())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .build();
    }

    @Override
    public String presignPutObject(String bucket, String objectKey, String contentType, Duration expiry) {
        try {
            ensureBucketExists(bucket);
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .object(objectKey)
                .expiry((int) expiry.toSeconds())
                .build());
        } catch (Exception exception) {
            throw new IllegalStateException("failed to create minio upload url", exception);
        }
    }

    @Override
    public String presignGetObject(String bucket, String objectKey, Duration expiry) {
        try {
            ensureBucketExists(bucket);
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectKey)
                .expiry((int) expiry.toSeconds())
                .build());
        } catch (Exception exception) {
            throw new IllegalStateException("failed to create minio download url", exception);
        }
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        return objectSize(bucket, objectKey) >= 0;
    }

    @Override
    public long objectSize(String bucket, String objectKey) {
        try {
            ensureBucketExists(bucket);
            return minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build()).size();
        } catch (Exception exception) {
            return -1;
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
