package com.angelo.paymentsflow.exception;

/**
 * Base type for transfer requests that are syntactically valid but cannot be
 * processed as posted to the ledger (unknown account, currency mismatch,
 * same-account transfer, or an unbalanced entry). Mapped to HTTP 422.
 */
public abstract class UnprocessableTransferException extends RuntimeException {

    protected UnprocessableTransferException(String message) {
        super(message);
    }
}
