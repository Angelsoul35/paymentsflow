package com.angelo.paymentsflow.exception;

/** Thrown when a transfer (journal entry) is requested by an id that does not exist (HTTP 404). */
public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(Long transferId) {
        super("transfer not found: " + transferId);
    }
}
