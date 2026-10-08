package com.angelo.paymentsflow.web.dto;

import com.angelo.paymentsflow.domain.JournalEntry;
import com.angelo.paymentsflow.domain.Posting;
import java.time.Instant;
import java.util.List;

/**
 * Response body for transfer endpoints. A transfer is the journal entry it
 * produced; {@code fromAccountId}/{@code toAccountId} are the credited and
 * debited accounts, and {@code postings} shows the balanced legs in full.
 */
public record TransferResponse(
        Long id,
        String status,
        String currency,
        long amount,
        Long fromAccountId,
        Long toAccountId,
        List<PostingView> postings,
        Instant createdAt) {

    public static TransferResponse of(JournalEntry entry) {
        Long fromAccountId = entry.getPostings().stream()
                .filter(Posting::isCredit)
                .map(p -> p.getAccount().getId())
                .findFirst()
                .orElse(null);
        Long toAccountId = entry.getPostings().stream()
                .filter(Posting::isDebit)
                .map(p -> p.getAccount().getId())
                .findFirst()
                .orElse(null);
        return new TransferResponse(
                entry.getId(),
                "POSTED",
                entry.getCurrency(),
                entry.totalDebits(),
                fromAccountId,
                toAccountId,
                entry.getPostings().stream().map(PostingView::of).toList(),
                entry.getCreatedAt());
    }
}
