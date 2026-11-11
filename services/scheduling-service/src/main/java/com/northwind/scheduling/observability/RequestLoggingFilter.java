package com.northwind.scheduling.observability;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * One JSON access line per /api request with traceId (Micrometer Tracing MDC) and the centerCode / sessionId /
 * bookingId / candidateNumber known from the path or set by {@link BookingOperationsAspect}. The query string and
 * bodies are never logged. The trace id is echoed as {@code X-Trace-Id} so callers can quote it to on-call.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("com.northwind.scheduling.Requests");

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String CENTER_CODE = "centerCode";
    public static final String SESSION_ID = "sessionId";
    public static final String BOOKING_ID = "bookingId";
    public static final String CANDIDATE_NUMBER = "candidateNumber";

    private static final Pattern SESSION = Pattern.compile("^/api/v\\d+/sessions/(\\d+)(/|$)");
    private static final Pattern BOOKING = Pattern.compile("^/api/v\\d+/bookings/(\\d+)(/|$)");
    private static final Pattern CENTER = Pattern.compile("^/api/v\\d+/centers/([A-Za-z0-9_-]{1,20})(/|$)");
    private static final Pattern CANDIDATE = Pattern.compile("^/api/v\\d+/candidates/(NW\\d{8})(/|$)");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String path = request.getRequestURI();
        putFromPath(path);
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            response.setHeader(TRACE_HEADER, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                log.info("{} {} {}", kv("method", request.getMethod()), kv("path", path),
                        kv("status", response.getStatus()), kv("durationMs", (System.nanoTime() - start) / 1_000_000));
            } finally {
                clearMdc();
            }
        }
    }

    static void putFromPath(String path) {
        put(SESSION, path, SESSION_ID);
        put(BOOKING, path, BOOKING_ID);
        put(CENTER, path, CENTER_CODE);
        put(CANDIDATE, path, CANDIDATE_NUMBER);
    }

    private static void put(Pattern p, String path, String key) {
        Matcher m = p.matcher(path);
        if (m.find()) {
            MDC.put(key, m.group(1));
        }
    }

    static void clearMdc() {
        MDC.remove(CENTER_CODE);
        MDC.remove(SESSION_ID);
        MDC.remove(BOOKING_ID);
        MDC.remove(CANDIDATE_NUMBER);
    }
}
