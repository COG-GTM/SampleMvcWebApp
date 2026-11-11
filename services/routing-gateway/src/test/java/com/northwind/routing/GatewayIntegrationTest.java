package com.northwind.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * End-to-end through the real gateway against stub upstreams: portal (:8080), booking-api (:8081) and
 * scheduling-service (:8090). BOS01 is allow-listed, CHI01 is not.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"routing.config-poll-interval=200ms", "routing.resolve-cache-ttl=0s",
                "routing.coexistence-mode=none"})
class GatewayIntegrationTest {

    static final Path CONFIG;
    static final Stub PORTAL = new Stub(c -> new Stub.Answer(200, "text/html", "<h1>legacy " + c.uri() + "</h1>"));
    static final Stub BOOKING_API = new Stub(c -> c.method().equals("POST")
            ? Stub.Answer.json(201, "{\"bookingId\":3700,\"status\":\"BOOKED\"}")
            : Stub.Answer.json(200, "{\"legacy\":true}"));
    static final Stub SCHEDULING = new Stub(GatewayIntegrationTest::scheduling);

    static {
        try {
            CONFIG = Files.createTempFile("routing", ".properties");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        writeConfig("BOS01");
        System.setProperty("ROUTING_CONFIG_FILE", CONFIG.toString());
    }

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry r) {
        r.add("routing.portal-url", PORTAL::url);
        r.add("routing.booking-api-url", BOOKING_API::url);
        r.add("routing.scheduling-service-url", SCHEDULING::url);
        r.add("routing.config-file", CONFIG::toString);
    }

    static Stub.Answer scheduling(Stub.Call c) {
        String u = c.uri();
        if (u.startsWith("/api/v2/internal/routing/resolve")) {
            String center = u.contains("sessionId=154") || u.contains("bookingId=5000000") || u.contains("centerId=1&") || u.endsWith("centerId=1")
                    || u.contains("centerCode=BOS01") ? "BOS01"
                    : u.contains("sessionId=200") || u.contains("bookingId=1200") || u.contains("centerCode=CHI01") ? "CHI01"
                    : u.contains("centerCode=SEA01") ? "SEA01" : null;
            return center == null ? Stub.Answer.json(404, "{\"error\":\"SESSION_NOT_FOUND\"}")
                    : Stub.Answer.json(200, "{\"centerCode\":\"" + center + "\",\"target\":\"legacy\",\"reason\":\"x\"}");
        }
        if (c.method().equals("POST") && u.equals("/api/v2/bookings")) {
            return Stub.Answer.json(201, "{\"bookingId\":50000001,\"status\":\"BOOKED\",\"waitlistPosition\":null,"
                    + "\"booking\":{\"bookingId\":50000001},\"session\":{\"sessionId\":154}}");
        }
        if (c.method().equals("POST") && u.equals("/api/v2/bookings/50000001/reschedule")) {
            return Stub.Answer.json(200, "{\"bookingId\":50000002,\"status\":\"BOOKED\",\"booking\":{\"bookingId\":50000002}}");
        }
        if (c.method().equals("DELETE") && u.startsWith("/api/v2/bookings/50000001")) {
            return Stub.Answer.json(200, "{\"bookingId\":50000001,\"sessionId\":154,\"candidateNumber\":\"NW00000901\","
                    + "\"candidateName\":\"A\",\"status\":\"CANCELLED\",\"channel\":\"PARTNER\",\"bookedAt\":\"2026-11-10T08:00:00\"}");
        }
        if (u.startsWith("/api/v2/centers/BOS01/sessions")) {
            return Stub.Answer.json(200, "[{\"sessionId\":154,\"centerCode\":\"BOS01\",\"startsAt\":\"2026-11-12T09:00:00\",\"waitlistCount\":0}]");
        }
        return new Stub.Answer(200, "text/html", "<div id=root>modern " + u + "</div>");
    }

    static void writeConfig(String centers) {
        try {
            Files.writeString(CONFIG, "routing.modern-centers=" + centers + "\n# " + System.nanoTime() + "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stop() {
        PORTAL.server.stop(0);
        BOOKING_API.server.stop(0);
        SCHEDULING.server.stop(0);
        System.clearProperty("ROUTING_CONFIG_FILE");
    }

    @Autowired
    WebTestClient web;

    @BeforeEach
    void allowListBos01() {
        web = web.mutate().responseTimeout(Duration.ofSeconds(20)).build();
        writeConfig("BOS01");
        web.post().uri("/actuator/refresh").exchange().expectStatus().isOk();
    }

    @Test
    void nonBookingTrafficIsProxiedToLegacyUnchanged() {
        web.get().uri("/Results?candidateNumber=NW00000001").exchange()
                .expectStatus().isOk().expectHeader().valueEquals("X-Route", "legacy")
                .expectBody(String.class).isEqualTo("<h1>legacy /Results?candidateNumber=NW00000001</h1>");
        web.get().uri("/Booking?CenterCode=CHI01").exchange()
                .expectStatus().isOk().expectHeader().valueEquals("X-Route", "legacy");
    }

    @Test
    void partnerBookingOnAllowListedCentreIsServedByModernInV1Shape() {
        int before = BOOKING_API.calls("POST", "/api/v1/bookings").size();
        web.post().uri("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":154,\"candidateNumber\":\"NW00000901\",\"externalRef\":\"P-1\"}")
                .exchange().expectStatus().isCreated().expectHeader().valueEquals("X-Route", "modern")
                .expectBody().json("{\"bookingId\":50000001,\"status\":\"BOOKED\"}", true);
        assertThat(BOOKING_API.calls("POST", "/api/v1/bookings")).hasSize(before);
        Stub.Call v2 = SCHEDULING.calls("POST", "/api/v2/bookings").get(SCHEDULING.calls("POST", "/api/v2/bookings").size() - 1);
        assertThat(v2.body()).contains("\"sessionId\":154").contains("\"channel\":\"PARTNER\"").contains("\"externalRef\":\"P-1\"");
    }

    @Test
    void partnerBookingOnOtherCentreGoesToLegacyBookingApiWithTheSameBody() {
        String body = "{\"sessionId\":200,\"candidateNumber\":\"NW00000902\"}";
        web.post().uri("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isCreated().expectHeader().valueEquals("X-Route", "legacy")
                .expectBody().json("{\"bookingId\":3700,\"status\":\"BOOKED\"}", true);
        var calls = BOOKING_API.calls("POST", "/api/v1/bookings");
        assertThat(calls.get(calls.size() - 1).body()).isEqualTo(body);
    }

    @Test
    void portalPagesForAllowListedCentreRedirectToModernUi() {
        web.get().uri("/Booking?ExamCode=AZ-104&CenterCode=BOS01&CandidateNumber=NW00000901").exchange()
                .expectStatus().isFound().expectHeader().valueEquals("X-Route", "modern")
                .expectHeader().valueEquals("Location", "/app/?center=BOS01&exam=AZ-104");
        web.post().uri("/Booking/Book").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("__RequestVerificationToken=t&SessionId=154&CandidateNumber=NW00000901").exchange()
                .expectStatus().isSeeOther().expectHeader().valueEquals("Location", "/app/book?sessionId=154");
        web.post().uri("/Booking/Book").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("SessionId=200&CandidateNumber=NW00000901").exchange()
                .expectStatus().isOk().expectHeader().valueEquals("X-Route", "legacy");
        assertThat(PORTAL.calls("POST", "/Booking/Book").get(PORTAL.calls("POST", "/Booking/Book").size() - 1).body())
                .isEqualTo("SessionId=200&CandidateNumber=NW00000901");
        web.get().uri("/Admin/Sessions?centerId=1").exchange()
                .expectStatus().isFound().expectHeader().valueEquals("Location", "/app/admin/centers/BOS01");
        web.get().uri("/Admin/Sessions/Capacity/154").exchange()
                .expectStatus().isFound().expectHeader().valueEquals("Location", "/app/admin/sessions/154");
        web.get().uri("/app/book?sessionId=154").exchange()
                .expectStatus().isOk().expectHeader().valueEquals("X-Route", "modern");
    }

    @Test
    void v1SessionSearchForAllowListedCentreUsesV2() {
        web.get().uri("/api/v1/sessions?centerCode=BOS01").exchange()
                .expectStatus().isOk().expectHeader().valueEquals("X-Route", "modern")
                .expectBody().jsonPath("$[0].sessionId").isEqualTo(154).jsonPath("$[0].waitlistCount").doesNotExist();
        web.get().uri("/api/v1/sessions?centerCode=CHI01").exchange()
                .expectHeader().valueEquals("X-Route", "legacy").expectBody().json("{\"legacy\":true}");
    }

    @Test
    void rollbackByEditingTheFileRoutesToLegacyWithinFiveSecondsAndModernBookingsStayModern() throws Exception {
        writeConfig("");
        long deadline = System.currentTimeMillis() + 5000;
        String route = "modern";
        while (System.currentTimeMillis() < deadline) {
            route = web.get().uri("/Booking?CenterCode=BOS01").exchange().returnResult(String.class)
                    .getResponseHeaders().getFirst("X-Route");
            if ("legacy".equals(route)) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(route).isEqualTo("legacy");
        web.delete().uri("/api/v1/bookings/50000001").exchange()
                .expectStatus().isOk().expectHeader().valueEquals("X-Route", "modern")
                .expectBody().jsonPath("$.status").isEqualTo("CANCELLED").jsonPath("$.candidateName").doesNotExist();
        web.post().uri("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"sessionId\":154}")
                .exchange().expectHeader().valueEquals("X-Route", "legacy");
    }

    @Test
    void refreshEndpointReloadsAndInvalidListIsRejected() {
        writeConfig("SEA01");
        web.post().uri("/actuator/refresh").exchange().expectStatus().isOk();
        web.get().uri("/Booking?CenterCode=BOS01").exchange().expectHeader().valueEquals("X-Route", "legacy");
        writeConfig("BOS01,LON01");
        web.post().uri("/actuator/refresh").exchange().expectStatus().isOk();
        web.get().uri("/Booking?CenterCode=SEA01").exchange().expectStatus().isFound();
    }

    @Test
    void v2WritesOnNonAllowListedCentresAreRefused() {
        web.post().uri("/api/v2/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("sessionId", 200, "candidateNumber", "NW00000901")).exchange()
                .expectStatus().isForbidden().expectBody().jsonPath("$.error").isEqualTo("CENTER_NOT_MODERN");
        web.post().uri("/api/v2/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("sessionId", 154, "candidateNumber", "NW00000901")).exchange()
                .expectStatus().isCreated().expectHeader().valueEquals("X-Route", "modern");
        web.put().uri("/api/v2/sessions/200/capacity").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("capacity", 5)).exchange().expectStatus().isForbidden();
    }

    @Test
    void internalSchedulingEndpointsAreNotExposed() {
        web.post().uri("/api/v2/internal/import/sessions?mode=reset").exchange()
                .expectStatus().isNotFound().expectBody().jsonPath("$.error").isEqualTo("NOT_FOUND");
        web.get().uri("/api/v2//Internal/routing/resolve?centerCode=BOS01").exchange().expectStatus().isNotFound();
    }

    @Test
    void attendanceIsNotExposedThroughTheGateway() {
        web.post().uri("/api/v2/bookings/50000001/attendance").header("X-Service-Token", "anything").exchange()
                .expectStatus().isNotFound().expectHeader().valueEquals("X-Route", "modern")
                .expectBody().jsonPath("$.error").isEqualTo("NOT_FOUND");
        web.post().uri("/api/v2/bookings/1200/Attendance").exchange().expectStatus().isNotFound();
    }

    @Test
    void v2WritesWhoseCentreCannotBeResolvedAreRefused() {
        web.post().uri("/api/v2/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("sessionId", 999, "candidateNumber", "NW00000901")).exchange()
                .expectStatus().isForbidden().expectBody().jsonPath("$.error").isEqualTo("CENTER_NOT_MODERN");
    }

    @Test
    void reschedulesNeverMoveABookingOntoASeatTheOtherSideOwns() {
        String toChi01 = "{\"newSessionId\":200}";
        String toBos01 = "{\"newSessionId\":154}";
        web.post().uri("/api/v1/bookings/50000001/reschedule").contentType(MediaType.APPLICATION_JSON).bodyValue(toChi01)
                .exchange().expectStatus().isForbidden().expectHeader().valueEquals("X-Route", "modern")
                .expectBody().jsonPath("$.error").isEqualTo("CENTER_NOT_MODERN");
        web.post().uri("/api/v1/bookings/50000001/reschedule").contentType(MediaType.APPLICATION_JSON).bodyValue(toBos01)
                .exchange().expectStatus().isOk().expectHeader().valueEquals("X-Route", "modern")
                .expectBody().json("{\"bookingId\":50000002,\"status\":\"BOOKED\"}", true);
        web.post().uri("/api/v1/bookings/1200/reschedule").contentType(MediaType.APPLICATION_JSON).bodyValue(toBos01)
                .exchange().expectStatus().isForbidden().expectHeader().valueEquals("X-Route", "legacy")
                .expectBody().jsonPath("$.error").isEqualTo("CENTER_IS_MODERN");
        writeConfig("");
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && !"legacy".equals(web.get().uri("/Booking?CenterCode=BOS01")
                .exchange().returnResult(String.class).getResponseHeaders().getFirst("X-Route"))) {
            Thread.yield();
        }
        web.post().uri("/api/v1/bookings/50000001/reschedule").contentType(MediaType.APPLICATION_JSON).bodyValue(toBos01)
                .exchange().expectStatus().isOk().expectHeader().valueEquals("X-Route", "modern");
    }
}
