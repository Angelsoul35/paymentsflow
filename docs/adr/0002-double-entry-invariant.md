# ADR 0002 — Model money movement as balanced double-entry postings

## Status
Accepted.

## Context
A transfer could be implemented as "subtract from A, add to B" with two balance
updates. That makes conservation of money a convention enforced by whoever writes
the code, not a property of the data. A bug, a partial failure, or a new code path
can break it silently, and there is no record that ties the decrease in A to the
increase in B.

## Decision
Every money movement is a `JournalEntry` containing at least two `Posting` legs,
each a debit or a credit of a positive amount against one account. The invariant
**sum(debits) == sum(credits)** is enforced by `JournalEntry.validateBalanced()`
before any balance is modified. A transfer is the specific case of an entry that
**debits the destination and credits the source** by the same amount, so it is
balanced by construction; the invariant is still asserted as defence in depth.

How a posting changes a balance depends on the account's normal side
(`AccountType.isDebitNormal()`): a debit increases an asset or expense and decreases
a liability, equity, or income account, and vice versa. This is the standard
double-entry rule, encoded once in `Direction.signedEffect`.

## Consequences
- Conservation of money is a checkable invariant, not a convention: an unbalanced,
  single-leg, or non-positive entry is rejected and can never reach the accounts.
- Every balance is reproducible as the sum of its postings, so the ledger is
  auditable and a balance can be explained.
- Funding an account is itself a balanced entry (debit the asset, credit an equity
  account), which is why the examples fund `alice` from an `owner:equity` account
  rather than writing a balance directly.
- The transfer endpoint exposes only the common two-account case. The domain model
  already supports entries with more than two legs (e.g. splits and fees); exposing
  those is future API surface, not a model change.
