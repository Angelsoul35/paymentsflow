package com.angelo.paymentsflow.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /transfers}. The amount is in minor units
 * (cents), so {@code 150000} means 1,500.00 of the given currency.
 */
public record CreateTransferRequest(
        @NotNull(message = "fromAccountId is required")
        Long fromAccountId,

        @NotNull(message = "toAccountId is required")
        Long toAccountId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive (minor units)")
        Long amount,

        @NotNull(message = "currency is required")
        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO-4217 code")
        String currency) {
}
