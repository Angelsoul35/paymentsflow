# ADR 0004 — Guard concurrent balance updates with optimistic locking

## Status
Accepted.

## Context
Two transfers that touch the same account can interleave: both read balance = 100,
both compute a new balance, and the second write overwrites the first — a lost
update. A ledger that loses updates loses money. Something has to serialise the
conflicting part of two concurrent transfers without serialising *all* transfers.

## Decision
Each `Account` carries a JPA `@Version` column. When a transfer writes a balance,
Hibernate issues `UPDATE ... WHERE id = ? AND version = ?`; if a concurrent
transaction already bumped the version, zero rows match and the write fails with an
optimistic-lock exception.

`TransferService` wraps each attempt in a bounded **retry loop** (default 8 attempts,
configurable via `paymentsflow.transfer.max-retries`). On a conflict it retries in a
fresh transaction with a short randomised back-off; if the budget is exhausted it
returns **409 Conflict** so the client can retry. The transactional unit of work
lives in a separate `TransferExecutor` bean precisely so each retry runs in its own
transaction.

## Consequences
- No lost updates: a concurrent write is detected and retried or rejected, never
  silently applied over another. The concurrency test fires many simultaneous
  transfers at one pair of accounts and asserts the balances are exactly right.
- Non-conflicting transfers run fully in parallel; only the rare real conflict pays
  the cost, so throughput stays high compared with a pessimistic lock on every
  transfer.
- Under heavy contention on the *same* account, retries add latency and a persistent
  hotspot can still exhaust the budget and return 409. That is the honest trade-off;
  a workload with such hotspots might shard the hot account or batch its postings.
- Because all transfers lock accounts in the same order (source then destination
  within one flush), this design does not introduce lock-ordering deadlocks.
