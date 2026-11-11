package com.northwind.routing.route;

/** How a request identifies its test centre; {@code param} is the scheduling-service resolve query parameter. */
public record Lookup(Kind kind, String value) {

    public enum Kind {
        CENTER_CODE("centerCode"), CENTER_ID("centerId"), SESSION_ID("sessionId"), BOOKING_ID("bookingId");

        private final String param;

        Kind(String param) {
            this.param = param;
        }

        public String param() {
            return param;
        }
    }

    public Lookup {
        value = value == null ? null : value.trim();
    }

    public boolean present() {
        return value != null && !value.isEmpty();
    }

    /** Numeric booking id, or null when this is not a (valid) booking lookup. */
    public Long bookingId() {
        if (kind != Kind.BOOKING_ID || !present()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Lookup of(Kind kind, String value) {
        return new Lookup(kind, value);
    }
}
