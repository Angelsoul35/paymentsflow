package com.angelo.paymentsflow.web.dto;

import com.angelo.paymentsflow.domain.Money;

/** Response body for {@code GET /accounts/{id}/balance}. */
public record BalanceResponse(Long accountId, MoneyView balance) {

    public static BalanceResponse of(Long accountId, Money balance) {
        return new BalanceResponse(accountId, MoneyView.of(balance));
    }
}
