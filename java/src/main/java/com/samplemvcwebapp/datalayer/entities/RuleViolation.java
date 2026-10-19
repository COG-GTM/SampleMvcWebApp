package com.samplemvcwebapp.datalayer.entities;

/** One business-rule failure from an entity's validate() (legacy IValidatableObject ValidationResult). propertyName null = model-level. */
public record RuleViolation(String propertyName, String message) {
}
