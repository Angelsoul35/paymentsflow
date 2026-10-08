package com.angelo.paymentsflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * One leg of a {@link JournalEntry}: a debit or credit of a positive amount
 * against a single {@link Account}. Postings are immutable once created.
 */
@Entity
@Table(name = "posting")
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    protected Posting() {
        // for JPA
    }

    Posting(JournalEntry journalEntry, Account account, Direction direction, long amountMinor) {
        this.journalEntry = Objects.requireNonNull(journalEntry, "journalEntry");
        this.account = Objects.requireNonNull(account, "account");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.amountMinor = amountMinor;
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public boolean isDebit() {
        return direction == Direction.DEBIT;
    }

    public boolean isCredit() {
        return direction == Direction.CREDIT;
    }
}
