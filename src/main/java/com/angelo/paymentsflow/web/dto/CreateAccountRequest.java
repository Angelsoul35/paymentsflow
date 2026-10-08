package com.angelo.paymentsflow.web.dto;

import com.angelo.paymentsflow.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Request body for {@code POST /accounts}. */
public record CreateAccountRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @NotNull(message = "type is required (ASSET, LIABILITY, EQUITY, INCOME, EXPENSE)")
        AccountType type,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO-4217 code")
        String currency) {
}
