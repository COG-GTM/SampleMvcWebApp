package com.samplemvcwebapp.servicelayer;

/** One error. propertyName is the form field (e.g. "Title", "Slug", "UserChosenTags") or null for a model-level error. */
public record ValidationError(String propertyName, String message) {
}
