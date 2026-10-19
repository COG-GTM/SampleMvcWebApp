package com.samplemvcwebapp.web.infrastructure;

/** Raised when a Details/Edit id does not exist (legacy: null Result, caught by HandleErrorAttribute). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String entityName, int id) {
        super(entityName + " with id " + id + " was not found");
    }
}
