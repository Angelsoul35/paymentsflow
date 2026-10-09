# Real-inputs snapshot — UCI Online Retail

This directory vendors a small, self-contained snapshot of **real** transaction
records and the script that reproduces it. The snapshot is fed through
paymentsflow's own double-entry engine by the benchmark
(`src/test/java/com/angelo/paymentsflow/realinputs/`), and the engine's ledger
is validated against the reference totals pinned below.

## What this is

`online_retail_head.csv` is the first **1,989** line items of the UCI *Online
Retail* dataset — the real transactions of a UK-based, registered non-store
online retailer between 2010-12-01 and 2011-12-09. It is cut at a complete
invoice boundary so every invoice in the snapshot is whole.

Columns (one invoice line item per row):

| Column        | Meaning                                              |
| ------------- | ---------------------------------------------------- |
| `InvoiceNo`   | Invoice number; a leading `C` marks a credit note    |
| `StockCode`   | Product code                                         |
| `Description` | Product description (may contain commas; RFC-4180 quoted) |
| `Quantity`    | Units; negative on a credit note                     |
| `InvoiceDate` | Timestamp                                            |
| `UnitPrice`   | Unit price in GBP, always two decimals in this snapshot |
| `CustomerID`  | Customer identifier; blank when the sale has no registered customer |
| `Country`     | Customer country                                     |

## Source, license, attribution

- **Dataset:** *Online Retail*, UCI Machine Learning Repository.
  DOI [10.24432/C5BW33](https://doi.org/10.24432/C5BW33).
- **Creator:** Daqing Chen, School of Engineering, London South Bank University (2015).
- **Introductory paper:** Chen, D., Sain, S. L., & Guo, K. (2012). *Data mining
  for the online retail industry.* Journal of Database Marketing & Customer
  Strategy Management, 19(3), 197–208.
- **License:** Creative Commons Attribution 4.0 International (CC BY 4.0).
  Reuse and adaptation are permitted with attribution; this directory provides it.
- **Access path used here:** the HuggingFace datasets-server **rows API**,
  mirror [`aarav912/online-retail`](https://huggingface.co/datasets/aarav912/online-retail),
  endpoint
  `https://datasets-server.huggingface.co/rows?dataset=aarav912%2Fonline-retail&config=default&split=train&offset=N&length=100`.
  The mirror carries the canonical first invoice (536365, StockCode 85123A,
  qty 6, 2010-12-01, 2.55 GBP, customer 17850), confirming it is the UCI data.

## Regeneration

```bash
python3 regenerate.py           # rewrites online_retail_head.csv + prints pinned values
python3 regenerate.py --check   # asserts the committed CSV is byte-identical to a fresh pull
```

`regenerate.py` is also the **independent reference**: it recomputes every total
the Java engine is checked against, in pure Python, directly from the raw rows —
a different language and a different code path over the same records.

## Shaping rules (engine inputs)

The loader and the reference script apply the same rules to turn line items into
ledger postings:

- Line amount (pence) = `Quantity × round(UnitPrice × 100)`, integer arithmetic only.
- An invoice's **net** amount is the sum of its line amounts. Each invoice maps
  to exactly one balanced journal entry carrying that net.
- Invoices with any **blank `CustomerID`** are skipped (no receivable account).
- Invoices whose **net is zero** are skipped (a posting must move a positive amount).

Mapping to double-entry: one credit-normal `INCOME` account `revenue:sales` and
one debit-normal `ASSET` receivable per customer. A sale (net > 0) is
`transfer(revenue → customer)`; a return (net < 0) is `transfer(customer → revenue)`.

## Pinned reference values (independently computed)

Over this snapshot (all amounts in pence):

| Quantity                                   | Value       |
| ------------------------------------------ | ----------- |
| line items                                 | 1,989       |
| journal entries (invoices kept)            | 89          |
| customer accounts                          | 71          |
| reversal (credit-note) entries             | 6           |
| revenue — net sales                        | 3,222,693   |
| sum of customer receivables                | 3,222,693   |
| trial-balance net (assets − credits)       | 0           |
| gross turnover (Σ entry debits = Σ credits)| 3,287,739   |

Sample per-customer receivables: `17850 = 149,934`, `13047 = 36,663`,
`15311 = 44,533`, and two driven negative by credit notes: `17548 = −14,148`,
`14527 = −2,750`.

The benchmark asserts the engine reproduces all of these to the penny, that the
trial balance is exactly zero, and that an idempotent replay posts nothing new.
Three tamper controls then corrupt an input or the ledger and prove a specific
check fails.
