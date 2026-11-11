package com.northwind.assessment.scheduling.web;

/** {@code {"error":"<CODE>","message":"...","legacyCode":50001|null}} */
public record ApiError(String error, String message, Integer legacyCode) {
}
