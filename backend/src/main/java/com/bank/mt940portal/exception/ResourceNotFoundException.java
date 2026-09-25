package com.bank.mt940portal.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(Class<?> type, Object id) {
        super(type.getSimpleName() + " not found: " + id);
    }
}
