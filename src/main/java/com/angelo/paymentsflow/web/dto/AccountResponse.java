package com.angelo.paymentsflow.web.dto;

import com.angelo.paymentsflow.domain.Account;
import com.angelo.paymentsflow.domain.AccountType;
import java.time.Instant;

/** Response body for account endpoints. */
public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        String currency,
        MoneyView balance,
        long version,
        Instant createdAt) {

    public static AccountResponse of(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrency(),
                MoneyView.of(account.getBalance()),
                account.getVersion(),
                account.getCreatedAt());
    }
}
