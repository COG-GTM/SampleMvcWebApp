package com.northwind.scheduling.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.northwind.scheduling.domain.BookingStatus;
import com.northwind.scheduling.domain.CapacityImpact;
import com.northwind.scheduling.outbox.OutboxPayload;

/** MBA-3831: docs/schemas/candidate-capacity-impact-notified.schema.json matches the emitted event and docs/events.md. */
public class CandidateCapacityImpactSchemaTest {

    public static final Path SCHEMA = Path.of("docs/schemas/candidate-capacity-impact-notified.schema.json");
    static final Path DOC = Path.of("docs/events.md");
    /** spring.jackson settings of the service (application.yml): ISO date-times, nulls included */
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static JsonSchema schema() throws IOException {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(Files.readString(SCHEMA), config);
    }

    static CandidateCapacityImpactNotified sample() {
        return new CandidateCapacityImpactNotified(UUID.randomUUID(), 154, "SFO01", "MHL-RN1",
                LocalDateTime.of(2026, 11, 12, 13, 30), 20, 8, "NW00000909", 9, "BOOKED", "WAITLISTED",
                CapacityImpact.Kind.STATUS_CHANGED);
    }

    static ObjectNode sampleJson() {
        return JSON.valueToTree(sample());
    }

    @Test
    void serializedEventMatchesTheSchema() throws IOException {
        Set<ValidationMessage> errors = schema().validate(sampleJson());
        assertThat(errors).isEmpty();
    }

    @Test
    void notificationInsideTheOutboxPayloadMatchesTheSchemaAndRoundTrips() throws IOException {
        CandidateCapacityImpactNotified n = sample();
        String body = JSON.writeValueAsString(new OutboxPayload("CandidateCapacityImpactNotified", "d", List.of(), List.of(), n));

        JsonNode tree = JSON.readTree(body);
        assertThat(schema().validate(tree.get("notification"))).isEmpty();
        assertThat(JSON.readValue(body, OutboxPayload.class).notification()).isEqualTo(n);
    }

    @Test
    void schemaPropertiesAreExactlyTheRecordComponentsAndAllRequired() throws IOException {
        JsonNode schema = JSON.readTree(Files.readString(SCHEMA));
        Set<String> props = new TreeSet<>();
        schema.get("properties").fieldNames().forEachRemaining(props::add);
        Set<String> required = new TreeSet<>();
        schema.get("required").forEach(r -> required.add(r.asText()));
        Set<String> components = new TreeSet<>(Arrays.stream(CandidateCapacityImpactNotified.class.getRecordComponents())
                .map(c -> c.getName()).toList());

        assertThat(props).isEqualTo(components).isEqualTo(required);
        assertThat(props).containsAll(List.of("eventId", "sessionId", "centerCode", "examCode", "startsAt", "oldCapacity",
                "newCapacity", "candidateNumber", "bookingId", "previousStatus", "newStatus"));
        List<String> statuses = new ArrayList<>();
        schema.at("/$defs/bookingStatus/enum").forEach(s -> statuses.add(s.asText()));
        assertThat(statuses).containsExactlyInAnyOrderElementsOf(Arrays.stream(BookingStatus.values()).map(Enum::name).toList());
        List<String> impacts = new ArrayList<>();
        schema.at("/properties/impact/enum").forEach(s -> impacts.add(s.asText()));
        assertThat(impacts).containsExactlyInAnyOrderElementsOf(Arrays.stream(CapacityImpact.Kind.values()).map(Enum::name).toList());
    }

    @Test
    void invalidEventsAreRejected() throws IOException {
        JsonSchema schema = schema();
        assertThat(schema.validate(sampleJson().without("candidateNumber"))).isNotEmpty();
        assertThat(schema.validate(sampleJson().put("newStatus", "DEMOTED"))).isNotEmpty();
        assertThat(schema.validate(sampleJson().put("eventId", "not-a-uuid"))).isNotEmpty();
        assertThat(schema.validate(sampleJson().put("startsAt", "2026-11-12T13:30:00Z"))).isNotEmpty();
        assertThat(schema.validate(sampleJson().put("candidateNumber", "X1"))).isNotEmpty();
        assertThat(schema.validate(sampleJson().put("extra", 1))).isNotEmpty();
        assertThat(schema.validate(sampleJson().put("newCapacity", -1))).isNotEmpty();
    }

    @Test
    void eventsDocDocumentsEveryFieldAndLinksTheSchema() throws IOException {
        String doc = Files.readString(DOC);
        assertThat(doc).contains("## CandidateCapacityImpactNotified", "schemas/candidate-capacity-impact-notified.schema.json");
        for (var c : CandidateCapacityImpactNotified.class.getRecordComponents()) {
            assertThat(doc).as("docs/events.md documents " + c.getName()).contains("`" + c.getName() + "`");
        }
        int start = doc.indexOf("```json") + "```json".length();
        JsonNode example = JSON.readTree(doc.substring(start, doc.indexOf("```", start)));
        assertThat(schema().validate(example)).as("example in docs/events.md").isEmpty();
    }
}
