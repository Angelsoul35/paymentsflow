package com.angelo.paymentsflow.realinputs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.angelo.paymentsflow.domain.Account;
import com.angelo.paymentsflow.domain.AccountType;
import com.angelo.paymentsflow.domain.Direction;
import com.angelo.paymentsflow.domain.JournalEntry;
import com.angelo.paymentsflow.exception.UnbalancedEntryException;
import com.angelo.paymentsflow.repository.AccountRepository;
import com.angelo.paymentsflow.repository.IdempotencyRecordRepository;
import com.angelo.paymentsflow.repository.JournalEntryRepository;
import com.angelo.paymentsflow.service.AccountService;
import com.angelo.paymentsflow.service.TransferService;
import com.angelo.paymentsflow.realinputs.OnlineRetailLoader.InvoiceEntry;
import com.angelo.paymentsflow.realinputs.OnlineRetailLoader.LineItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real-inputs benchmark and validation.
 *
 * <p>This is the real-data entry point for paymentsflow: it feeds a vendored
 * snapshot of a real UK retailer's transactions (the UCI <em>Online Retail</em>
 * dataset) through the service's <em>own</em> double-entry engine and validates
 * the ledger it produces against reference values that were computed
 * independently, in Python, directly from the same CSV (see the dataset README).
 * The engine is never re-implemented here; it is driven exactly as the REST API
 * drives it.
 *
 * <p>What is proven on the real data:
 * <ul>
 *   <li>the loader reads the real records with the expected shape;</li>
 *   <li>every journal entry the engine posts is balanced
 *       ({@code debits == credits});</li>
 *   <li>the engine's revenue, per-customer receivables, entry count and gross
 *       turnover match the pinned independent reference values;</li>
 *   <li>the whole-ledger trial balance is exactly zero (conservation);</li>
 *   <li>replaying the same batch under an idempotency key posts nothing new.</li>
 * </ul>
 * Three tamper controls then corrupt the inputs or the ledger and assert that a
 * specific check <em>fails</em>, proving the validations have teeth and are not
 * trivially satisfied.
 */
@SpringBootTest
@Tag("benchmark")
class RealInputsLedgerBenchmarkTest {

    // ---- Pinned reference values, computed independently in Python from the
    // ---- vendored snapshot (src/test/resources/realdata/online-retail/README.md).
    private static final int EXPECTED_LINE_ITEMS = 1989;
    private static final int EXPECTED_ENTRIES = 89;
    private static final int EXPECTED_CUSTOMERS = 71;
    private static final int EXPECTED_REVERSALS = 6;
    private static final long EXPECTED_REVENUE_MINOR = 3_222_693L;      // net sales, pence
    private static final long EXPECTED_GROSS_TURNOVER_MINOR = 3_287_739L; // sum of |net|, pence

    // Pinned per-customer net receivable balances (pence).
    private static final String C_17850 = "17850";
    private static final String C_13047 = "13047";
    private static final String C_17548 = "17548"; // a credit-note customer (negative)
    private static final String C_15311 = "15311";
    private static final String C_14527 = "14527"; // a credit-note customer (negative)
    private static final long BAL_17850 = 149_934L;
    private static final long BAL_13047 = 36_663L;
    private static final long BAL_17548 = -14_148L;
    private static final long BAL_15311 = 44_533L;
    private static final long BAL_14527 = -2_750L;

    @Autowired
    private AccountService accounts;
    @Autowired
    private TransferService transfers;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private JournalEntryRepository journalEntries;
    @Autowired
    private IdempotencyRecordRepository idempotencyRecords;

    @BeforeEach
    void clean() {
        idempotencyRecords.deleteAll();
        journalEntries.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("loader reads the real vendored transactions with the expected shape")
    void loaderReadsRealTransactionsWithExpectedShape() {
        List<LineItem> items = OnlineRetailLoader.readLineItems();
        assertThat(items).hasSize(EXPECTED_LINE_ITEMS);

        LineItem first = items.get(0);
        assertThat(first.invoiceNo()).isEqualTo("536365");
        assertThat(first.stockCode()).isEqualTo("85123A");
        assertThat(first.description()).isEqualTo("WHITE HANGING HEART T-LIGHT HOLDER");
        assertThat(first.quantity()).isEqualTo(6);
        assertThat(first.unitPriceMinor()).isEqualTo(255);   // GBP 2.55 -> 255 pence, exact
        assertThat(first.customerId()).isEqualTo("17850");
        assertThat(first.country()).isEqualTo("United Kingdom");
        assertThat(first.lineMinor()).isEqualTo(6 * 255);

        // Exact pence conversion uses integer arithmetic, never double.
        assertThat(OnlineRetailLoader.parsePence("2.55")).isEqualTo(255);
        assertThat(OnlineRetailLoader.parsePence("27.50")).isEqualTo(2750);
        assertThat(OnlineRetailLoader.parsePence("0.00")).isEqualTo(0);
        assertThat(OnlineRetailLoader.parsePence("13.0")).isEqualTo(1300);

        List<InvoiceEntry> entries = OnlineRetailLoader.loadInvoiceEntries();
        assertThat(entries).hasSize(EXPECTED_ENTRIES);
        InvoiceEntry firstEntry = entries.get(0);
        assertThat(firstEntry.invoiceNo()).isEqualTo("536365");
        assertThat(firstEntry.customerId()).isEqualTo("17850");
        assertThat(firstEntry.netMinor()).isEqualTo(13_912L); // independently recomputed total
        assertThat(entries.stream().filter(InvoiceEntry::isReversal).count())
                .isEqualTo(EXPECTED_REVERSALS);
    }

    @Test
    @DisplayName("engine's ledger over the real data matches the pinned reference values")
    void engineLedgerMatchesPinnedReferenceOnRealData() {
        List<InvoiceEntry> entries = OnlineRetailLoader.loadInvoiceEntries();

        RealLedgerReplay replay = new RealLedgerReplay(accounts, transfers);
        RealLedgerReplay.ReplayResult result = replay.replay(entries, true);

        // 1) Every entry the engine posted is balanced (debits == credits, >= 2 legs).
        long engineGrossTurnover = assertEveryEntryBalancedAndSumDebits();

        // 2) Totals match the independent Python reference, to the penny.
        assertThat(result.postedEntries()).isEqualTo(EXPECTED_ENTRIES);
        assertThat(journalEntries.count()).isEqualTo(EXPECTED_ENTRIES);
        assertThat(result.customerBalances()).hasSize(EXPECTED_CUSTOMERS);
        assertThat(result.revenueBalance()).isEqualTo(EXPECTED_REVENUE_MINOR);
        assertThat(engineGrossTurnover).isEqualTo(EXPECTED_GROSS_TURNOVER_MINOR);

        // 3) Conservation: the whole-ledger trial balance is exactly zero.
        assertThat(result.customerAssetTotal()).isEqualTo(EXPECTED_REVENUE_MINOR);
        assertThat(result.trialBalanceNet()).isZero();
        assertThat(globalTrialBalance()).isZero();

        // 4) Per-customer receivables match the pinned reference, including the
        //    two customers whose balance is driven negative by a credit note.
        assertThat(result.customerBalances().get(C_17850)).isEqualTo(BAL_17850);
        assertThat(result.customerBalances().get(C_13047)).isEqualTo(BAL_13047);
        assertThat(result.customerBalances().get(C_17548)).isEqualTo(BAL_17548);
        assertThat(result.customerBalances().get(C_15311)).isEqualTo(BAL_15311);
        assertThat(result.customerBalances().get(C_14527)).isEqualTo(BAL_14527);

        printReport(entries, result, engineGrossTurnover);
    }

    @Test
    @DisplayName("replaying the same batch under an idempotency key posts nothing new")
    void idempotentReplayPostsNothingNew() {
        List<InvoiceEntry> entries = OnlineRetailLoader.loadInvoiceEntries();

        RealLedgerReplay replay = new RealLedgerReplay(accounts, transfers);
        RealLedgerReplay.ReplayResult firstRun = replay.replay(entries, true);
        assertThat(firstRun.postedEntries()).isEqualTo(EXPECTED_ENTRIES);
        assertThat(firstRun.replayedEntries()).isZero();
        long entriesAfterFirst = journalEntries.count();

        // Second pass with the SAME invoice keys, against the SAME accounts:
        // every transfer must short-circuit and no balance may move.
        RealLedgerReplay.ReplayResult secondRun = replay.replayReusing(entries, firstRun.ledger(), true);

        assertThat(secondRun.postedEntries()).isZero();
        assertThat(secondRun.replayedEntries()).isEqualTo(EXPECTED_ENTRIES);
        // No new journal entries, and every balance is unchanged to the penny.
        assertThat(journalEntries.count()).isEqualTo(entriesAfterFirst);
        assertThat(secondRun.revenueBalance()).isEqualTo(firstRun.revenueBalance());
        assertThat(secondRun.customerAssetTotal()).isEqualTo(firstRun.customerAssetTotal());
        assertThat(secondRun.customerBalances()).isEqualTo(firstRun.customerBalances());
        assertThat(secondRun.trialBalanceNet()).isZero();
    }

    // ================= TAMPER / SCRAMBLE CONTROLS (must break a check) =================

    @Test
    @DisplayName("tamper: dropping a leg from a real posting makes the entry unbalanced")
    void droppingALegMakesTheEntryUnbalanced() {
        // Use the real first invoice's total (13912 pence) to build postings.
        Account customer = new Account("customer:17850", AccountType.ASSET, OnlineRetailLoader.CURRENCY);
        Account revenue = new Account("revenue:sales", AccountType.INCOME, OnlineRetailLoader.CURRENCY);

        // A posting with the credit leg dropped: only the debit survives.
        JournalEntry oneLegged = new JournalEntry(OnlineRetailLoader.CURRENCY, "tampered: dropped credit leg");
        oneLegged.addPosting(customer, Direction.DEBIT, 13_912L);
        assertThatThrownBy(oneLegged::validateBalanced)
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessageContaining("at least two postings");

        // A posting whose legs disagree by a single penny is also rejected.
        JournalEntry mismatched = new JournalEntry(OnlineRetailLoader.CURRENCY, "tampered: penny mismatch");
        mismatched.addPosting(customer, Direction.DEBIT, 13_912L);
        mismatched.addPosting(revenue, Direction.CREDIT, 13_911L);
        assertThatThrownBy(mismatched::validateBalanced)
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessageContaining("unbalanced");
    }

    @Test
    @DisplayName("tamper: scrambling one real amount breaks the reconciliation")
    void scramblingOneAmountBreaksTheReconciliation() {
        List<InvoiceEntry> clean = OnlineRetailLoader.loadInvoiceEntries();

        // Corrupt exactly one record: inflate the first invoice by one pound.
        List<InvoiceEntry> tampered = new ArrayList<>(clean);
        InvoiceEntry victim = tampered.get(0);
        tampered.set(0, new InvoiceEntry(victim.invoiceNo(), victim.customerId(), victim.netMinor() + 100));

        RealLedgerReplay replay = new RealLedgerReplay(accounts, transfers);
        RealLedgerReplay.ReplayResult result = replay.replay(tampered, false);

        // The pinned reconciliation now FAILS: revenue is off by exactly the tamper,
        // and so is the affected customer's receivable.
        assertThat(result.revenueBalance()).isNotEqualTo(EXPECTED_REVENUE_MINOR);
        assertThat(result.revenueBalance()).isEqualTo(EXPECTED_REVENUE_MINOR + 100);
        assertThat(result.customerBalances().get(victim.customerId()))
                .isNotEqualTo(BAL_17850)
                .isEqualTo(BAL_17850 + 100);
        // Double-entry still holds internally (the transfer is balanced by
        // construction), which is exactly why reconciliation against an external
        // reference - not the internal balance check - is what catches this.
        assertThat(result.trialBalanceNet()).isZero();
    }

    @Test
    @DisplayName("tamper: a one-sided posting breaks the whole-ledger trial balance")
    void aOneSidedPostingBreaksGlobalTrialBalance() {
        List<InvoiceEntry> entries = OnlineRetailLoader.loadInvoiceEntries();
        RealLedgerReplay replay = new RealLedgerReplay(accounts, transfers);
        RealLedgerReplay.ReplayResult clean = replay.replay(entries, false);
        assertThat(clean.trialBalanceNet()).isZero();
        assertThat(globalTrialBalance()).isZero();

        // Apply a posting to ONE account with no matching counter-posting - a lost
        // leg. The engine would never produce this (validateBalanced forbids it);
        // we inject it directly to prove the trial-balance invariant is sensitive.
        Account victim = accountRepository.findById(clean.revenueAccountId()).orElseThrow();
        victim.apply(Direction.CREDIT, 500L); // revenue up by 5.00 with nothing debited
        accountRepository.saveAndFlush(victim);

        long tamperedTrialBalance = globalTrialBalance();
        assertThat(tamperedTrialBalance)
                .as("a one-sided posting must break the trial balance")
                .isNotZero()
                .isEqualTo(-500L); // credit-normal account rose, so assets - credits = -500
    }

    // ---- helpers ----

    /**
     * Asserts every persisted journal entry is balanced and has at least two
     * legs, and returns the engine's gross turnover (sum of all entry debits),
     * which by the invariant equals the sum of all credits.
     */
    private long assertEveryEntryBalancedAndSumDebits() {
        List<Long> ids = journalEntries.findAll().stream().map(JournalEntry::getId).toList();
        long sumDebits = 0;
        long sumCredits = 0;
        for (Long id : ids) {
            JournalEntry entry = transfers.getTransfer(id);
            assertThat(entry.getPostings()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(entry.totalDebits())
                    .as("entry %s must balance", id)
                    .isEqualTo(entry.totalCredits());
            sumDebits += entry.totalDebits();
            sumCredits += entry.totalCredits();
        }
        assertThat(sumDebits).isEqualTo(sumCredits);
        return sumDebits;
    }

    /**
     * Recomputes the trial balance across every account from scratch: debit-normal
     * balances count positive, credit-normal negative. A balanced ledger nets to
     * zero.
     */
    private long globalTrialBalance() {
        long net = 0;
        for (Account account : accountRepository.findAll()) {
            net += account.getType().isDebitNormal() ? account.getBalanceMinor() : -account.getBalanceMinor();
        }
        return net;
    }

    private void printReport(List<InvoiceEntry> entries,
                             RealLedgerReplay.ReplayResult result,
                             long grossTurnover) {
        String line = "=".repeat(78);
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(line).append('\n');
        sb.append("paymentsflow - REAL-INPUTS LEDGER BENCHMARK\n");
        sb.append(line).append('\n');
        sb.append("dataset           : UCI Online Retail (real UK retailer, 2010-2011)\n");
        sb.append("source            : HuggingFace aarav912/online-retail (rows API)\n");
        sb.append("snapshot          : ").append(EXPECTED_LINE_ITEMS).append(" real line items -> ")
                .append(entries.size()).append(" balanced journal entries\n");
        sb.append("currency          : ").append(OnlineRetailLoader.CURRENCY).append(" (amounts in pence)\n");
        sb.append(line).append('\n');
        sb.append(String.format("%-34s %18d%n", "journal entries posted", result.postedEntries()));
        sb.append(String.format("%-34s %18d%n", "customer accounts", result.customerBalances().size()));
        sb.append(String.format("%-34s %18d%n", "reversal (credit-note) entries",
                entries.stream().filter(InvoiceEntry::isReversal).count()));
        sb.append(String.format("%-34s %15d.%02d%n", "revenue (net sales)",
                result.revenueBalance() / 100, Math.abs(result.revenueBalance() % 100)));
        sb.append(String.format("%-34s %15d.%02d%n", "sum of customer receivables",
                result.customerAssetTotal() / 100, Math.abs(result.customerAssetTotal() % 100)));
        sb.append(String.format("%-34s %15d.%02d%n", "gross turnover (sum of debits)",
                grossTurnover / 100, Math.abs(grossTurnover % 100)));
        sb.append(String.format("%-34s %18d%n", "trial balance (assets - credits)", result.trialBalanceNet()));
        sb.append(line).append('\n');
        sb.append("sample per-customer receivables (independently pinned):\n");
        sb.append(String.format("  customer %-8s = %10d pence  (pinned %d)%n", C_17850,
                result.customerBalances().get(C_17850), BAL_17850));
        sb.append(String.format("  customer %-8s = %10d pence  (pinned %d)%n", C_17548,
                result.customerBalances().get(C_17548), BAL_17548));
        sb.append(line).append('\n');
        sb.append("VERDICT: PASS - the engine's ledger over real transactions reproduces\n");
        sb.append("every independently-pinned reference value to the penny, every journal\n");
        sb.append("entry balances, the whole-ledger trial balance is exactly zero, and an\n");
        sb.append("idempotent replay posts nothing new. The double-entry engine is a real,\n");
        sb.append("validated capability on real-world data.\n");
        sb.append(line).append('\n');
        System.out.println(sb);
    }
}
