package com.northwind.assessment.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.northwind.assessment.gateway.resolve.CenterResolver;
import com.northwind.assessment.gateway.routing.AllowListHolder;

/**
 * Gateway wiring against WireMock stand-ins for the portal, booking-api, results-service, capacity-sync and
 * scheduling-service (integration contract items 3, 5, 6).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    static final WireMockServer portal = start();
    static final WireMockServer bookingApi = start();
    static final WireMockServer results = start();
    static final WireMockServer capacitySync = start();
    static final WireMockServer scheduling = start();
    static final Path configFile = tempFile();

    private static WireMockServer start() {
        WireMockServer s = new WireMockServer(options().dynamicPort());
        s.start();
        return s;
    }

    private static Path tempFile() {
        try {
            Path p = Files.createTempFile("routing", ".properties");
            Files.writeString(p, "routing.modern-centers=\n");
            return p;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("routing.portal-url", portal::baseUrl);
        r.add("routing.booking-api-url", bookingApi::baseUrl);
        r.add("routing.results-service-url", results::baseUrl);
        r.add("routing.capacity-sync-url", capacitySync::baseUrl);
        r.add("routing.scheduling-service-url", scheduling::baseUrl);
        r.add("routing.config-file", configFile::toString);
        r.add("routing.watch-interval", () -> "500ms");
    }

    @AfterAll
    static void stop() {
        portal.stop();
        bookingApi.stop();
        results.stop();
        capacitySync.stop();
        scheduling.stop();
    }

    @LocalServerPort
    int port;
    @Autowired
    AllowListHolder allowList;
    @Autowired
    CenterResolver resolver;

    WebTestClient client;

    static final String LEGACY_BOOKED = "{\n  \"bookingId\" : 3588,\n  \"status\" : \"BOOKED\"\n}";

    @BeforeEach
    void setUp() throws Exception {
        for (WireMockServer s : new WireMockServer[] {portal, bookingApi, results, capacitySync, scheduling}) {
            s.resetAll();
        }
        setAllowList("");
        resolver.clear();
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10)).build();

        bookingApi.stubFor(post("/api/v1/bookings").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json;charset=UTF-8").withBody(LEGACY_BOOKED)));
        bookingApi.stubFor(get(urlPathMatching("/api/v1/.*")).willReturn(okJson("{\"legacy\":true}")));
        portal.stubFor(any(anyUrl()).willReturn(ok("<html>legacy portal</html>")));
        stubResolve("sessionId=100", "BOS01", 1, 100, null, null);
        stubResolve("sessionId=200", "NYC01", 2, 200, null, null);
        stubResolve("centerCode=BOS01", "BOS01", 1, null, null, null);
        stubResolve("centerCode=NYC01", "NYC01", 2, null, null, null);
        stubResolve("centerId=1", "BOS01", 1, null, null, null);
        stubResolve("bookingId=50000001", "NYC01", 2, 200, 50000001L, "MODERN");
        stubResolve("bookingId=300", "BOS01", 1, 100, 300L, "LEGACY_IMPORT");
        scheduling.stubFor(get(urlPathEqualTo("/internal/routing/resolve")).atPriority(10)
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"NOT_FOUND\",\"message\":\"x\"}")));
    }

    private static void stubResolve(String query, String code, int centerId, Integer sessionId, Long bookingId,
            String origin) {
        String[] kv = query.split("=");
        scheduling.stubFor(get(urlPathEqualTo("/internal/routing/resolve")).withQueryParam(kv[0], equalTo(kv[1]))
                .atPriority(1)
                .willReturn(okJson(String.format("{\"centerCode\":\"%s\",\"centerId\":%d,\"sessionId\":%s,"
                        + "\"bookingId\":%s,\"bookingOrigin\":%s}", code, centerId, sessionId, bookingId,
                        origin == null ? "null" : "\"" + origin + "\""))
                        .withHeader("Cache-Control", "max-age=60")));
    }

    private void setAllowList(String centers) throws Exception {
        Files.writeString(configFile, "routing.modern-centers=" + centers + "\n");
        client().post().uri("/actuator/refresh").exchange().expectStatus().isOk();
        assertThat(String.join(",", allowList.get().modernCenters())).isEqualTo(centers);
    }

    private WebTestClient client() {
        return client != null ? client : WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private EntityExchangeResult<String> postBooking(int sessionId) {
        return client.post().uri("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":" + sessionId + ",\"candidateNumber\":\"NW00000901\"}")
                .exchange().expectBody(String.class).returnResult();
    }

    @Test
    void emptyAllowListPassesBookingToLegacyWithoutAnyResolveCall() {
        EntityExchangeResult<String> r = postBooking(100);
        assertThat(r.getStatus().value()).isEqualTo(201);
        assertThat(r.getResponseHeaders().getFirst("X-Route")).isEqualTo("legacy");
        assertThat(r.getResponseBody()).isEqualTo(LEGACY_BOOKED);
        bookingApi.verify(postRequestedFor(urlEqualTo("/api/v1/bookings"))
                .withRequestBody(equalToJson("{\"sessionId\":100,\"candidateNumber\":\"NW00000901\"}")));
        scheduling.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allowListedCenterBookingIsTranslatedToV2WithV1ShapePreserved() throws Exception {
        setAllowList("BOS01");
        scheduling.stubFor(post("/api/v2/bookings").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{\"bookingId\":50000007,\"status\":\"BOOKED\","
                        + "\"sessionId\":100,\"candidateNumber\":\"NW00000901\",\"accommodationCode\":null,"
                        + "\"channel\":\"PARTNER\",\"waitlistPosition\":null,\"seatsAvailable\":11}")));

        EntityExchangeResult<String> r = postBooking(100);

        assertThat(r.getStatus().value()).isEqualTo(201);
        assertThat(r.getResponseHeaders().getFirst("X-Route")).isEqualTo("modern");
        assertThat(r.getResponseHeaders().getFirst("Content-Type")).isEqualTo("application/json;charset=UTF-8");
        assertThat(r.getResponseBody()).isEqualTo("{\n  \"bookingId\" : 50000007,\n  \"status\" : \"BOOKED\"\n}");
        scheduling.verify(postRequestedFor(urlEqualTo("/api/v2/bookings")).withRequestBody(equalToJson(
                "{\"sessionId\":100,\"candidateNumber\":\"NW00000901\",\"accommodationCode\":null,"
                        + "\"externalRef\":null,\"channel\":\"PARTNER\"}")));
        bookingApi.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void nonAllowListedCenterStaysLegacyWhileAnotherCenterIsAllowListed() throws Exception {
        setAllowList("BOS01");
        EntityExchangeResult<String> r = postBooking(200);
        assertThat(r.getResponseHeaders().getFirst("X-Route")).isEqualTo("legacy");
        assertThat(r.getResponseBody()).isEqualTo(LEGACY_BOOKED);
        scheduling.verify(0, postRequestedFor(urlPathEqualTo("/api/v2/bookings")));
    }

    @Test
    void v2ErrorIsMappedToV1ErrorBodyWithSameStatus() throws Exception {
        setAllowList("BOS01");
        scheduling.stubFor(post("/api/v2/bookings").willReturn(aResponse().withStatus(409)
                .withBody("{\"error\":\"DUPLICATE_BOOKING\",\"message\":\"Candidate already booked\",\"legacyCode\":50002}")));
        EntityExchangeResult<String> r = postBooking(100);
        assertThat(r.getStatus().value()).isEqualTo(409);
        assertThat(r.getResponseBody())
                .isEqualTo("{\n  \"error\" : \"DUPLICATE_BOOKING\",\n  \"message\" : \"Candidate already booked\"\n}");
    }

    @Test
    void modernBookingIdCancelRoutesModernEvenWithEmptyAllowList() {
        scheduling.stubFor(delete(urlPathEqualTo("/api/v2/bookings/50000001")).willReturn(okJson(
                "{\"bookingId\":50000001,\"sessionId\":200,\"candidateNumber\":\"NW00000901\",\"status\":\"CANCELLED\","
                        + "\"origin\":\"MODERN\",\"cancelReason\":\"Cancelled by partner\"}")));
        EntityExchangeResult<String> r = client.delete().uri("/api/v1/bookings/50000001").exchange()
                .expectBody(String.class).returnResult();
        assertThat(r.getStatus().value()).isEqualTo(200);
        assertThat(r.getResponseHeaders().getFirst("X-Route")).isEqualTo("modern");
        assertThat(r.getResponseBody()).contains("\"cancelReason\" : \"Cancelled by partner\"")
                .doesNotContain("origin");
        scheduling.verify(deleteRequestedFor(urlPathEqualTo("/api/v2/bookings/50000001"))
                .withQueryParam("reason", equalTo("Cancelled by partner")));
        bookingApi.verify(0, deleteRequestedFor(anyUrl()));
    }

    @Test
    void modernBookingIdRescheduleRoutesModernAndAnswers200LikeV1() {
        scheduling.stubFor(post("/api/v2/bookings/50000001/reschedule").willReturn(aResponse().withStatus(201)
                .withBody("{\"bookingId\":50000002,\"status\":\"BOOKED\",\"sessionId\":201}")));
        EntityExchangeResult<String> r = client.post().uri("/api/v1/bookings/50000001/reschedule")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"newSessionId\":201}").exchange()
                .expectBody(String.class).returnResult();
        assertThat(r.getStatus().value()).isEqualTo(200);
        assertThat(r.getResponseHeaders().getFirst("X-Route")).isEqualTo("modern");
        assertThat(r.getResponseBody()).isEqualTo("{\n  \"bookingId\" : 50000002,\n  \"status\" : \"BOOKED\"\n}");
        scheduling.verify(postRequestedFor(urlEqualTo("/api/v2/bookings/50000001/reschedule"))
                .withRequestBody(equalToJson("{\"newSessionId\":201}")));
    }

    @Test
    void legacyImportedBookingOfAllowListedCenterGoesModern() throws Exception {
        setAllowList("BOS01");
        scheduling.stubFor(delete(urlPathEqualTo("/api/v2/bookings/300")).willReturn(okJson("{\"bookingId\":300}")));
        client.delete().uri("/api/v1/bookings/300?reason=ill").exchange()
                .expectHeader().valueEquals("X-Route", "modern");
        scheduling.verify(deleteRequestedFor(urlPathEqualTo("/api/v2/bookings/300"))
                .withQueryParam("reason", equalTo("ill")));
    }

    @Test
    void sessionReadsForAllowListedCenterGoModern() throws Exception {
        setAllowList("BOS01");
        scheduling.stubFor(get("/api/v2/sessions/100").willReturn(okJson(
                "{\"sessionId\":100,\"centerCode\":\"BOS01\",\"bookedSeats\":3,\"waitlistCount\":1}")));
        scheduling.stubFor(get(urlPathEqualTo("/api/v2/centers/BOS01/sessions")).willReturn(okJson("[{\"sessionId\":100}]")));
        client.get().uri("/api/v1/sessions/100").exchange().expectHeader().valueEquals("X-Route", "modern")
                .expectBody(String.class).value(b -> assertThat(b).contains("\"bookedSeats\" : 3")
                        .doesNotContain("waitlistCount"));
        client.get().uri("/api/v1/sessions?centerCode=BOS01&examCode=CTA-NET1").exchange()
                .expectHeader().valueEquals("X-Route", "modern");
        scheduling.verify(getRequestedFor(urlPathEqualTo("/api/v2/centers/BOS01/sessions"))
                .withQueryParam("examCode", equalTo("CTA-NET1")).withoutQueryParam("centerCode"));
        client.get().uri("/api/v1/sessions?centerCode=BOS01&from=2026-11-20&to=2026-11-25").exchange()
                .expectHeader().valueEquals("X-Route", "modern");
        scheduling.verify(getRequestedFor(urlPathEqualTo("/api/v2/centers/BOS01/sessions"))
                .withQueryParam("from", equalTo("2026-11-20")).withQueryParam("to", equalTo("2026-11-26")));
        client.get().uri("/api/v1/sessions?centerCode=%20BOS01").exchange().expectHeader().valueEquals("X-Route", "legacy");
        client.get().uri("/api/v1/sessions?centerCode=NYC01").exchange().expectHeader().valueEquals("X-Route", "legacy");
        client.get().uri("/api/v1/sessions").exchange().expectHeader().valueEquals("X-Route", "legacy");
    }

    @Test
    void rollbackByWatchedFileIsEffectiveWithinFiveSecondsWithoutRestart() throws Exception {
        setAllowList("BOS01");
        scheduling.stubFor(post("/api/v2/bookings").willReturn(aResponse().withStatus(201)
                .withBody("{\"bookingId\":50000009,\"status\":\"BOOKED\"}")));
        assertThat(postBooking(100).getResponseHeaders().getFirst("X-Route")).isEqualTo("modern");

        Files.writeString(configFile, "routing.modern-centers=\n");
        long start = System.nanoTime();
        String route = "modern";
        while (route.equals("modern") && System.nanoTime() - start < Duration.ofSeconds(5).toNanos()) {
            Thread.sleep(100);
            route = allowList.get().isEmpty() ? postBooking(100).getResponseHeaders().getFirst("X-Route") : "modern";
        }
        assertThat(route).isEqualTo("legacy");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void refreshWithProductionCenterIsRejectedAndPreviousListKept() throws Exception {
        setAllowList("BOS01");
        Files.writeString(configFile, "routing.modern-centers=BOS01,NYC01\n");
        client.post().uri("/actuator/refresh").exchange().expectStatus().isOk();
        assertThat(allowList.get().modernCenters()).containsExactly("BOS01");
        assertThat(allowList.getLastRejection()).contains("NYC01");
    }

    @Test
    void writeFailsClosedWhenResolveIsUnavailableButReadsFallBackToLegacy() throws Exception {
        setAllowList("BOS01");
        scheduling.stubFor(get(urlPathEqualTo("/internal/routing/resolve")).atPriority(0)
                .willReturn(aResponse().withStatus(500)));
        EntityExchangeResult<String> w = postBooking(999);
        assertThat(w.getStatus().value()).isEqualTo(503);
        assertThat(w.getResponseBody()).contains("ROUTING_UNAVAILABLE");
        bookingApi.verify(0, postRequestedFor(anyUrl()));
        client.get().uri("/api/v1/sessions/999").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Route", "legacy");
    }

    @Test
    void resolve400ForAnIdSchedulingServiceCannotParseMeansUnknownToModern() throws Exception {
        setAllowList("BOS01");
        scheduling.stubFor(get(urlPathEqualTo("/internal/routing/resolve")).withQueryParam("sessionId", equalTo("3000000000"))
                .atPriority(0).willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"VALIDATION\",\"message\":\"sessionId must be a number in the int range\"}")));
        client.post().uri("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":3000000000,\"candidateNumber\":\"NW00000901\"}").exchange()
                .expectHeader().valueEquals("X-Route", "legacy");
        bookingApi.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/bookings")));
        client.get().uri("/api/v1/sessions/3000000000").exchange().expectHeader().valueEquals("X-Route", "legacy");
    }

    @Test
    void resolveResultsAreCachedPerCacheControl() throws Exception {
        setAllowList("BOS01");
        scheduling.stubFor(get(urlPathMatching("/api/v2/sessions/100")).willReturn(okJson("{\"sessionId\":100}")));
        client.get().uri("/api/v1/sessions/100").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/sessions/100").exchange().expectStatus().isOk();
        scheduling.verify(1, getRequestedFor(urlPathEqualTo("/internal/routing/resolve")));
    }

    @Test
    void portalBookingSearchForAllowListedCenterRedirectsToModernUi() throws Exception {
        setAllowList("BOS01");
        client.get().uri("/Booking?ExamCode=CTA-NET1&CenterCode=BOS01&From=2026-12-01&CandidateNumber=NW00000901")
                .exchange().expectStatus().isFound()
                .expectHeader().valueEquals("Location", "/app?centerCode=BOS01&examCode=CTA-NET1&date=2026-12-01")
                .expectHeader().valueEquals("X-Route", "modern");
        client.get().uri("/booking/book?sessionId=100&candidateNumber=NW00000901").exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "/app/book?sessionId=100&candidateNumber=NW00000901");
        client.get().uri("/Admin/Sessions?centerId=1").exchange()
                .expectHeader().valueEquals("Location", "/app/admin/centers/BOS01");
        client.get().uri("/Admin/Sessions/Capacity/100").exchange()
                .expectHeader().valueEquals("Location", "/app/admin/sessions/100");
        client.get().uri("/Booking/Reschedule/50000001?candidateNumber=NW00000901").exchange()
                .expectHeader().valueEquals("Location", "/app/reschedule/50000001?candidateNumber=NW00000901");
        client.post().uri("/Booking/Book").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("SessionId=100&CandidateNumber=NW00000901").exchange()
                .expectStatus().isSeeOther();
        portal.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void portalPagesForOtherCentersAndNonBookingPagesPassThrough() throws Exception {
        setAllowList("BOS01");
        for (String uri : new String[] {"/Booking?CenterCode=NYC01", "/Booking", "/Booking/MyBookings?candidateNumber=NW00000901",
                "/Admin/Centers", "/Eligibility", "/Results", "/"}) {
            client.get().uri(uri).exchange().expectStatus().isOk()
                    .expectHeader().valueEquals("X-Route", "legacy")
                    .expectBody(String.class).isEqualTo("<html>legacy portal</html>");
        }
        client.post().uri("/Booking/Book").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("SessionId=200&CandidateNumber=NW00000901").exchange().expectStatus().isOk();
        portal.verify(postRequestedFor(urlEqualTo("/Booking/Book"))
                .withRequestBody(equalTo("SessionId=200&CandidateNumber=NW00000901")));
    }

    @Test
    void modernUiAndOtherLegacyServicesAreProxied() {
        scheduling.stubFor(get(urlPathMatching("/app.*")).willReturn(ok("<html>modern spa</html>")));
        results.stubFor(get(urlPathMatching("/api/v1/results.*")).willReturn(okJson("[]")));
        capacitySync.stubFor(get("/sync/last").willReturn(okJson("{}")));
        client.get().uri("/app/book?sessionId=100").exchange().expectHeader().valueEquals("X-Route", "modern")
                .expectBody(String.class).isEqualTo("<html>modern spa</html>");
        client.get().uri("/api/v1/results/candidate/NW00000001").exchange()
                .expectHeader().valueEquals("X-Route", "legacy");
        client.get().uri("/sync/last").exchange().expectHeader().valueEquals("X-Route", "legacy");
        results.verify(getRequestedFor(urlEqualTo("/api/v1/results/candidate/NW00000001")));
        capacitySync.verify(getRequestedFor(urlEqualTo("/sync/last")));
    }

    @Test
    void numericStringSessionIdOnV1BookingIsRoutedByItsCenter() throws Exception {
        scheduling.stubFor(post("/api/v2/bookings").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{\"bookingId\":50000009,\"status\":\"BOOKED\"}")));
        setAllowList("BOS01");
        client.post().uri("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":\"100\",\"candidateNumber\":\"NW00000901\"}").exchange()
                .expectStatus().isCreated().expectHeader().valueEquals("X-Route", "modern");
        scheduling.verify(postRequestedFor(urlEqualTo("/api/v2/bookings"))
                .withRequestBody(matchingJsonPath("$.sessionId", equalTo("100"))));
        client.post().uri("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":100.0,\"candidateNumber\":\"NW00000901\"}").exchange()
                .expectStatus().isCreated().expectHeader().valueEquals("X-Route", "modern");
        bookingApi.verify(0, postRequestedFor(urlEqualTo("/api/v1/bookings")));
        client.delete().uri("/api/v1/bookings/99999999999999999999").exchange()
                .expectHeader().valueEquals("X-Route", "legacy");
    }

    @Test
    void directV2WritesAreOnlyForwardedForModernCentersOrModernBookings() throws Exception {
        scheduling.stubFor(any(urlPathMatching("/api/v2/.*")).willReturn(okJson("{}")));
        setAllowList("BOS01");
        client.post().uri("/api/v2/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":200,\"candidateNumber\":\"NW00000901\"}").exchange()
                .expectStatus().isForbidden().expectHeader().valueEquals("X-Route", "legacy")
                .expectBody(String.class).value(b -> assertThat(b).contains("CENTER_NOT_MODERN"));
        client.post().uri("/api/v2/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":\"200\",\"candidateNumber\":\"NW00000901\"}").exchange()
                .expectStatus().isForbidden();
        client.post().uri("/api/v2/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":\"abc\",\"candidateNumber\":\"NW00000901\"}").exchange()
                .expectStatus().isForbidden();
        client.put().uri("/api/v2/sessions/200/capacity").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"newCapacity\":5}").exchange().expectStatus().isForbidden();
        client.delete().uri("/api/v2/bookings/99999999999999999999").exchange().expectStatus().isForbidden()
                .expectHeader().valueEquals("X-Route", "legacy");
        scheduling.verify(0, anyRequestedFor(urlPathMatching("/api/v2/.*")));

        client.post().uri("/api/v2/bookings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sessionId\":100,\"candidateNumber\":\"NW00000901\"}").exchange()
                .expectStatus().isOk().expectHeader().valueEquals("X-Route", "modern");
        scheduling.verify(postRequestedFor(urlEqualTo("/api/v2/bookings"))
                .withRequestBody(equalToJson("{\"sessionId\":100,\"candidateNumber\":\"NW00000901\"}")));
        client.put().uri("/api/v2/sessions/100/capacity").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"newCapacity\":5}").exchange().expectStatus().isOk();
        setAllowList("");
        client.delete().uri("/api/v2/bookings/50000001").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Route", "modern");
        client.post().uri("/api/v2/bookings/300/reschedule").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"newSessionId\":100}").exchange().expectStatus().isForbidden();
        client.get().uri("/api/v2/sessions/200").exchange().expectStatus().isOk();
    }
}
