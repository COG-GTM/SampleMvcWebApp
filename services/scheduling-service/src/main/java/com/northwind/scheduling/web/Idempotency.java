package com.northwind.scheduling.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.scheduling.api.ApiError;
import com.northwind.scheduling.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Idempotency-Key handling for POST commands (contract section 6): (key, client, request hash, response) kept for
 * scheduling.idempotency.retention-days. Same key + same request replays the stored status and body with
 * {@code Idempotent-Replayed: true}; same key + different request is 422 IDEMPOTENCY_KEY_REUSED. 5xx is not stored.
 */
@Component
public class Idempotency {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final int retentionDays;

    public Idempotency(JdbcTemplate jdbc, ObjectMapper mapper, @Value("${scheduling.idempotency.retention-days:7}") int retentionDays) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.retentionDays = retentionDays;
    }

    public ResponseEntity<?> execute(String key, String client, String request, Supplier<ResponseEntity<?>> command) {
        if (key == null || key.isBlank()) {
            return command.get();
        }
        if (key.length() > 64) {
            throw ApiException.badRequest("VALIDATION", "Idempotency-Key is limited to 64 characters");
        }
        String who = client == null || client.isBlank() ? "anonymous" : client;
        String hash = sha256(request);
        // Reserve the key first (StatusCode 0 = in progress) so overlapping retries never run the command twice.
        jdbc.update("DELETE FROM dbo.IdempotencyKey WHERE IdemKey = ? AND Client = ? AND CreatedAt <= DATEADD(DAY, -?, GETDATE())",
                key, who, retentionDays);
        try {
            jdbc.update("INSERT INTO dbo.IdempotencyKey (IdemKey, Client, RequestHash, StatusCode) VALUES (?, ?, ?, 0)", key, who, hash);
        } catch (DuplicateKeyException taken) {
            List<Map<String, Object>> prior = jdbc.queryForList("SELECT RequestHash, StatusCode, Body FROM dbo.IdempotencyKey "
                    + "WHERE IdemKey = ? AND Client = ?", key, who);
            if (prior.isEmpty()) {
                return execute(key, client, request, command);
            }
            return replay(prior.get(0), hash);
        }
        ResponseEntity<?> response;
        try {
            response = command.get();
        } catch (ApiException e) {
            response = ResponseEntity.status(e.status()).body(new ApiError(e.error(), e.getMessage(), e.legacyState(), null));
        } catch (RuntimeException e) {
            release(key, who);
            throw e;
        }
        if (response.getStatusCode().is5xxServerError()) {
            release(key, who);
            return response;
        }
        try {
            jdbc.update("UPDATE dbo.IdempotencyKey SET StatusCode = ?, Body = ? WHERE IdemKey = ? AND Client = ?",
                    response.getStatusCode().value(), mapper.writeValueAsString(response.getBody()), key, who);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return response;
    }

    private void release(String key, String who) {
        jdbc.update("DELETE FROM dbo.IdempotencyKey WHERE IdemKey = ? AND Client = ? AND StatusCode = 0", key, who);
    }

    private ResponseEntity<?> replay(Map<String, Object> prior, String hash) {
        if (!hash.equals(prior.get("RequestHash"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiError("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for a different request", null, null));
        }
        int status = ((Number) prior.get("StatusCode")).intValue();
        if (status == 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("IDEMPOTENCY_IN_PROGRESS",
                    "A request with this Idempotency-Key is still being processed; retry later", null, null));
        }
        return ResponseEntity.status(status)
                .header("Idempotent-Replayed", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .body((String) prior.get("Body"));
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void purge() {
        jdbc.update("DELETE FROM dbo.IdempotencyKey WHERE CreatedAt <= DATEADD(DAY, -?, GETDATE())", retentionDays);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
