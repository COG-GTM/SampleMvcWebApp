package com.northwind.scheduling.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.northwind.scheduling.SqlServerTestBase;

/**
 * MBA-3833: the running service's /v3/api-docs must equal the committed api/openapi.yaml.
 * Regenerate after an intended API change: {@code mvn verify -Dit.test=OpenApiSpecIT -Dopenapi.write=true}
 * (writes api/openapi.yaml), then review the diff; breaking changes are caught by OpenApiCompatibilityTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SqlServerTestBase.FixtureLegacy.class)
class OpenApiSpecIT extends SqlServerTestBase {

    static final Path COMMITTED = Path.of("api/openapi.yaml");
    static final ObjectMapper JSON = new ObjectMapper();
    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Autowired
    TestRestTemplate http;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    JsonNode liveSpec() throws Exception {
        ResponseEntity<String> r = http.getForEntity("/v3/api-docs", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(r.getBody());
    }

    @Test
    void runningServiceSpecMatchesCommittedOpenapiYaml() throws Exception {
        String liveYaml = http.getForObject("/v3/api-docs.yaml", String.class);
        if (Boolean.getBoolean("openapi.write")) {
            Files.writeString(COMMITTED, liveYaml);
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.generated.yaml"), liveYaml);
        assertThat(COMMITTED).as("committed spec").exists();
        JsonNode committed = YAML.readTree(COMMITTED.toFile());
        JsonNode live = liveSpec();
        List<String> diffs = new ArrayList<>();
        diff("", committed, live, diffs);
        assertThat(diffs)
                .as("api/openapi.yaml differs from the running service's /v3/api-docs (live copy: "
                        + "target/openapi.generated.yaml). If the change is intended, regenerate with "
                        + "-Dit.test=OpenApiSpecIT -Dopenapi.write=true and commit it.")
                .isEmpty();
    }

    static void diff(String at, JsonNode expected, JsonNode actual, List<String> out) {
        if (expected.isObject() && actual.isObject()) {
            TreeSet<String> keys = new TreeSet<>();
            expected.fieldNames().forEachRemaining(keys::add);
            actual.fieldNames().forEachRemaining(keys::add);
            for (String k : keys) {
                String p = at + "/" + k.replace("~", "~0").replace("/", "~1");
                if (!expected.has(k)) {
                    out.add("added " + p);
                } else if (!actual.has(k)) {
                    out.add("removed " + p);
                } else {
                    diff(p, expected.get(k), actual.get(k), out);
                }
            }
        } else if (expected.isNumber() && actual.isNumber()) {
            if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
                out.add("changed " + at + ": " + expected + " -> " + actual);
            }
        } else if (!expected.equals(actual)) {
            out.add("changed " + at + ": " + expected + " -> " + actual);
        }
    }

    @Test
    void everyPublicV2EndpointIsDocumentedAndInternalIsNot() throws Exception {
        JsonNode paths = liveSpec().get("paths");
        List<String> missing = new ArrayList<>();
        int count = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            for (String pattern : e.getKey().getPatternValues()) {
                if (!pattern.startsWith("/api/v2/") || pattern.startsWith("/api/v2/internal")) {
                    continue;
                }
                for (var m : e.getKey().getMethodsCondition().getMethods()) {
                    count++;
                    JsonNode op = paths.path(pattern).path(m.name().toLowerCase());
                    if (op.isMissingNode()) {
                        missing.add(m + " " + pattern);
                    } else {
                        assertThat(op.path("responses").fieldNames()).as(m + " " + pattern).toIterable()
                                .anyMatch(c -> c.startsWith("2"));
                        assertThat(op.path("summary").asText()).as("summary of " + m + " " + pattern).isNotBlank();
                    }
                }
            }
        }
        assertThat(count).isGreaterThanOrEqualTo(14);
        assertThat(missing).as("undocumented /api/v2 endpoints").isEmpty();
        for (Iterator<String> it = paths.fieldNames(); it.hasNext();) {
            String p = it.next();
            assertThat(p).startsWith("/api/v2/").doesNotStartWith("/api/v2/internal");
        }
    }

    @Test
    void errorModelAndLegacyMappingAreDocumentedWithExamples() throws Exception {
        JsonNode spec = liveSpec();
        JsonNode err = spec.at("/components/schemas/ApiError");
        assertThat(err.path("required").toString()).isEqualTo("[\"error\",\"message\"]");
        assertThat(err.at("/properties/error/enum").toString()).contains("NO_ACCOMMODATION_SEAT", "INVALID_CAPACITY");
        assertThat(err.at("/properties/legacyCode/nullable").asBoolean()).isTrue();
        JsonNode book409 = spec.at("/paths/~1api~1v2~1bookings/post/responses/409/content/application~1json");
        assertThat(book409.at("/schema/$ref").asText()).isEqualTo("#/components/schemas/ApiError");
        assertThat(book409.at("/examples/NO_ACCOMMODATION_SEAT/value/legacyCode").asInt()).isEqualTo(50003);
        assertThat(spec.at("/paths/~1api~1v2~1bookings~1{bookingId}~1reschedule/post/responses/409/content/application~1json/examples")
                .fieldNames()).toIterable().contains("DUPLICATE_BOOKING", "NO_ACCOMMODATION_SEAT", "SAME_SESSION");
        assertThat(spec.at("/paths/~1api~1v2~1bookings/post/responses/201/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/BookingResultDto");
        assertThat(spec.at("/paths/~1api~1v2~1bookings/post/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/BookRequest");
        assertThat(spec.at("/components/schemas/BookRequest/required").toString())
                .isEqualTo("[\"candidateNumber\",\"sessionId\"]");
        assertThat(spec.at("/components/schemas/EligibilityDto/required").toString())
                .contains("\"eligible\"", "\"reason\"", "\"earliestRetakeDate\"");
        assertThat(spec.at("/paths/~1api~1v2~1candidates~1{candidateNumber}~1eligibility/get/parameters").findValuesAsText("name"))
                .contains("examCode");
        spec.at("/paths/~1api~1v2~1candidates~1{candidateNumber}~1eligibility/get/parameters").forEach(p -> {
            if (p.path("name").asText().equals("examCode")) {
                assertThat(p.path("required").asBoolean()).as("examCode required").isTrue();
            }
        });
        assertThat(spec.toString()).as("LocalDateTime has no offset, so no RFC 3339 date-time").doesNotContain("\"date-time\"");
        JsonNode startsAt = spec.at("/components/schemas/SessionDto/properties/startsAt");
        assertThat(startsAt.path("type").asText()).isEqualTo("string");
        assertThat(startsAt.path("example").asText()).matches(startsAt.path("pattern").asText());
        assertThat(spec.at("/info/description").asText()).contains("| `NO_ACCOMMODATION_SEAT` | 409 | 50003 |");
    }

    @Test
    void swaggerUiIsServed() {
        ResponseEntity<String> r = http.getForEntity("/swagger-ui.html", String.class);
        assertThat(r.getStatusCode().is2xxSuccessful() || r.getStatusCode().is3xxRedirection()).isTrue();
        ResponseEntity<String> ui = http.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(ui.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ui.getBody()).contains("swagger-ui");
        assertThat(http.getForObject("/v3/api-docs/swagger-config", String.class)).contains("/v3/api-docs");
    }
}
