package com.northwind.assessment.gateway.resolve;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Body of scheduling-service {@code GET /internal/routing/resolve} (integration contract item 3). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResolvedCenter(String centerCode, Long centerId, Long sessionId, Long bookingId, String bookingOrigin) {
}
