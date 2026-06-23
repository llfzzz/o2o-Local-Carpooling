package com.o2o.carpooling.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.OcrTask;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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
            insert into ocr_tasks (task_id, file_object_id, result_json, completed_at)
            values (:taskId, :fileObjectId, :resultJson, :completedAt)
            """)
            .param("taskId", task.taskId())
            .param("fileObjectId", task.fileObjectId())
            .param("resultJson", writeJson(task.result()))
            .param("completedAt", task.completedAt())
            .update();
    }

    Optional<OcrTask> findLatestByFileObjectId(String fileObjectId) {
        return jdbcClient.sql("""
            select task_id, file_object_id, result_json, completed_at
            from ocr_tasks
            where file_object_id = :fileObjectId
            order by completed_at desc, id desc
            limit 1
            """)
            .param("fileObjectId", fileObjectId)
            .query(this::mapRow)
            .optional();
    }

    private OcrTask mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OcrTask(
            resultSet.getString("task_id"),
            resultSet.getString("file_object_id"),
            readOcrResult(resultSet.getString("result_json")),
            resultSet.getTimestamp("completed_at").toInstant()
        );
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
