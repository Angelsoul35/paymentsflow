# ADR 0001 — Represent money as integer minor units

## Status
Accepted.

## Context
Monetary amounts need exact arithmetic. IEEE-754 binary floating point cannot
represent most decimal fractions exactly (`0.1 + 0.2 == 0.30000000000000004`), so a
ledger built on `double`/`float` accumulates rounding error, and a set of postings
that should net to zero does not. A ledger whose balances cannot be trusted to the
cent is worthless.

## Decision
Every amount in the system is a `long` number of **minor units** (cents for most
currencies), paired with an ISO-4217 currency in the `Money` value object. Account
balances, posting amounts, and request/response amounts are all minor units.
Conversion to a decimal string happens only at the presentation edge
(`Money.format()`), never in the arithmetic.

## Consequences
- Addition and subtraction are exact; a balanced entry is exactly balanced, and the
  concurrency test can assert that two balances sum to the funded total with no
  tolerance.
- Clients send and receive integers: `150000` means `1500.00`. The API documents
  this, and the DTO rejects non-integer or negative amounts.
- A `long` holds roughly ±92 quadrillion minor units, far beyond any realistic
  balance, so overflow is not a practical concern at this scope.
- Sub-minor-unit amounts (some FX, per-unit interest) are out of scope. A system
  that needs them would move to a scaled integer or `BigDecimal` with an explicit
  rounding policy — a bigger change than swapping a numeric type, because it also
  needs rounding rules at every boundary.
