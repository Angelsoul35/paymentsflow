package com.angelo.paymentsflow.web.dto;

import com.angelo.paymentsflow.domain.Money;

/**
 * API representation of an amount: the raw minor-unit integer (authoritative),
 * the currency, and a human-readable formatting for display.
 */
public record MoneyView(long amount, String currency, String formatted) {

    public static MoneyView of(Money money) {
        return new MoneyView(money.minorUnits(), money.currency(), money.format());
    }
}
