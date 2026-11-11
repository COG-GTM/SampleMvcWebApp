package com.northwind.scheduling.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Error body (contract section 4). {@code sqlError} is set only for SYSTEM_ERROR caused by SQL Server (e.g. 1205). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, Integer legacyState, Integer sqlError) {
}
