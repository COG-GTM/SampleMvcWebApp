package com.northwind.assessment.gateway.resolve;

public class ResolveUnavailableException extends RuntimeException {

    public ResolveUnavailableException(ResolveKey key, Throwable cause) {
        super("center resolution unavailable for " + key.param() + "=" + key.value() + ": " + cause.getMessage(), cause);
    }
}
