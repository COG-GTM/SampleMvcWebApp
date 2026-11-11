package com.northwind.scheduling.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import com.northwind.scheduling.domain.BookingStatus;
import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.SessionStatus;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Public OpenAPI document of /api/v2 (MBA-3833): GET /v3/api-docs, /swagger-ui.html, committed as api/openapi.yaml.
 * Documentation lives here (keyed by operationId / schema name) rather than as annotations on the controllers and
 * Dtos, so the endpoint code stays as M2 wrote it. Paths and filters: springdoc.properties.
 */
@Configuration
@PropertySource("classpath:springdoc.properties")
public class OpenApiConfig {

    static final String API_VERSION = "2.0.0";
    static final String ERROR_SCHEMA = "ApiError";
    /** 401 body of service-token operations; kept out of ApiError so its enum stays compatible with published baselines. */
    static final String SERVICE_TOKEN_ERROR_SCHEMA = "ServiceTokenError";
    /** 4xx body of the MBA-3829 offer operations; kept out of ApiError for the same reason. */
    static final String OFFER_ERROR_SCHEMA = "OfferError";
    static final Set<ErrorCode> OFFER_ERRORS = Set.of(ErrorCode.OFFER_NOT_FOUND, ErrorCode.OFFER_NOT_PENDING);
    static final Set<String> SESSION_TIMES = Set.of("startsAt", "sessionStartsAt");
    static final String LOCAL_DATE_TIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?$";

    /** Error codes each operation can answer with (SchedulingService, domain rules, ApiExceptionHandler). */
    static final Map<String, List<ErrorCode>> ERRORS = Map.ofEntries(
            Map.entry("sessions", List.of(ErrorCode.VALIDATION, ErrorCode.CENTER_NOT_FOUND)),
            Map.entry("session", List.of(ErrorCode.BAD_REQUEST, ErrorCode.SESSION_NOT_FOUND)),
            Map.entry("waitlist", List.of(ErrorCode.BAD_REQUEST, ErrorCode.SESSION_NOT_FOUND)),
            Map.entry("capacityChanges", List.of(ErrorCode.BAD_REQUEST, ErrorCode.SESSION_NOT_FOUND)),
            Map.entry("changeCapacity", List.of(ErrorCode.VALIDATION, ErrorCode.SESSION_NOT_FOUND,
                    ErrorCode.INVALID_CAPACITY)),
            Map.entry("book", List.of(ErrorCode.VALIDATION, ErrorCode.CANDIDATE_NOT_FOUND, ErrorCode.SESSION_NOT_FOUND,
                    ErrorCode.SESSION_NOT_OPEN, ErrorCode.DUPLICATE_BOOKING, ErrorCode.NO_ACCOMMODATION_SEAT,
                    ErrorCode.NOT_ELIGIBLE)),
            Map.entry("booking", List.of(ErrorCode.BAD_REQUEST, ErrorCode.BOOKING_NOT_FOUND)),
            Map.entry("cancel", List.of(ErrorCode.BAD_REQUEST, ErrorCode.BOOKING_NOT_FOUND, ErrorCode.NOT_CANCELLABLE)),
            Map.entry("attendance", List.of(ErrorCode.BAD_REQUEST, ErrorCode.UNAUTHORIZED, ErrorCode.BOOKING_NOT_FOUND, ErrorCode.NOT_CANCELLABLE)),
            Map.entry("reschedule", List.of(ErrorCode.VALIDATION, ErrorCode.BOOKING_NOT_FOUND,
                    ErrorCode.SESSION_NOT_FOUND, ErrorCode.NOT_CANCELLABLE, ErrorCode.SAME_SESSION,
                    ErrorCode.DIFFERENT_EXAM, ErrorCode.SESSION_NOT_OPEN, ErrorCode.DUPLICATE_BOOKING,
                    ErrorCode.NO_ACCOMMODATION_SEAT,
                    ErrorCode.NOT_ELIGIBLE)),
            Map.entry("candidate", List.of(ErrorCode.CANDIDATE_NOT_FOUND)),
            Map.entry("candidateBookings", List.of(ErrorCode.CANDIDATE_NOT_FOUND)),
            Map.entry("eligibility", List.of(ErrorCode.VALIDATION, ErrorCode.CANDIDATE_NOT_FOUND,
                    ErrorCode.EXAM_NOT_FOUND)),
            Map.entry("offer", List.of(ErrorCode.BAD_REQUEST, ErrorCode.OFFER_NOT_FOUND)),
            Map.entry("acceptOffer", List.of(ErrorCode.BAD_REQUEST, ErrorCode.OFFER_NOT_FOUND, ErrorCode.OFFER_NOT_PENDING)),
            Map.entry("declineOffer", List.of(ErrorCode.BAD_REQUEST, ErrorCode.OFFER_NOT_FOUND, ErrorCode.OFFER_NOT_PENDING)));

    /** operationId -> tag, summary. */
    static final Map<String, String[]> OPERATIONS = Map.ofEntries(
            Map.entry("centers", new String[] {"Reference data", "List test centres"}),
            Map.entry("exams", new String[] {"Reference data", "List published exams"}),
            Map.entry("sessions", new String[] {"Sessions", "Search a centre's sessions (optional exam and date range)"}),
            Map.entry("session", new String[] {"Sessions", "Get a session with derived seat counts"}),
            Map.entry("waitlist", new String[] {"Sessions", "List a session's waitlist in position order"}),
            Map.entry("capacityChanges", new String[] {"Capacity", "List a session's capacity changes"}),
            Map.entry("changeCapacity", new String[] {"Capacity",
                    "Change a session's capacity (newest bookings above it are demoted to the waitlist, ODD-02)"}),
            Map.entry("book", new String[] {"Bookings",
                    "Book a seat (WAITLISTED when the session is full); returns the legacy {bookingId, status} plus the live booking and session"}),
            Map.entry("booking", new String[] {"Bookings", "Get a booking"}),
            Map.entry("cancel", new String[] {"Bookings",
                    "Cancel a booking (frees the seat; it is offered to the next eligible waitlist entry, MBA-3829 / ADR 0004)"}),
            Map.entry("offer", new String[] {"Offers", "Get a waitlist seat offer"}),
            Map.entry("acceptOffer", new String[] {"Offers",
                    "Accept a pending seat offer (the waitlisted booking becomes BOOKED, later waitlist positions move up)"}),
            Map.entry("declineOffer", new String[] {"Offers",
                    "Decline a pending seat offer (the waitlisted booking is cancelled, the seat is offered to the next entry)"}),
            Map.entry("attendance", new String[] {"Bookings",
                    "Record attendance (BOOKED -> ATTENDED; already ATTENDED is a no-op). Called by results-service on result ingestion"}),
            Map.entry("reschedule", new String[] {"Bookings",
                    "Reschedule a booking to another session of the same exam (cancel + book, new booking id, ODD-05)"}),
            Map.entry("candidate", new String[] {"Candidates", "Get a candidate profile"}),
            Map.entry("candidateBookings", new String[] {"Candidates", "List a candidate's bookings"}),
            Map.entry("eligibility", new String[] {"Candidates", "Check retake eligibility for an exam (calendar days, ODD-04)"}));

    static final Set<String> CREATED = Set.of("book", "reschedule");

    /** Operations that need the service credential (MBA-3823). */
    static final String SERVICE_TOKEN = "serviceToken";
    static final Set<String> SECURED = Set.of("attendance");

    /** Query parameters declared optional on the controller that SchedulingService rejects with VALIDATION when missing. */
    static final Map<String, Set<String>> REQUIRED_PARAMS = Map.of("eligibility", Set.of("examCode"));

    /** Response properties that can be JSON null (nullable columns of V1__scheduling_schema.sql). */
    static final Map<String, Set<String>> NULLABLE = Map.of(
            "BookingDto", Set.of("accommodationCode", "externalRef", "cancelledAt", "cancelReason", "waitlistPosition",
                    "rescheduledFrom", "offerId", "offerStatus", "offerExpiresAt"),
            "BookingResultDto", Set.of("waitlistPosition"),
            "CandidateDto", Set.of("defaultAccommodationCode"),
            "EligibilityDto", Set.of("earliestRetakeDate"),
            "WaitlistEntryDto", Set.of("accommodationCode", "offerId", "offerStatus", "offerExpiresAt"),
            "OfferDto", Set.of("resolvedAt"),
            "CapacityChangeDto", Set.of("reason"));

    /** Request properties the service rejects with VALIDATION when missing. */
    static final Map<String, List<String>> REQUIRED_REQUEST = Map.of(
            "BookRequest", List.of("sessionId", "candidateNumber"),
            "RescheduleRequest", List.of("newSessionId"),
            "CapacityRequest", List.of("capacity"));

    static final Map<String, Object> PROPERTY_EXAMPLES = Map.ofEntries(
            Map.entry("sessionId", 154), Map.entry("newSessionId", 163), Map.entry("bookingId", 1201),
            Map.entry("candidateNumber", "NW00000920"), Map.entry("candidateName", "First20 Last20"),
            Map.entry("firstName", "First20"), Map.entry("lastName", "Last20"), Map.entry("email", "c20@example.test"),
            Map.entry("examCode", "MHL-RN1"), Map.entry("examTitle", "Registered Nurse Part 1"),
            Map.entry("title", "Registered Nurse Part 1"), Map.entry("centerCode", "SFO01"),
            Map.entry("centerName", "Bayside Assessment Centre"), Map.entry("name", "Bayside Assessment Centre"),
            Map.entry("city", "San Francisco"), Map.entry("region", "NA-WEST"),
            Map.entry("timeZone", "America/Los_Angeles"), Map.entry("roomName", "Room North"),
            Map.entry("startsAt", "2026-11-12T13:30:00"), Map.entry("sessionStartsAt", "2026-11-12T13:30:00"),
            Map.entry("bookedAt", "2026-11-10T09:15:00"), Map.entry("addedAt", "2026-11-10T09:15:00"),
            Map.entry("changedAt", "2026-11-10T09:15:00"), Map.entry("capacity", 20),
            Map.entry("accommodationCapacity", 2), Map.entry("accommodationCode", "ET25"),
            Map.entry("defaultAccommodationCode", "ET25"), Map.entry("externalRef", "PX-3008"),
            Map.entry("date", "2026-11-20"), Map.entry("earliestRetakeDate", "2026-12-07"),
            Map.entry("dayCounting", "calendar"), Map.entry("durationMinutes", 120), Map.entry("retakeWaitDays", 30),
            Map.entry("changedBy", "scheduling-service"), Map.entry("reason", "Room maintenance"),
            Map.entry("offerId", 7), Map.entry("offeredAt", "2026-11-10T09:15:00"),
            Map.entry("expiresAt", "2026-11-11T09:15:00"), Map.entry("offerExpiresAt", "2026-11-11T09:15:00"),
            Map.entry("resolvedAt", "2026-11-10T10:00:00"), Map.entry("position", 1), Map.entry("heldSeats", 1));

    static final Map<String, Object> ERROR_EXAMPLES = errorExamples();

    private static Map<String, Object> errorExamples() {
        Map<ErrorCode, String> msg = new LinkedHashMap<>();
        msg.put(ErrorCode.VALIDATION, "sessionId and candidateNumber are required");
        msg.put(ErrorCode.BAD_REQUEST, "Failed to convert value of type 'java.lang.String' to required type 'int'");
        msg.put(ErrorCode.UNAUTHORIZED, "X-Service-Token missing or invalid");
        msg.put(ErrorCode.CANDIDATE_NOT_FOUND, "Candidate NW00009999 not found");
        msg.put(ErrorCode.SESSION_NOT_FOUND, "Session 99999 not found");
        msg.put(ErrorCode.BOOKING_NOT_FOUND, "Booking 99999 not found");
        msg.put(ErrorCode.EXAM_NOT_FOUND, "Exam NOPE not found");
        msg.put(ErrorCode.CENTER_NOT_FOUND, "Center NOPE not found");
        msg.put(ErrorCode.SESSION_NOT_OPEN, "Session 263 is not open for booking");
        msg.put(ErrorCode.DUPLICATE_BOOKING, "Candidate NW00000901 already has an active booking on session 154");
        msg.put(ErrorCode.NO_ACCOMMODATION_SEAT, "No accommodation seat available on session 155");
        msg.put(ErrorCode.NOT_CANCELLABLE, "Booking 1201 is CANCELLED");
        msg.put(ErrorCode.INVALID_CAPACITY, "Capacity 1 is invalid for session 154 (must be at least 2, the accommodation capacity)");
        msg.put(ErrorCode.DIFFERENT_EXAM, "Booking 1201 cannot be moved to session 155 (different exam)");
        msg.put(ErrorCode.SAME_SESSION, "Booking 1201 is already on session 154");
        msg.put(ErrorCode.NOT_ELIGIBLE, "Candidate NW00000939 is not eligible to book MHL-RN1 (RETAKE_WAIT_NOT_ELAPSED), "
                + "earliest retake date 2026-12-07");
        msg.put(ErrorCode.OFFER_NOT_FOUND, "Offer 99999 not found");
        msg.put(ErrorCode.OFFER_NOT_PENDING, "Offer 7 is EXPIRED");
        Map<String, Object> out = new LinkedHashMap<>();
        msg.forEach((code, text) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", code.name());
            body.put("message", text);
            if (code != ErrorCode.BAD_REQUEST) {
                body.put("legacyCode", code.legacyCode());
            }
            out.put(code.name(), body);
        });
        return out;
    }

    static String errorTable() {
        StringBuilder sb = new StringBuilder("| error | HTTP | legacyCode |\n|---|---|---|\n");
        for (ErrorCode c : ErrorCode.values()) {
            sb.append("| `").append(c.name()).append("` | ").append(c.httpStatus()).append(" | ")
                    .append(c.legacyCode() == null ? "null" : c.legacyCode()).append(" |\n");
        }
        return sb.toString();
    }

    @Bean
    OpenAPI schedulingOpenApi() {
        String description = """
                Test-centre scheduling API of the Northwind Assessment Network (scheduling-service, MBA-3821).
                Consumers: the modern UI under `/app` and the routing gateway's `/api/v1` -> `/api/v2` translation (M3).
                The committed contract is `services/scheduling-service/api/openapi.yaml`; breaking changes against
                `api/baseline/` fail the build (OpenApiCompatibilityTest). Rule decisions: docs/adr/0002-ported-rule-decisions.md.

                ## Errors
                Every 4xx is an `ApiError`: `error` is the symbolic code, `message` is human readable, and `legacyCode` is
                `50000 + RAISERROR state` of the legacy procs (ODD-11, HB-14), e.g. `NO_ACCOMMODATION_SEAT` = 50003, or
                null when the rule never existed as a proc error. Malformed input (unreadable JSON, wrong path/param type)
                answers `BAD_REQUEST` without `legacyCode`. The offer operations (MBA-3829) answer `OFFER_NOT_FOUND` and
                `OFFER_NOT_PENDING` as an `OfferError` (same shape).

                """ + errorTable();
        return new OpenAPI()
                .info(new Info().title("Northwind Scheduling API").version(API_VERSION).description(description))
                .servers(List.of(new Server().url("/").description("scheduling-service (default :8090)")))
                .tags(List.of(new Tag().name("Bookings"), new Tag().name("Candidates"), new Tag().name("Capacity"),
                        new Tag().name("Offers"), new Tag().name("Reference data"), new Tag().name("Sessions")))
                .components(new Components().addSecuritySchemes(SERVICE_TOKEN, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(AttendanceTokenGuard.HEADER)
                        .description("Service credential (scheduling.attendance.service-token); results-service only")));
    }

    static Schema<?> errorSchema() {
        return errorSchema(Arrays.stream(ErrorCode.values())
                        .filter(c -> c != ErrorCode.UNAUTHORIZED && !OFFER_ERRORS.contains(c)).toList(),
                "Error body of every 4xx answer (ApiExceptionHandler)", "No accommodation seat available on session 155");
    }

    static Schema<?> serviceTokenErrorSchema() {
        return errorSchema(List.of(ErrorCode.UNAUTHORIZED), "401 body when X-Service-Token is missing or invalid",
                "X-Service-Token missing or invalid");
    }

    static Schema<?> offerErrorSchema() {
        return errorSchema(Arrays.stream(ErrorCode.values()).filter(OFFER_ERRORS::contains).toList(),
                "404/409 body of the waitlist offer operations (MBA-3829)", "Offer 7 is EXPIRED");
    }

    static Schema<?> errorSchema(List<ErrorCode> errorCodes, String description, String exampleMessage) {
        List<String> codes = errorCodes.stream().map(Enum::name).toList();
        IntegerSchema legacy = new IntegerSchema();
        legacy.setNullable(true);
        legacy.setDescription("50000 + legacy proc RAISERROR state (ODD-11): 50001..50006, see the table in the API "
                + "description; null when the rule had no proc error. Present on /api/v2 rule errors, absent for BAD_REQUEST "
                + "and on /api/v1.");
        legacy.setExample(codes.contains("NO_ACCOMMODATION_SEAT") ? 50003 : null);
        StringSchema error = new StringSchema();
        error._enum(codes).description("Symbolic error code (booking-api vocabulary)").example(codes.contains("NO_ACCOMMODATION_SEAT") ? "NO_ACCOMMODATION_SEAT" : codes.get(0));
        Schema<?> s = new ObjectSchema()
                .description(description)
                .addProperty("error", error)
                .addProperty("message", new StringSchema().description("Human-readable message")
                        .example(exampleMessage))
                .addProperty("legacyCode", legacy);
        s.setRequired(List.of("error", "message"));
        return s;
    }

    @Bean
    OpenApiCustomizer schedulingApiDocumentation() {
        return this::document;
    }

    void document(OpenAPI api) {
        if (api.getComponents() == null) {
            api.setComponents(new Components());
        }
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        if (schemas != null) {
            schemas.forEach(this::documentSchema);
        }
        api.getComponents().addSchemas(ERROR_SCHEMA, errorSchema());
        api.getComponents().addSchemas(SERVICE_TOKEN_ERROR_SCHEMA, serviceTokenErrorSchema());
        api.getComponents().addSchemas(OFFER_ERROR_SCHEMA, offerErrorSchema());
        if (api.getPaths() == null) {
            return;
        }
        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> documentOperation(op)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void documentSchema(String name, Schema schema) {
        if (ERROR_SCHEMA.equals(name) || SERVICE_TOKEN_ERROR_SCHEMA.equals(name) || OFFER_ERROR_SCHEMA.equals(name)
                || schema.getProperties() == null) {
            return;
        }
        Map<String, Schema> props = schema.getProperties();
        // LocalDateTime carries no offset, so it is not an RFC 3339 date-time
        props.replaceAll((p, ps) -> "date-time".equals(ps.getFormat()) ? new StringSchema()
                .pattern(LOCAL_DATE_TIME_PATTERN)
                .description(SESSION_TIMES.contains(p)
                        ? "Session start in the test centre's wall-clock time (see the centre's timeZone), ISO-8601 without offset"
                        : "Server-local clock of the system that recorded it: the scheduling service for new writes, the legacy "
                                + "estate for imported rows (not centre-local; docs/as-is/smoke-test.md, HB-25, MR-06), "
                                + "ISO-8601 without offset") : ps);
        if (name.endsWith("Dto")) {
            // Jackson default-property-inclusion: always -> every response property is always present
            schema.setRequired(new ArrayList<>(new TreeMap<>(props).keySet()));
            Set<String> nullable = NULLABLE.getOrDefault(name, Set.of());
            props.forEach((p, ps) -> {
                if (nullable.contains(p)) {
                    ps.setNullable(true);
                }
            });
        }
        if (REQUIRED_REQUEST.containsKey(name)) {
            // optional request properties accept JSON null (the UI sends accommodationCode: null)
            List<String> required = REQUIRED_REQUEST.get(name);
            schema.setRequired(required);
            props.forEach((p, ps) -> {
                if (!required.contains(p)) {
                    ps.setNullable(true);
                }
            });
        }
        props.forEach((p, ps) -> {
            if (ps.getExample() == null && PROPERTY_EXAMPLES.containsKey(p) && ps.get$ref() == null) {
                ps.setExample(PROPERTY_EXAMPLES.get(p));
            }
        });
        Schema status = props.get("status");
        if (status != null) {
            if (name.equals("SessionDto")) {
                status.setEnum(Arrays.stream(SessionStatus.values()).map(Enum::name).toList());
                status.setExample(SessionStatus.OPEN.name());
            } else if (name.equals("OfferDto")) {
                status.setEnum(Arrays.stream(com.northwind.scheduling.domain.OfferStatus.values()).map(Enum::name).toList());
                status.setExample("PENDING");
            } else if (name.startsWith("Booking")) {
                status.setEnum(Arrays.stream(BookingStatus.values()).map(Enum::name).toList());
                status.setExample(BookingStatus.BOOKED.name());
            }
        }
        Schema channel = props.get("channel");
        if (channel != null) {
            channel.setEnum(name.equals("BookRequest") ? List.of("PORTAL", "PARTNER") : List.of("PORTAL", "PARTNER", "IMPORT"));
            channel.setExample("PORTAL");
            if (name.equals("BookRequest")) {
                channel.setDescription("Booking channel; defaults to PORTAL (scheduling.default-channel)");
            }
        }
        if (name.equals("EligibilityDto")) {
            Schema reason = props.get("reason");
            reason.setEnum(List.of("FIRST_ATTEMPT", "ALREADY_PASSED", "RETAKE_WAIT_NOT_ELAPSED", "RETAKE_ALLOWED"));
            reason.setExample("RETAKE_WAIT_NOT_ELAPSED");
            props.get("message").setExample("Retake not allowed until 07/12/2026");
            props.get("eligible").setExample(false);
        }
        if (name.equals("CapacityResultDto")) {
            props.get("oldCapacity").setExample(20);
            props.get("newCapacity").setExample(5);
        }
    }

    void documentOperation(Operation op) {
        String id = op.getOperationId();
        String[] meta = OPERATIONS.get(id);
        if (meta != null) {
            op.setTags(List.of(meta[0]));
            op.setSummary(meta[1]);
        }
        if (op.getParameters() != null) {
            Set<String> requiredParams = REQUIRED_PARAMS.getOrDefault(id, Set.of());
            op.getParameters().stream().filter(p -> requiredParams.contains(p.getName())).forEach(p -> p.setRequired(true));
        }
        if (SECURED.contains(id)) {
            op.addSecurityItem(new SecurityRequirement().addList(SERVICE_TOKEN));
        }
        ApiResponses responses = op.getResponses();
        if (responses == null) {
            return;
        }
        if (CREATED.contains(id) && responses.containsKey("200")) {
            ApiResponse ok = responses.remove("200");
            ok.setDescription("Created");
            responses.addApiResponse("201", ok);
        }
        Map<Integer, List<ErrorCode>> byStatus = new TreeMap<>();
        for (ErrorCode c : ERRORS.getOrDefault(id, List.of())) {
            byStatus.computeIfAbsent(c.httpStatus(), k -> new ArrayList<>()).add(c);
        }
        byStatus.forEach((status, codes) -> {
            MediaType mt = new MediaType().schema(new Schema<>().$ref("#/components/schemas/"
                    + (status == 401 ? SERVICE_TOKEN_ERROR_SCHEMA
                    : OFFER_ERRORS.containsAll(codes) ? OFFER_ERROR_SCHEMA : ERROR_SCHEMA)));
            for (ErrorCode c : codes) {
                mt.addExamples(c.name(), new Example().summary(c.name() + (c.legacyCode() == null ? ""
                        : " (legacy " + c.legacyCode() + ")")).value(ERROR_EXAMPLES.get(c.name())));
            }
            String text = switch (status) {
                case 400 -> "Invalid input";
                case 401 -> "Unauthorized";
                case 404 -> "Not found";
                default -> "Rule violation";
            };
            responses.addApiResponse(String.valueOf(status), new ApiResponse()
                    .description(text + ": " + String.join(", ", codes.stream().map(Enum::name).toList()))
                    .content(new Content().addMediaType("application/json", mt)));
        });
    }
}
