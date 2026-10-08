package com.angelo.paymentsflow.domain;

/**
 * The five classical account types of double-entry bookkeeping, each with a
 * "normal" side on which its balance grows.
 *
 * <ul>
 *   <li><b>Debit-normal</b> ({@link #ASSET}, {@link #EXPENSE}): a debit increases
 *       the balance, a credit decreases it.</li>
 *   <li><b>Credit-normal</b> ({@link #LIABILITY}, {@link #EQUITY},
 *       {@link #INCOME}): a credit increases the balance, a debit decreases it.</li>
 * </ul>
 *
 * This single property is what decides the sign of a posting's effect on a
 * balance; see {@link Direction#signedEffect(AccountType, long)}.
 */
public enum AccountType {
    ASSET(true),
    LIABILITY(false),
    EQUITY(false),
    INCOME(false),
    EXPENSE(true);

    private final boolean debitNormal;

    AccountType(boolean debitNormal) {
        this.debitNormal = debitNormal;
    }

    /** {@code true} if debits increase this type's balance. */
    public boolean isDebitNormal() {
        return debitNormal;
    }
}
