package com.angelo.paymentsflow.exception;

/** Thrown when an account is requested directly by an id that does not exist (HTTP 404). */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long accountId) {
        super("account not found: " + accountId);
    }
}
