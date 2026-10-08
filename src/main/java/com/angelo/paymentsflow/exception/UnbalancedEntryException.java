package com.angelo.paymentsflow.exception;

/**
 * Thrown when a journal entry violates the double-entry invariant (fewer than
 * two postings, a non-positive amount, or debits that do not equal credits).
 */
public class UnbalancedEntryException extends UnprocessableTransferException {

    public UnbalancedEntryException(String message) {
        super(message);
    }
}
