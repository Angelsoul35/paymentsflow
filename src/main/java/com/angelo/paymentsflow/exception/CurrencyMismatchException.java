package com.angelo.paymentsflow.exception;

/** Thrown when a transfer's currency does not match both accounts' currencies. */
public class CurrencyMismatchException extends UnprocessableTransferException {

    public CurrencyMismatchException(String message) {
        super(message);
    }
}
