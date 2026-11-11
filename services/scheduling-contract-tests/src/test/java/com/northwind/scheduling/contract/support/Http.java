package com.northwind.scheduling.contract.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Minimal JSON-over-HTTP client for the legacy Java 8 services. */
public final class Http {

    public static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final String base;

    public Http(String base) {
        this.base = base;
    }

    public record Resp(int status, JsonNode body) {
    }

    public Resp get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    public Resp delete(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).DELETE());
    }

    public Resp postJson(String path, Object body) {
        try {
            return send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public Resp postFile(String path, String fileName, String content) {
        String boundary = "----contract" + UUID.randomUUID();
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"
                + content + "\r\n--" + boundary + "--\r\n";
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    private Resp send(HttpRequest.Builder b) {
        try {
            HttpResponse<String> r = CLIENT.send(b.timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = r.body() == null || r.body().isBlank() ? null : JSON.readTree(r.body());
            return new Resp(r.statusCode(), body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
