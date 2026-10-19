package com.samplemvcwebapp.servicelayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mirrors GenericServices ISuccessOrErrors: either a success message or a list of errors. */
public final class SuccessOrErrors {

    private final String successMessage;
    private final List<ValidationError> errors;

    private SuccessOrErrors(String successMessage, List<ValidationError> errors) {
        this.successMessage = successMessage;
        this.errors = Collections.unmodifiableList(errors);
    }

    public static SuccessOrErrors success(String message) {
        return new SuccessOrErrors(message, List.of());
    }

    public static SuccessOrErrors errors(List<ValidationError> errors) {
        if (errors.isEmpty()) throw new IllegalArgumentException("errors must not be empty");
        return new SuccessOrErrors(null, new ArrayList<>(errors));
    }

    public static SuccessOrErrors error(String propertyName, String message) {
        return errors(List.of(new ValidationError(propertyName, message)));
    }

    public boolean isValid() { return errors.isEmpty(); }
    public String getSuccessMessage() { return successMessage; }
    public List<ValidationError> getErrors() { return errors; }
}
