package com.angelo.paymentsflow.domain;

import com.angelo.paymentsflow.exception.UnbalancedEntryException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An atomic, balanced set of postings. The central invariant of double-entry
 * bookkeeping lives here: an entry is valid only if it has at least two
 * postings, every amount is positive, and the sum of debits equals the sum of
 * credits. {@link #validateBalanced()} enforces it before the entry is allowed
 * to affect any balance.
 */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Posting> postings = new ArrayList<>();

    protected JournalEntry() {
        // for JPA
    }

    public JournalEntry(String currency, String description) {
        this.currency = Objects.requireNonNull(currency, "currency").toUpperCase();
        this.description = Objects.requireNonNull(description, "description");
        this.createdAt = Instant.now();
    }

    /** Adds a posting leg to this entry. */
    public Posting addPosting(Account account, Direction direction, long amountMinor) {
        Posting posting = new Posting(this, account, direction, amountMinor);
        postings.add(posting);
        return posting;
    }

    public long totalDebits() {
        return postings.stream().filter(Posting::isDebit).mapToLong(Posting::getAmountMinor).sum();
    }

    public long totalCredits() {
        return postings.stream().filter(Posting::isCredit).mapToLong(Posting::getAmountMinor).sum();
    }

    /**
     * Enforces the double-entry invariant. Called before any balance is touched,
     * so an invalid entry can never reach the accounts.
     *
     * @throws UnbalancedEntryException if the entry has fewer than two postings,
     *                                  a non-positive amount, or debits that do
     *                                  not equal credits
     */
    public void validateBalanced() {
        if (postings.size() < 2) {
            throw new UnbalancedEntryException("a journal entry must have at least two postings");
        }
        if (postings.stream().anyMatch(p -> p.getAmountMinor() <= 0)) {
            throw new UnbalancedEntryException("every posting amount must be positive");
        }
        long debits = totalDebits();
        long credits = totalCredits();
        if (debits != credits) {
            throw new UnbalancedEntryException(
                    "entry is unbalanced: debits=" + debits + " credits=" + credits);
        }
    }

    public Long getId() {
        return id;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Posting> getPostings() {
        return List.copyOf(postings);
    }
}
