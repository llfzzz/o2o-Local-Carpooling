package com.o2o.carpooling.driver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.carpooling.common.domain.DriverVerificationStatus;
import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.OcrResultMasker;
import com.o2o.carpooling.common.domain.VerificationCase;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
class DriverVerificationRepository {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    DriverVerificationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void save(VerificationCase verificationCase) {
        jdbcClient.sql("""
            insert into driver_verification_cases (
              case_id, user_id, status, uploaded_file_ids_json, ocr_result_json, submitted_at
            ) values (
              :caseId, :userId, :status, :uploadedFileIdsJson, :ocrResultJson, :submittedAt
            )
            """)
            .param("caseId", verificationCase.caseId())
            .param("userId", verificationCase.userId())
            .param("status", verificationCase.status().name())
            .param("uploadedFileIdsJson", writeJson(verificationCase.uploadedFileIds()))
            .param("ocrResultJson", writeJson(OcrResultMasker.maskSensitiveFields(verificationCase.ocrResult())))
            .param("submittedAt", verificationCase.submittedAt())
            .update();
    }

    void updateStatus(String caseId, DriverVerificationStatus status) {
        int updated = jdbcClient.sql("""
            update driver_verification_cases
            set status = :status, reviewed_at = :reviewedAt
            where case_id = :caseId
            """)
            .param("status", status.name())
            .param("reviewedAt", Instant.now())
            .param("caseId", caseId)
            .update();
        if (updated == 0) {
            throw new IllegalArgumentException("verification case not found: " + caseId);
        }
    }

    Optional<VerificationCase> findByCaseId(String caseId) {
        return jdbcClient.sql("""
            select case_id, user_id, status, uploaded_file_ids_json, ocr_result_json, submitted_at
            from driver_verification_cases
            where case_id = :caseId
            """)
            .param("caseId", caseId)
            .query(this::mapRow)
            .optional();
    }

    /**
     * True when this user has at least one APPROVED document case. This is the document half of
     * "may this user act as a driver"; the identity half lives in identity-service.
     */
    boolean hasApprovedCase(String userId) {
        return jdbcClient.sql("""
            select count(*) from driver_verification_cases
            where user_id = :userId and status = :status
            """)
            .param("userId", userId)
            .param("status", DriverVerificationStatus.APPROVED.name())
            .query(Long.class)
            .single() > 0;
    }

    /**
     * Count cases in a given status. Served by {@code idx_driver_verification_status}; used by the
     * admin dashboard's pending-review tile so it no longer fetches every case (with its OCR/file
     * JSON blobs) over HTTP just to count a subset in Java.
     */
    long countByStatus(DriverVerificationStatus status) {
        return jdbcClient.sql("""
            select count(*) from driver_verification_cases
            where status = :status
            """)
            .param("status", status.name())
            .query(Long.class)
            .single();
    }

    List<VerificationCase> listCases() {
        return jdbcClient.sql("""
            select case_id, user_id, status, uploaded_file_ids_json, ocr_result_json, submitted_at
            from driver_verification_cases
            order by submitted_at desc, id desc
            """)
            .query(this::mapRow)
            .list();
    }

    private VerificationCase mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new VerificationCase(
            resultSet.getString("case_id"),
            resultSet.getString("user_id"),
            DriverVerificationStatus.valueOf(resultSet.getString("status")),
            readStringMap(resultSet.getString("uploaded_file_ids_json")),
            readOcrResult(resultSet.getString("ocr_result_json")),
            resultSet.getTimestamp("submitted_at").toInstant()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to serialize verification case", exception);
        }
    }

    private Map<String, String> readStringMap(String json) {
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize uploaded file ids", exception);
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
