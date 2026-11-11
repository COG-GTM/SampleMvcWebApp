package com.northwind.assessment.gateway.resolve;

/** One lookup parameter of the resolve endpoint: sessionId | bookingId | centerId | centerCode. */
public record ResolveKey(String param, String value) {

    public static ResolveKey session(Object id) { return new ResolveKey("sessionId", String.valueOf(id)); }
    public static ResolveKey booking(Object id) { return new ResolveKey("bookingId", String.valueOf(id)); }
    public static ResolveKey centerId(Object id) { return new ResolveKey("centerId", String.valueOf(id)); }
    public static ResolveKey centerCode(String code) { return new ResolveKey("centerCode", code.trim().toUpperCase()); }
}
