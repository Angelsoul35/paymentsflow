package com.angelo.paymentsflow.service;

import com.angelo.paymentsflow.domain.Account;
import com.angelo.paymentsflow.domain.Direction;
import com.angelo.paymentsflow.domain.IdempotencyRecord;
import com.angelo.paymentsflow.domain.JournalEntry;
import com.angelo.paymentsflow.exception.CurrencyMismatchException;
import com.angelo.paymentsflow.exception.UnknownAccountException;
import com.angelo.paymentsflow.repository.AccountRepository;
import com.angelo.paymentsflow.repository.IdempotencyRecordRepository;
import com.angelo.paymentsflow.repository.JournalEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional unit of work for a single transfer attempt. Kept separate
 * from {@link TransferService} so that the retry loop can start a fresh
 * transaction (and a fresh persistence context) on each attempt simply by
 * calling this bean again through the Spring proxy.
 */
@Component
public class TransferExecutor {

    private final AccountRepository accounts;
    private final JournalEntryRepository journalEntries;
    private final IdempotencyRecordRepository idempotencyRecords;

    public TransferExecutor(AccountRepository accounts,
                            JournalEntryRepository journalEntries,
                            IdempotencyRecordRepository idempotencyRecords) {
        this.accounts = accounts;
        this.journalEntries = journalEntries;
        this.idempotencyRecords = idempotencyRecords;
    }

    /**
     * Posts one balanced transfer in a single transaction: the source account is
     * credited and the destination debited by the same amount, so the entry is
     * balanced by construction. The balance invariant is still asserted before
     * any balance is touched (defence in depth).
     *
     * <p>Writing the accounts with {@code saveAndFlush} forces the optimistic
     * lock check to happen here, where a concurrent conflict surfaces as a
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * for the caller's retry loop. When an idempotency key is present, its
     * record is written in the same transaction, so a concurrent replay either
     * sees the committed result or fails its own insert on the unique key.
     *
     * @return the id of the posted journal entry
     */
    @Transactional
    public Long execute(TransferCommand command) {
        Account from = accounts.findById(command.fromAccountId())
                .orElseThrow(() -> new UnknownAccountException(command.fromAccountId()));
        Account to = accounts.findById(command.toAccountId())
                .orElseThrow(() -> new UnknownAccountException(command.toAccountId()));

        requireMatchingCurrency(from, to, command.currency());

        JournalEntry entry = new JournalEntry(
                command.currency(),
                "transfer %d -> %d".formatted(from.getId(), to.getId()));
        entry.addPosting(to, Direction.DEBIT, command.amountMinor());
        entry.addPosting(from, Direction.CREDIT, command.amountMinor());
        entry.validateBalanced();

        from.apply(Direction.CREDIT, command.amountMinor());
        to.apply(Direction.DEBIT, command.amountMinor());

        JournalEntry saved = journalEntries.save(entry);
        accounts.saveAndFlush(from);
        accounts.saveAndFlush(to);

        if (command.idempotencyKey() != null) {
            idempotencyRecords.saveAndFlush(new IdempotencyRecord(command.idempotencyKey(), saved.getId()));
        }
        return saved.getId();
    }

    private void requireMatchingCurrency(Account from, Account to, String currency) {
        if (!from.getCurrency().equals(currency) || !to.getCurrency().equals(currency)) {
            throw new CurrencyMismatchException(
                    "transfer currency %s must match both accounts (from=%s, to=%s)"
                            .formatted(currency, from.getCurrency(), to.getCurrency()));
        }
    }
}
