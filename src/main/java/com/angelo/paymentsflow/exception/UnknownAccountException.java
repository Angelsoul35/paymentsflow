package com.angelo.paymentsflow.exception;

/** Thrown when a transfer references an account id that does not exist. */
public class UnknownAccountException extends UnprocessableTransferException {

    public UnknownAccountException(Long accountId) {
        super("unknown account: " + accountId);
    }
}
