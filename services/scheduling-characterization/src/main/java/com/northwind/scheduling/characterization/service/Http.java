package com.northwind.scheduling.characterization.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal JSON-over-HTTP client for the scheduling-service API. */
final class Http {

    record Response(int status, JsonNode body) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base;

    Http(String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    Response get(String path) {
        return send("GET", path, null, null);
    }

    Response send(String method, String path, Object body, String actor) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(120))
                    .header("Accept", "application/json");
            if (actor != null) {
                b.header("X-Actor", actor);
            }
            if (body != null) {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<byte[]> r = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            JsonNode node = r.body() == null || r.body().length == 0 ? null : JSON.readTree(r.body());
            return new Response(r.statusCode(), node);
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    Response require(Response r, String what) {
        if (!r.ok()) {
            throw new IllegalStateException(what + " -> HTTP " + r.status() + " " + r.body());
        }
        return r;
    }
}
