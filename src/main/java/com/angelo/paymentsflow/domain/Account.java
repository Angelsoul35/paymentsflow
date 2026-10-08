package com.angelo.paymentsflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * A ledger account. Its {@code balanceMinor} is a running total in minor units,
 * maintained exclusively by applying postings through {@link #apply}.
 *
 * <p>The {@link Version} field enables JPA optimistic locking: two transactions
 * that read the same account and both try to write it will collide on commit,
 * and the loser is retried or rejected rather than silently overwriting the
 * winner (a "lost update").
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public Account(String name, AccountType type, String currency) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.currency = Objects.requireNonNull(currency, "currency").toUpperCase();
        this.balanceMinor = 0L;
        this.createdAt = Instant.now();
    }

    /**
     * Applies a posting to this account's balance. The direction's effect
     * depends on the account type (see {@link Direction#signedEffect}).
     *
     * @param direction the posting side
     * @param amount    a positive amount in minor units
     */
    public void apply(Direction direction, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("posting amount must be positive");
        }
        this.balanceMinor += direction.signedEffect(this.type, amount);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public Money getBalance() {
        return new Money(balanceMinor, currency);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
