package com.angelo.paymentsflow.domain;

/**
 * The side of a posting. Every posting is either a debit or a credit; a journal
 * entry is balanced when the sum of its debits equals the sum of its credits.
 */
public enum Direction {
    DEBIT,
    CREDIT;

    /**
     * The signed effect this posting direction has on the balance of an account
     * of the given type. A posting increases a balance when its direction
     * matches the account's normal side, and decreases it otherwise.
     *
     * @param type   the account type the posting is applied to
     * @param amount a positive amount in minor units
     * @return {@code +amount} if the balance increases, {@code -amount} otherwise
     */
    public long signedEffect(AccountType type, long amount) {
        boolean increases = (this == DEBIT) == type.isDebitNormal();
        return increases ? amount : -amount;
    }
}
