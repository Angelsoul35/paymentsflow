# ADR 0003 — Make transfers idempotent with a client key

## Status
Accepted.

## Context
Payment clients retry. A request that times out may well have succeeded on the
server, so a blind retry of `POST /transfers` risks posting the same payment twice —
the classic double-charge. The server cannot tell a retry from a genuinely new
transfer without help from the client.

## Decision
`POST /transfers` accepts an optional `Idempotency-Key` header. The key is stored in
an `idempotency_record` table whose **primary key is the key itself**, written in the
same transaction as the journal entry it produced.

- On a request whose key is already recorded, the service returns the original
  transfer (HTTP 200) and posts nothing.
- On a first-time key, the transfer is posted and the key recorded atomically with
  it. If two requests with the same key race, the second loses the unique-key insert
  (`DataIntegrityViolationException`), its transaction rolls back — so it does not
  double-post — and the service resolves it to the original entry.

A request without a key is processed normally; idempotency is opt-in, as the header
name implies.

## Consequences
- A retried transfer is safe: at most one posting per key, even under concurrency.
- Idempotency and posting share one transaction, so the key is never recorded for a
  transfer that did not commit, and a committed transfer always has its key.
- Keys accumulate. At this scope they are kept indefinitely; a production system
  would expire them (e.g. a TTL or periodic sweep) and may scope a key to a client or
  endpoint rather than globally.
- The key maps to one canonical result. Replaying a key with a *different* body still
  returns the original transfer; validating that the replayed body matches is a
  reasonable stricter policy left out here for simplicity.
