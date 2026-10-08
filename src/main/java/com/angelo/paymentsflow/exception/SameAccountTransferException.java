package com.angelo.paymentsflow.exception;

/** Thrown when a transfer's source and destination accounts are the same. */
public class SameAccountTransferException extends UnprocessableTransferException {

    public SameAccountTransferException(Long accountId) {
        super("source and destination accounts must differ (both were " + accountId + ")");
    }
}
