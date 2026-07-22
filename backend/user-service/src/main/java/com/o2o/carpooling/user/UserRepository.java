package com.o2o.carpooling.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.domain.UserRole;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
class UserRepository {

    private final JdbcClient jdbcClient;
    private final FieldEncryptionService fieldEncryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    UserRepository(JdbcClient jdbcClient, FieldEncryptionService fieldEncryptionService) {
        this.jdbcClient = jdbcClient;
        this.fieldEncryptionService = fieldEncryptionService;
    }

    @Transactional
    void upsert(UserAccount user) {
        // Race-safe upsert without a read-then-write window: update in place, and only insert when
        // no row matched. If two first-time upserts of the same user_id race, the loser's insert
        // trips the user_id UNIQUE constraint and falls back to an update — instead of the previous
        // find-then-insert that let both callers see "absent" and one fail on the unique key.
        if (updateExisting(user) > 0) {
            return;
        }
        try {
            insertNew(user);
        } catch (DuplicateKeyException raced) {
            updateExisting(user);
        }
    }

    private int updateExisting(UserAccount user) {
        return jdbcClient.sql("""
            update users
            set phone = :phone, roles_json = :rolesJson, updated_at = :updatedAt
            where user_id = :userId
            """)
            .param("phone", fieldEncryptionService.encrypt(user.phone()))
            .param("rolesJson", rolesJson(user.roles()))
            .param("updatedAt", Instant.now())
            .param("userId", user.userId())
            .update();
    }

    private void insertNew(UserAccount user) {
        jdbcClient.sql("""
            insert into users (user_id, phone, roles_json, created_at, updated_at)
            values (:userId, :phone, :rolesJson, :createdAt, :updatedAt)
            """)
            .param("userId", user.userId())
            .param("phone", fieldEncryptionService.encrypt(user.phone()))
            .param("rolesJson", rolesJson(user.roles()))
            .param("createdAt", user.createdAt())
            .param("updatedAt", Instant.now())
            .update();
    }

    Optional<UserAccount> findByUserId(String userId) {
        return jdbcClient.sql("""
            select user_id, phone, roles_json, created_at
            from users
            where user_id = :userId
            """)
            .param("userId", userId)
            .query(this::mapRow)
            .optional();
    }

    List<UserAccount> list() {
        return jdbcClient.sql("""
            select user_id, phone, roles_json, created_at
            from users
            order by created_at desc
            """)
            .query(this::mapRow)
            .list();
    }

    private UserAccount mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserAccount(
            resultSet.getString("user_id"),
            fieldEncryptionService.decrypt(resultSet.getBytes("phone")),
            roles(resultSet.getString("roles_json")),
            resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private String rolesJson(Set<UserRole> roles) {
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to serialize user roles", exception);
        }
    }

    private Set<UserRole> roles(String json) {
        try {
            return Arrays.stream(objectMapper.readValue(json, UserRole[].class)).collect(Collectors.toUnmodifiableSet());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize user roles", exception);
        }
    }
}
