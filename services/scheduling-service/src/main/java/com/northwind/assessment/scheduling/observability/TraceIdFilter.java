package com.northwind.assessment.scheduling.observability;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts {@code traceId} into the MDC for the whole request: the trace id of a valid W3C {@code traceparent}, else a
 * well-formed {@code X-Request-Id}, else a generated one. {@code X-Request-Id} is echoed on the response (the
 * caller's value if it sent one, otherwise the trace id). API and internal calls get one completion log line
 * (method, path, status, duration; no query string, no body). The MDC is cleared when the request ends.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACEPARENT = "traceparent";
    public static final String REQUEST_ID = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final Pattern TRACEPARENT_FORMAT =
            Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final String INVALID_TRACE_ID = "00000000000000000000000000000000";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = safeRequestId(request.getHeader(REQUEST_ID));
        String traceId = traceId(request.getHeader(TRACEPARENT), requestId);
        LogContext.clearAll();
        MDC.put(LogContext.TRACE_ID, traceId);
        response.setHeader(REQUEST_ID, requestId != null ? requestId : traceId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (logged(request.getRequestURI())) {
                log.info("request completed", kv("method", request.getMethod()), kv("path", request.getRequestURI()),
                        kv("status", response.getStatus()), kv("durationMs", (System.nanoTime() - start) / 1_000_000));
            }
            LogContext.clearAll();
        }
    }

    static String traceId(String traceparent, String requestId) {
        if (traceparent != null) {
            String tp = traceparent.trim().toLowerCase(Locale.ROOT);
            Matcher m = TRACEPARENT_FORMAT.matcher(tp);
            if (m.matches() && !INVALID_TRACE_ID.equals(m.group(1)) && !tp.startsWith("ff")) {
                return m.group(1);
            }
        }
        if (requestId != null) {
            return requestId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    static String safeRequestId(String header) {
        if (header == null) {
            return null;
        }
        String v = header.trim();
        return SAFE_REQUEST_ID.matcher(v).matches() ? v : null;
    }

    private static boolean logged(String uri) {
        return uri != null && (uri.startsWith("/api/") || uri.startsWith("/internal/"));
    }
}
