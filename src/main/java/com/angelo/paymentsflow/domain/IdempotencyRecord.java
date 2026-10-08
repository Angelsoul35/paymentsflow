package com.angelo.paymentsflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Maps a client-supplied idempotency key to the journal entry it produced.
 * The key is the primary key, so a duplicate POST with the same key cannot
 * insert a second row: the unique constraint turns a concurrent replay into a
 * failed insert, which the service resolves by returning the original result
 * instead of posting twice.
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String key;

    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // for JPA
    }

    public IdempotencyRecord(String key, Long journalEntryId) {
        this.key = Objects.requireNonNull(key, "key");
        this.journalEntryId = Objects.requireNonNull(journalEntryId, "journalEntryId");
        this.createdAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public Long getJournalEntryId() {
        return journalEntryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
