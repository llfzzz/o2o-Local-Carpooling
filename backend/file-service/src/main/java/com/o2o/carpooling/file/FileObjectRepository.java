package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
class FileObjectRepository {

    private final JdbcClient jdbcClient;

    FileObjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(FileObject fileObject, String sha256) {
        jdbcClient.sql("""
            insert into file_objects (
              file_id, owner_user_id, bucket, object_key, content_type, sha256, visibility, created_at
            ) values (
              :fileId, :ownerUserId, :bucket, :objectKey, :contentType, :sha256, :visibility, :createdAt
            )
            """)
            .param("fileId", fileObject.fileObjectId())
            .param("ownerUserId", fileObject.ownerId())
            .param("bucket", fileObject.bucket())
            .param("objectKey", fileObject.objectName())
            .param("contentType", fileObject.contentType())
            .param("sha256", sha256)
            .param("visibility", fileObject.privateObject() ? "PRIVATE" : "PUBLIC")
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

    private FileObject mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
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
