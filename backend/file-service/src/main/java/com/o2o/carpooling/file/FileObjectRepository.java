package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
class FileObjectRepository {

    private final JdbcClient jdbcClient;

    FileObjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(FileObject fileObject, String sha256) {
        save(fileObject, sha256, FileUploadStatus.AVAILABLE, null, fileObject.createdAt());
    }

    void savePendingUpload(FileObject fileObject, String sha256, Instant uploadExpiresAt) {
        save(fileObject, sha256, FileUploadStatus.PENDING_UPLOAD, uploadExpiresAt, null);
    }

    private void save(FileObject fileObject, String sha256, FileUploadStatus status, Instant uploadExpiresAt, Instant uploadedAt) {
        jdbcClient.sql("""
            insert into file_objects (
              file_id, owner_user_id, bucket, object_key, content_type, sha256,
              visibility, upload_status, upload_expires_at, uploaded_at, created_at
            ) values (
              :fileId, :ownerUserId, :bucket, :objectKey, :contentType, :sha256,
              :visibility, :uploadStatus, :uploadExpiresAt, :uploadedAt, :createdAt
            )
            """)
            .param("fileId", fileObject.fileObjectId())
            .param("ownerUserId", fileObject.ownerId())
            .param("bucket", fileObject.bucket())
            .param("objectKey", fileObject.objectName())
            .param("contentType", fileObject.contentType())
            .param("sha256", sha256)
            .param("visibility", fileObject.privateObject() ? "PRIVATE" : "PUBLIC")
            .param("uploadStatus", status.name())
            .param("uploadExpiresAt", uploadExpiresAt)
            .param("uploadedAt", uploadedAt)
            .param("createdAt", fileObject.createdAt())
            .update();
    }

    Optional<FileObject> findByFileObjectId(String fileObjectId) {
        return jdbcClient.sql("""
            select file_id, owner_user_id, bucket, object_key, content_type, visibility, created_at
            from file_objects
            where file_id = :fileId
            """)
            .param("fileId", fileObjectId)
            .query(this::mapRow)
            .optional();
    }

    Optional<StoredFileObject> findStoredByFileObjectId(String fileObjectId) {
        return jdbcClient.sql("""
            select file_id, owner_user_id, bucket, object_key, content_type, visibility,
                   upload_status, upload_expires_at, uploaded_at, created_at
            from file_objects
            where file_id = :fileId
            """)
            .param("fileId", fileObjectId)
            .query(this::mapStoredRow)
            .optional();
    }

    void markAvailable(String fileObjectId, Instant uploadedAt) {
        jdbcClient.sql("""
            update file_objects
            set upload_status = :status,
                uploaded_at = :uploadedAt
            where file_id = :fileId
            """)
            .param("status", FileUploadStatus.AVAILABLE.name())
            .param("uploadedAt", uploadedAt)
            .param("fileId", fileObjectId)
            .update();
    }

    private FileObject mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return mapFileObject(resultSet);
    }

    private StoredFileObject mapStoredRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StoredFileObject(
            mapFileObject(resultSet),
            FileUploadStatus.valueOf(resultSet.getString("upload_status")),
            resultSet.getTimestamp("upload_expires_at") == null ? null : resultSet.getTimestamp("upload_expires_at").toInstant(),
            resultSet.getTimestamp("uploaded_at") == null ? null : resultSet.getTimestamp("uploaded_at").toInstant()
        );
    }

    private FileObject mapFileObject(ResultSet resultSet) throws SQLException {
        return new FileObject(
            resultSet.getString("file_id"),
            resultSet.getString("owner_user_id"),
            resultSet.getString("bucket"),
            resultSet.getString("object_key"),
            resultSet.getString("content_type"),
            "PRIVATE".equals(resultSet.getString("visibility")),
            resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
