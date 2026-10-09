package com.angelo.paymentsflow.realinputs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the vendored real-world snapshot of the UCI <em>Online Retail</em>
 * dataset (a real UK online retailer's 2010&ndash;2011 transactions) and shapes
 * it into ledger inputs for the engine under test.
 *
 * <p>Each row is one invoice line item: {@code InvoiceNo, StockCode, Description,
 * Quantity, InvoiceDate, UnitPrice, CustomerID, Country}. Money arrives as a
 * sterling decimal (always two places in this snapshot) and is converted to an
 * exact integer number of pence &mdash; never through {@code double} &mdash; so
 * the amounts fed to the ledger are exact, matching the engine's own minor-unit
 * model.
 *
 * <h2>Shaping rules (documented and reproducible)</h2>
 * <ul>
 *   <li>Line amount (pence) = {@code Quantity * round(UnitPrice * 100)};
 *       {@code Quantity} may be negative on a credit note (an {@code InvoiceNo}
 *       starting with {@code C}).</li>
 *   <li>An invoice's net amount is the sum of its line amounts. The invoice is
 *       mapped to exactly one balanced journal entry carrying that net amount.</li>
 *   <li>Invoices with any blank {@code CustomerID} are skipped: without a
 *       customer there is no receivable account to post against.</li>
 *   <li>Invoices whose net amount is zero are skipped: a ledger posting must
 *       move a positive amount, and a zero-net invoice has no economic effect.</li>
 * </ul>
 * These rules are applied identically by the independent Python reference that
 * pins the expected totals (see the dataset README), so the two computations are
 * genuinely independent code paths over the same real records.
 */
public final class OnlineRetailLoader {

    /** Classpath location of the vendored snapshot. */
    public static final String RESOURCE = "/realdata/online-retail/online_retail_head.csv";

    /** The ISO-4217 currency of the dataset (a real UK retailer). */
    public static final String CURRENCY = "GBP";

    private OnlineRetailLoader() {
    }

    /** One raw invoice line item as read from the snapshot. */
    public record LineItem(
            String invoiceNo,
            String stockCode,
            String description,
            long quantity,
            String invoiceDate,
            long unitPriceMinor,
            String customerId,
            String country) {

        /** Signed amount of this line in pence ({@code quantity * unitPriceMinor}). */
        public long lineMinor() {
            return quantity * unitPriceMinor;
        }
    }

    /**
     * One invoice collapsed to a single balanced ledger posting.
     *
     * @param invoiceNo  the source invoice number (used as the idempotency key)
     * @param customerId the single customer the invoice belongs to
     * @param netMinor   the signed net amount in pence; positive is a sale
     *                   (debit the customer, credit sales), negative is a return
     */
    public record InvoiceEntry(String invoiceNo, String customerId, long netMinor) {
        public boolean isReversal() {
            return netMinor < 0;
        }
    }

    /** Reads every raw line item from the snapshot, in file order. */
    public static List<LineItem> readLineItems() {
        try (InputStream in = OnlineRetailLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("vendored snapshot not found on classpath: " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String[]> records = CsvReader.readAll(reader);
            if (records.isEmpty()) {
                throw new IllegalStateException("snapshot is empty");
            }
            String[] header = records.get(0);
            requireHeader(header);
            List<LineItem> items = new ArrayList<>(records.size() - 1);
            for (int i = 1; i < records.size(); i++) {
                String[] r = records.get(i);
                if (r.length != 8) {
                    throw new IllegalStateException(
                            "row " + i + " has " + r.length + " columns, expected 8");
                }
                items.add(new LineItem(
                        r[0].trim(),
                        r[1].trim(),
                        r[2],
                        Long.parseLong(r[3].trim()),
                        r[4].trim(),
                        parsePence(r[5].trim()),
                        r[6].trim(),
                        r[7].trim()));
            }
            return items;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Groups the raw line items into one balanced {@link InvoiceEntry} per
     * invoice, applying the documented shaping rules. Order is preserved so the
     * running-balance recurrence can be reproduced deterministically.
     */
    public static List<InvoiceEntry> loadInvoiceEntries() {
        Map<String, List<LineItem>> byInvoice = new LinkedHashMap<>();
        for (LineItem item : readLineItems()) {
            byInvoice.computeIfAbsent(item.invoiceNo(), k -> new ArrayList<>()).add(item);
        }
        List<InvoiceEntry> entries = new ArrayList<>();
        for (Map.Entry<String, List<LineItem>> e : byInvoice.entrySet()) {
            List<LineItem> lines = e.getValue();
            if (lines.stream().anyMatch(li -> li.customerId().isEmpty())) {
                continue; // no customer -> no receivable account to post against
            }
            String customer = lines.get(0).customerId();
            long net = 0;
            for (LineItem li : lines) {
                net += li.lineMinor();
            }
            if (net == 0) {
                continue; // a posting must move a positive amount
            }
            entries.add(new InvoiceEntry(e.getKey(), customer, net));
        }
        return entries;
    }

    /**
     * Converts a two-decimal sterling string (e.g. {@code "2.55"}, {@code "27.50"},
     * {@code "0.00"}) to exact pence using integer arithmetic only. A value with no
     * decimal point is treated as whole pounds; more than two decimal places is
     * rejected rather than silently rounded.
     */
    static long parsePence(String value) {
        boolean negative = value.startsWith("-");
        String v = negative ? value.substring(1) : value;
        int dot = v.indexOf('.');
        long pounds;
        long pence;
        if (dot < 0) {
            pounds = Long.parseLong(v);
            pence = 0;
        } else {
            String whole = v.substring(0, dot);
            String frac = v.substring(dot + 1);
            if (frac.length() > 2) {
                throw new IllegalArgumentException("unexpected sub-penny precision: " + value);
            }
            while (frac.length() < 2) {
                frac += "0";
            }
            pounds = whole.isEmpty() ? 0 : Long.parseLong(whole);
            pence = Long.parseLong(frac);
        }
        long total = pounds * 100 + pence;
        return negative ? -total : total;
    }

    private static void requireHeader(String[] header) {
        String[] expected = {
                "InvoiceNo", "StockCode", "Description", "Quantity",
                "InvoiceDate", "UnitPrice", "CustomerID", "Country"};
        if (header.length != expected.length) {
            throw new IllegalStateException("unexpected header width: " + header.length);
        }
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].equals(header[i].trim())) {
                throw new IllegalStateException(
                        "unexpected header column " + i + ": '" + header[i] + "' (expected '" + expected[i] + "')");
            }
        }
    }
}
