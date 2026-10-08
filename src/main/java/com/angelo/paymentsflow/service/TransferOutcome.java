package com.angelo.paymentsflow.service;

/**
 * The result of handling a transfer request.
 *
 * @param journalEntryId the posted (or previously posted) entry
 * @param replayed       {@code true} if an idempotency key matched an existing
 *                       entry and nothing new was posted
 */
public record TransferOutcome(Long journalEntryId, boolean replayed) {
}
