package com.northwind.assessment.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;

final class Ids {

    private Ids() {
    }

    /** null when not a long (non-numeric or out of range) */
    static Long parseLong(String s) {
        try {
            return s == null ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** sessionId as booking-api's Jackson binding coerces it: any number (truncated) or an integer string; else null */
    static Long sessionId(JsonNode req) {
        JsonNode n = req == null ? null : req.get("sessionId");
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.canConvertToLong() ? n.longValue() : null; // 100.0 binds as 100 in booking-api (float-as-int)
        }
        return n.isTextual() ? parseLong(n.asText()) : null;
    }
}
