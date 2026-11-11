package com.northwind.ad.results.it;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Stands in for scheduling-service + its outbox relay in the results-service ITs: POST
 * /api/v2/bookings/{id}/attendance records the call, then projects ATTENDED into dbo.Booking the way the relay
 * does (sa connection, SESSION_CONTEXT writer = scheduling-relay so trigger side effects are skipped).
 */
public final class SchedulingStub {

    private static final Pattern ATTENDANCE = Pattern.compile("^/api/v2/bookings/(\\d+)/attendance$");
    private static SchedulingStub instance;

    private final HttpServer server;
    private final List<Integer> attendanceCalls = new CopyOnWriteArrayList<Integer>();

    private SchedulingStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public static synchronized SchedulingStub get() {
        if (instance == null) {
            try {
                instance = new SchedulingStub();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return instance;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Integer> attendanceCalls() {
        return attendanceCalls;
    }

    private void handle(HttpExchange ex) throws IOException {
        Matcher m = ATTENDANCE.matcher(ex.getRequestURI().getPath());
        int code;
        String body;
        if ("POST".equals(ex.getRequestMethod()) && m.matches()) {
            int bookingId = Integer.parseInt(m.group(1));
            attendanceCalls.add(bookingId);
            int n = SqlServerDb.saJdbc().update("EXEC sp_set_session_context N'writer', N'scheduling-relay'; "
                    + "UPDATE dbo.Booking SET Status = 'ATTENDED' WHERE BookingId = ? AND Status IN ('BOOKED','ATTENDED')",
                    bookingId);
            code = n == 1 ? 200 : 409;
            body = "{\"bookingId\":" + bookingId + ",\"status\":\"" + (n == 1 ? "ATTENDED" : "CONFLICT") + "\"}";
        } else {
            code = 404;
            body = "{}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
