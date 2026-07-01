package com.o2o.carpooling.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "minio")
class FileStorageProperties {

    private String endpoint = "http://127.0.0.1:9000";
    private String bucket = "o2o-carpooling-private";
    private String accessKey = "";
    private String secretKey = "";
    private Duration presignUploadExpiry = Duration.ofMinutes(15);
    private Duration presignDownloadExpiry = Duration.ofMinutes(10);
    /** MIME types accepted for upload (driver licence / vehicle licence scans + PDFs). */
    private Set<String> allowedContentTypes = new LinkedHashSet<>(Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "application/pdf"));
    /** Max upload size in bytes (default 10 MiB). */
    private long maxUploadBytes = 10L * 1024 * 1024;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Duration getPresignUploadExpiry() {
        return presignUploadExpiry;
    }

    public void setPresignUploadExpiry(Duration presignUploadExpiry) {
        this.presignUploadExpiry = presignUploadExpiry;
    }

    public Duration getPresignDownloadExpiry() {
        return presignDownloadExpiry;
    }

    public void setPresignDownloadExpiry(Duration presignDownloadExpiry) {
        this.presignDownloadExpiry = presignDownloadExpiry;
    }

    public Set<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(Set<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }
}
