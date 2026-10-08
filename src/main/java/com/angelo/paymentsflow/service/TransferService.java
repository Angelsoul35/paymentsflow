package com.angelo.paymentsflow.service;

import com.angelo.paymentsflow.config.TransferProperties;
import com.angelo.paymentsflow.domain.IdempotencyRecord;
import com.angelo.paymentsflow.domain.JournalEntry;
import com.angelo.paymentsflow.exception.ConcurrencyConflictException;
import com.angelo.paymentsflow.exception.SameAccountTransferException;
import com.angelo.paymentsflow.exception.TransferNotFoundException;
import com.angelo.paymentsflow.repository.IdempotencyRecordRepository;
import com.angelo.paymentsflow.repository.JournalEntryRepository;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a transfer: idempotency short-circuit, then the posting attempt
 * wrapped in a retry loop that re-runs on an optimistic-lock conflict and, after
 * a bounded number of attempts, surfaces a {@link ConcurrencyConflictException}
 * (HTTP 409).
 *
 * <p>This bean is deliberately <em>not</em> {@code @Transactional}: each attempt
 * must run in its own transaction, which it gets by calling the separate
 * {@link TransferExecutor} bean through the Spring proxy.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferExecutor executor;
    private final JournalEntryRepository journalEntries;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final int maxRetries;

    public TransferService(TransferExecutor executor,
                           JournalEntryRepository journalEntries,
                           IdempotencyRecordRepository idempotencyRecords,
                           TransferProperties properties) {
        this.executor = executor;
        this.journalEntries = journalEntries;
        this.idempotencyRecords = idempotencyRecords;
        this.maxRetries = properties.maxRetries();
    }

    /**
     * Handles a transfer request, honouring the idempotency key and retrying on
     * optimistic-lock conflicts.
     */
    public TransferOutcome transfer(TransferCommand command) {
        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new SameAccountTransferException(command.fromAccountId());
        }

        Optional<TransferOutcome> replay = findReplay(command.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get();
        }

        int attempt = 0;
        while (true) {
            try {
                return new TransferOutcome(executor.execute(command), false);
            } catch (ObjectOptimisticLockingFailureException conflict) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw new ConcurrencyConflictException(
                            "transfer could not be committed after " + maxRetries
                                    + " attempts due to concurrent updates");
                }
                log.debug("optimistic-lock conflict on transfer attempt {}, retrying", attempt);
                backoff(attempt);
            } catch (DataIntegrityViolationException e) {
                // A concurrent request with the same idempotency key won the race
                // to insert the key; its entry is the canonical result.
                Optional<TransferOutcome> raced = findReplay(command.idempotencyKey());
                if (raced.isPresent()) {
                    return raced.get();
                }
                throw e;
            }
        }
    }

    /** Loads a posted transfer (journal entry) with its postings, for the API. */
    @Transactional(readOnly = true)
    public JournalEntry getTransfer(Long id) {
        return journalEntries.findByIdWithPostings(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    private Optional<TransferOutcome> findReplay(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return idempotencyRecords.findById(idempotencyKey)
                .map(IdempotencyRecord::getJournalEntryId)
                .map(entryId -> new TransferOutcome(entryId, true));
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, 10L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrencyConflictException("transfer interrupted while retrying");
        }
    }
}
