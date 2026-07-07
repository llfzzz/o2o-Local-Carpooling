package com.o2o.carpooling.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.OcrTask;
import com.o2o.carpooling.common.domain.OcrTaskStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class OcrTaskRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    OcrTaskRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(OcrTask task) {
        jdbcClient.sql("""
            insert into ocr_tasks (task_id, file_object_id, status, provider_ref, result_json, submitted_at, completed_at)
            values (:taskId, :fileObjectId, :status, :providerRef, :resultJson, :submittedAt, :completedAt)
            """)
            .param("taskId", task.taskId())
            .param("fileObjectId", task.fileObjectId())
            .param("status", task.status().name())
            .param("providerRef", task.providerRef())
            .param("resultJson", task.result() == null ? null : writeJson(task.result()))
            .param("submittedAt", timestampOrNull(task.submittedAt()))
            .param("completedAt", timestampOrNull(task.completedAt()))
            .update();
    }

    /** Mark a task COMPLETED with its recognized (masked) result. */
    void complete(String taskId, OcrResult result, Instant completedAt) {
        jdbcClient.sql("""
            update ocr_tasks
            set status = :status, result_json = :resultJson, completed_at = :completedAt
            where task_id = :taskId
            """)
            .param("status", OcrTaskStatus.COMPLETED.name())
            .param("resultJson", writeJson(result))
            .param("completedAt", timestampOrNull(completedAt))
            .param("taskId", taskId)
            .update();
    }

    Optional<OcrTask> findByTaskId(String taskId) {
        return jdbcClient.sql("""
            select task_id, file_object_id, status, provider_ref, result_json, submitted_at, completed_at
            from ocr_tasks where task_id = :taskId
            """)
            .param("taskId", taskId)
            .query(this::mapRow)
            .optional();
    }

    /** Most recently submitted tasks first (operator task-management listing). */
    List<OcrTask> findRecent(int limit) {
        return jdbcClient.sql("""
            select task_id, file_object_id, status, provider_ref, result_json, submitted_at, completed_at
            from ocr_tasks
            order by submitted_at desc, id desc
            limit :limit
            """)
            .param("limit", limit)
            .query(this::mapRow)
            .list();
    }

    Optional<OcrTask> findLatestByFileObjectId(String fileObjectId) {
        return jdbcClient.sql("""
            select task_id, file_object_id, status, provider_ref, result_json, submitted_at, completed_at
            from ocr_tasks
            where file_object_id = :fileObjectId
            order by submitted_at desc, id desc
            limit 1
            """)
            .param("fileObjectId", fileObjectId)
            .query(this::mapRow)
            .optional();
    }

    private OcrTask mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        String resultJson = resultSet.getString("result_json");
        Timestamp submittedAt = resultSet.getTimestamp("submitted_at");
        Timestamp completedAt = resultSet.getTimestamp("completed_at");
        return new OcrTask(
            resultSet.getString("task_id"),
            resultSet.getString("file_object_id"),
            OcrTaskStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("provider_ref"),
            resultJson == null ? null : readOcrResult(resultJson),
            submittedAt == null ? null : submittedAt.toInstant(),
            completedAt == null ? null : completedAt.toInstant()
        );
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String writeJson(OcrResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to serialize OCR result", exception);
        }
    }

    private OcrResult readOcrResult(String json) {
        try {
            return objectMapper.readValue(json, OcrResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize OCR result", exception);
        }
    }
}
