package com.northwind.scheduling.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import com.northwind.scheduling.domain.SchedulingException;
import com.northwind.scheduling.persistence.Rows;
import com.northwind.scheduling.persistence.SchedulingRepository;
import com.northwind.scheduling.service.SchedulingService;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Times {@code SchedulingService.book/cancel/reschedule/changeCapacity} outside their transaction (highest
 * precedence, so commit time counts), records {@link BookingMetrics} and writes one structured log line per
 * command with centerCode/sessionId/bookingId/candidateNumber in the MDC. No candidate PII beyond candidateNumber.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BookingOperationsAspect {
    private static final Logger log = LoggerFactory.getLogger("com.northwind.scheduling.BookingOperations");
    static final String UNKNOWN = "unknown";

    private final BookingMetrics metrics;
    private final SchedulingRepository repo;

    public BookingOperationsAspect(BookingMetrics metrics, SchedulingRepository repo) {
        this.metrics = metrics;
        this.repo = repo;
    }

    @Around("execution(public * com.northwind.scheduling.service.SchedulingService.book(..))")
    public Object book(ProceedingJoinPoint pjp) throws Throwable {
        return timed("book", pjp);
    }

    @Around("execution(public * com.northwind.scheduling.service.SchedulingService.cancel(..))")
    public Object cancel(ProceedingJoinPoint pjp) throws Throwable {
        return timed("cancel", pjp);
    }

    @Around("execution(public * com.northwind.scheduling.service.SchedulingService.reschedule(..))")
    public Object reschedule(ProceedingJoinPoint pjp) throws Throwable {
        return timed("reschedule", pjp);
    }

    @Around("execution(public * com.northwind.scheduling.service.SchedulingService.changeCapacity(..))")
    public Object capacity(ProceedingJoinPoint pjp) throws Throwable {
        return timed("capacity", pjp);
    }

    private Object timed(String operation, ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        long start = System.nanoTime();
        Object result = null;
        Throwable failure = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            long nanos = System.nanoTime() - start;
            Throwable failed = failure;
            String outcome = failure == null ? BookingMetrics.SUCCESS
                    : failure instanceof SchedulingException ? BookingMetrics.BUSINESS_ERROR : BookingMetrics.ERROR;
            String code = failure == null ? null
                    : failure instanceof SchedulingException se ? se.code().name() : failure.getClass().getSimpleName();
            Ids ids = withCandidate(ids(operation, args, result));
            String centerCode = ids.centerCode != null ? ids.centerCode : centerFor(ids.sessionId, ids.bookingId);
            safely(() -> metrics.record(operation, outcome, centerCode, code, nanos));
            safely(() -> logOperation(operation, outcome, code, centerCode, ids, nanos, failed));
        }
    }

    private record Ids(Integer sessionId, Integer bookingId, Integer newBookingId, String candidateNumber,
                       String centerCode) {
    }

    private static Ids ids(String operation, Object[] args, Object result) {
        return switch (operation) {
            case "book" -> {
                SchedulingService.BookCommand cmd = args.length > 0 && args[0] instanceof SchedulingService.BookCommand c ? c : null;
                SchedulingService.BookingOutcome out = result instanceof SchedulingService.BookingOutcome o ? o : null;
                yield new Ids(cmd == null ? null : cmd.sessionId(), out == null ? null : out.bookingId(), null,
                        cmd == null ? null : cmd.candidateNumber(), null);
            }
            case "cancel" -> {
                Rows.Booking b = result instanceof Rows.Booking r ? r : null;
                yield new Ids(b == null ? null : b.sessionId(), intArg(args, 0), null,
                        b == null ? null : b.candidateNumber(), b == null ? null : b.centerCode());
            }
            case "reschedule" -> {
                SchedulingService.BookingOutcome out = result instanceof SchedulingService.BookingOutcome o ? o : null;
                // success: tagged with the destination session's centre; failure: with the booking being moved
                yield new Ids(out == null ? null : out.sessionId(), intArg(args, 0), out == null ? null : out.bookingId(),
                        null, null);
            }
            default -> {
                SchedulingService.CapacityOutcome out = result instanceof SchedulingService.CapacityOutcome o ? o : null;
                yield new Ids(intArg(args, 0), null, null, null, out == null ? null : out.session().centerCode());
            }
        };
    }

    /** reschedule (and failed cancels) only carry a bookingId; the candidate is known from that booking. */
    private Ids withCandidate(Ids ids) {
        if (ids.candidateNumber != null || ids.bookingId == null) {
            return ids;
        }
        try {
            String candidate = repo.booking(ids.bookingId).map(Rows.Booking::candidateNumber).orElse(null);
            return new Ids(ids.sessionId, ids.bookingId, ids.newBookingId, candidate, ids.centerCode);
        } catch (RuntimeException e) {
            return ids;
        }
    }

    private static Integer intArg(Object[] args, int i) {
        return args.length > i && args[i] instanceof Integer v ? v : null;
    }

    /** Looked up per command (centre codes can change with a snapshot refresh); failed lookups give "unknown". */
    String centerFor(Integer sessionId, Integer bookingId) {
        try {
            Integer sid = sessionId;
            if (sid == null && bookingId != null) {
                sid = repo.booking(bookingId).map(Rows.Booking::sessionId).orElse(null);
            }
            if (sid == null) {
                return UNKNOWN;
            }
            return repo.sessionCenterCode(sid).orElse(UNKNOWN);
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    private void logOperation(String operation, String outcome, String code, String centerCode, Ids ids, long nanos,
                              Throwable failure) {
        boolean inRequest = RequestContextHolder.getRequestAttributes() != null;
        put(RequestLoggingFilter.CENTER_CODE, centerCode);
        put(RequestLoggingFilter.SESSION_ID, ids.sessionId);
        put(RequestLoggingFilter.BOOKING_ID, ids.bookingId);
        put(RequestLoggingFilter.CANDIDATE_NUMBER, ids.candidateNumber);
        try {
            long ms = nanos / 1_000_000;
            if (BookingMetrics.ERROR.equals(outcome)) {
                log.error("booking {} {}", kv("operation", operation), kv("outcome", outcome), kv("code", code),
                        kv("newBookingId", ids.newBookingId), kv("durationMs", ms), failure);
            } else {
                log.info("booking {} {}", kv("operation", operation), kv("outcome", outcome), kv("code", code),
                        kv("newBookingId", ids.newBookingId), kv("durationMs", ms));
            }
        } finally {
            if (!inRequest) {
                RequestLoggingFilter.clearMdc();
            }
        }
    }

    private static void put(String key, Object value) {
        if (value != null) {
            MDC.put(key, String.valueOf(value));
        }
    }

    private static void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            log.debug("booking telemetry failed: {}", e.toString());
        }
    }
}
