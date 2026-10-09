package com.angelo.paymentsflow.realinputs;

import com.angelo.paymentsflow.domain.AccountType;
import com.angelo.paymentsflow.service.AccountService;
import com.angelo.paymentsflow.service.TransferCommand;
import com.angelo.paymentsflow.service.TransferOutcome;
import com.angelo.paymentsflow.service.TransferService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays the real Online Retail invoices through paymentsflow's <em>own</em>
 * engine end to end: it creates accounts with {@link AccountService} and posts
 * every invoice with {@link TransferService}, the same beans the REST API uses.
 * Nothing here re-implements the ledger; it only drives it and reads back the
 * balances the engine produced.
 *
 * <h2>Mapping to double-entry</h2>
 * The retailer's sales ledger is modelled with one credit-normal
 * {@link AccountType#INCOME INCOME} account ({@code revenue:sales}) and one
 * debit-normal {@link AccountType#ASSET ASSET} receivable account per customer.
 * <ul>
 *   <li><b>Sale</b> ({@code netMinor > 0}): {@code transfer(revenue -> customer)}.
 *       The engine credits {@code revenue} (income rises) and debits the customer
 *       receivable (it rises) by the same amount &mdash; the textbook
 *       sale-on-account entry, balanced by construction.</li>
 *   <li><b>Return / credit note</b> ({@code netMinor < 0}):
 *       {@code transfer(customer -> revenue)} for the absolute amount, reversing
 *       both sides.</li>
 * </ul>
 * The invoice number is used as the {@code Idempotency-Key}, so replaying the
 * same batch against the same accounts is a no-op &mdash; the property a
 * payments pipeline relies on.
 */
public final class RealLedgerReplay {

    private final AccountService accounts;
    private final TransferService transfers;

    public RealLedgerReplay(AccountService accounts, TransferService transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    /**
     * Creates the ledger accounts for a batch: one revenue account plus one
     * receivable account per distinct customer, in first-seen order.
     */
    public LedgerAccounts createAccounts(List<OnlineRetailLoader.InvoiceEntry> entries) {
        Long revenueId = accounts
                .create("revenue:sales", AccountType.INCOME, OnlineRetailLoader.CURRENCY).getId();
        Map<String, Long> customerAccountIds = new LinkedHashMap<>();
        for (OnlineRetailLoader.InvoiceEntry entry : entries) {
            customerAccountIds.computeIfAbsent(entry.customerId(), cid ->
                    accounts.create("customer:" + cid, AccountType.ASSET, OnlineRetailLoader.CURRENCY).getId());
        }
        return new LedgerAccounts(revenueId, customerAccountIds);
    }

    /** Creates fresh accounts and posts the whole batch through the engine. */
    public ReplayResult replay(List<OnlineRetailLoader.InvoiceEntry> entries, boolean useIdempotency) {
        LedgerAccounts ledger = createAccounts(entries);
        return post(entries, ledger, useIdempotency);
    }

    /**
     * Posts the batch again against accounts that already exist. With idempotency
     * keys this is the replay-safety path: every transfer short-circuits and no
     * balance moves.
     */
    public ReplayResult replayReusing(List<OnlineRetailLoader.InvoiceEntry> entries,
                                      LedgerAccounts ledger,
                                      boolean useIdempotency) {
        return post(entries, ledger, useIdempotency);
    }

    private ReplayResult post(List<OnlineRetailLoader.InvoiceEntry> entries,
                              LedgerAccounts ledger,
                              boolean useIdempotency) {
        long postedEntries = 0;
        long replayedEntries = 0;
        for (OnlineRetailLoader.InvoiceEntry entry : entries) {
            Long customerAccountId = ledger.customerAccountIds().get(entry.customerId());
            long amount = Math.abs(entry.netMinor());
            Long from;
            Long to;
            if (entry.isReversal()) {
                from = customerAccountId;      // money leaves the receivable
                to = ledger.revenueAccountId();
            } else {
                from = ledger.revenueAccountId(); // recognise sales revenue
                to = customerAccountId;           // receivable rises
            }
            String key = useIdempotency ? "invoice:" + entry.invoiceNo() : null;
            TransferOutcome outcome = transfers.transfer(
                    new TransferCommand(from, to, amount, OnlineRetailLoader.CURRENCY, key));
            if (outcome.replayed()) {
                replayedEntries++;
            } else {
                postedEntries++;
            }
        }
        return readState(ledger, postedEntries, replayedEntries);
    }

    private ReplayResult readState(LedgerAccounts ledger, long postedEntries, long replayedEntries) {
        Map<String, Long> customerBalances = new LinkedHashMap<>();
        long customerAssetTotal = 0;
        for (Map.Entry<String, Long> e : ledger.customerAccountIds().entrySet()) {
            long bal = accounts.get(e.getValue()).getBalanceMinor();
            customerBalances.put(e.getKey(), bal);
            customerAssetTotal += bal;
        }
        long revenueBalance = accounts.get(ledger.revenueAccountId()).getBalanceMinor();
        return new ReplayResult(
                ledger, revenueBalance, customerBalances, customerAssetTotal, postedEntries, replayedEntries);
    }

    /** The ledger accounts created for a batch. */
    public record LedgerAccounts(Long revenueAccountId, Map<String, Long> customerAccountIds) {
    }

    /**
     * The ledger state after a replay, read back from the engine's own accounts.
     *
     * @param ledger             the accounts the batch was posted against
     * @param revenueBalance     the engine's revenue balance (minor units)
     * @param customerBalances   customer id -> receivable balance (minor units)
     * @param customerAssetTotal sum of all customer receivable balances
     * @param postedEntries      invoices that posted a new journal entry
     * @param replayedEntries    invoices short-circuited by their idempotency key
     */
    public record ReplayResult(
            LedgerAccounts ledger,
            long revenueBalance,
            Map<String, Long> customerBalances,
            long customerAssetTotal,
            long postedEntries,
            long replayedEntries) {

        public Long revenueAccountId() {
            return ledger.revenueAccountId();
        }

        /**
         * The trial-balance net across the whole ledger: debit-normal balances
         * (the customer receivables) minus credit-normal balances (revenue).
         * Double-entry guarantees this is exactly zero.
         */
        public long trialBalanceNet() {
            return customerAssetTotal - revenueBalance;
        }
    }
}
