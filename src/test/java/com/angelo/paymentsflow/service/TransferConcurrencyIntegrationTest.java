package com.angelo.paymentsflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.angelo.paymentsflow.domain.Account;
import com.angelo.paymentsflow.domain.AccountType;
import com.angelo.paymentsflow.repository.AccountRepository;
import com.angelo.paymentsflow.repository.IdempotencyRecordRepository;
import com.angelo.paymentsflow.repository.JournalEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Exercises JPA optimistic locking under contention. Many transfers against the
 * same pair of accounts run concurrently; with the retry loop they all commit,
 * and the key assertion is that <em>no update is lost</em>: the final balances
 * are exactly what a serial run would produce, and the two balances still sum to
 * the funded total.
 */
@SpringBootTest
@TestPropertySource(properties = "paymentsflow.transfer.max-retries=50")
class TransferConcurrencyIntegrationTest {

    private static final int TRANSFERS = 10;
    private static final long AMOUNT = 10_000;
    private static final long FUNDING = 1_000_000;

    @Autowired
    private TransferService transfers;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private JournalEntryRepository journalEntries;
    @Autowired
    private IdempotencyRecordRepository idempotencyRecords;

    @BeforeEach
    void clean() {
        idempotencyRecords.deleteAll();
        journalEntries.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void concurrentTransfersDoNotLoseUpdates() throws Exception {
        Account equity = accounts.save(new Account("owner:equity", AccountType.EQUITY, "USD"));
        Account alice = accounts.save(new Account("alice", AccountType.ASSET, "USD"));
        Account bob = accounts.save(new Account("bob", AccountType.ASSET, "USD"));
        transfers.transfer(new TransferCommand(equity.getId(), alice.getId(), FUNDING, "USD", null));

        ExecutorService pool = Executors.newFixedThreadPool(TRANSFERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < TRANSFERS; i++) {
            tasks.add(() -> {
                start.await();
                return transfers.transfer(
                        new TransferCommand(alice.getId(), bob.getId(), AMOUNT, "USD", null))
                        .journalEntryId();
            });
        }

        List<Future<Long>> futures = new ArrayList<>();
        for (Callable<Long> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown(); // release all threads at once to maximise contention

        int committed = 0;
        for (Future<Long> future : futures) {
            future.get(); // propagates any exception and fails the test
            committed++;
        }
        pool.shutdown();

        assertThat(committed).isEqualTo(TRANSFERS);

        long aliceBalance = accounts.findById(alice.getId()).orElseThrow().getBalanceMinor();
        long bobBalance = accounts.findById(bob.getId()).orElseThrow().getBalanceMinor();

        // No lost updates: exactly TRANSFERS * AMOUNT moved, and the pair still balances.
        assertThat(bobBalance).isEqualTo(TRANSFERS * AMOUNT);
        assertThat(aliceBalance).isEqualTo(FUNDING - TRANSFERS * AMOUNT);
        assertThat(aliceBalance + bobBalance).isEqualTo(FUNDING);
    }
}
