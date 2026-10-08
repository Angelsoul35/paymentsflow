package com.angelo.paymentsflow.web;

import com.angelo.paymentsflow.exception.AccountNotFoundException;
import com.angelo.paymentsflow.exception.ConcurrencyConflictException;
import com.angelo.paymentsflow.exception.TransferNotFoundException;
import com.angelo.paymentsflow.exception.UnprocessableTransferException;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC-7807 {@link ProblemDetail} responses so that
 * every error - whether a bean-validation failure, a rejected transfer, or a
 * concurrency conflict - has the same machine-readable shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String BASE = "https://paymentsflow/errors/";

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Account not found", ex.getMessage(), "account-not-found");
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ProblemDetail handleTransferNotFound(TransferNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Transfer not found", ex.getMessage(), "transfer-not-found");
    }

    @ExceptionHandler(UnprocessableTransferException.class)
    public ProblemDetail handleUnprocessable(UnprocessableTransferException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Transfer rejected", ex.getMessage(),
                "transfer-rejected");
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ProblemDetail handleConflict(ConcurrencyConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrency conflict", ex.getMessage(),
                "concurrency-conflict");
    }

    /** Enriches the default 400 for bean-validation failures with field details. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "validation-failed");

        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        body.setProperty("errors", errors);

        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(BASE + code));
        problem.setProperty("code", code);
        return problem;
    }

    private record FieldViolation(String field, String message) {
    }
}
