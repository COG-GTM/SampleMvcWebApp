package com.northwind.scheduling.characterization.spi;

/**
 * Error result of a scheduling operation.
 *
 * @param condition      named condition
 * @param code           documented legacy code (50001-50006, or 50000 for system errors)
 * @param message        error text as returned to the caller
 * @param nativeNumber   target-native error number (legacy: SQL message number, always 50000 for proc errors), may be null
 * @param nativeState    target-native error state (legacy: RAISERROR state), may be null
 */
public record SchedulingError(ErrorCondition condition, int code, String message, Integer nativeNumber, Integer nativeState) {
}
