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

    /** sessionId as the services' JSON binding accepts it: a number or a numeric string; null otherwise */
    static Long sessionId(JsonNode req) {
        JsonNode n = req == null ? null : req.get("sessionId");
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isIntegralNumber()) {
            return n.canConvertToLong() ? n.asLong() : null;
        }
        return n.isTextual() ? parseLong(n.asText()) : null;
    }
}
