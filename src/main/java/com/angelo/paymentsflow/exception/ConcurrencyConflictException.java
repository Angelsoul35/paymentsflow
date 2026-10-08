package com.angelo.paymentsflow.exception;

/**
 * Thrown when a transfer cannot be committed because concurrent writes to the
 * same account kept colliding on the optimistic lock after the configured
 * number of retries. Mapped to HTTP 409 so the client can retry.
 */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
