package com.angelo.paymentsflow.service;

/**
 * An internal, validated-at-the-edge request to move {@code amountMinor} minor
 * units of {@code currency} from one account to another. Decouples the service
 * from the web DTO.
 *
 * @param fromAccountId  the account to credit (money leaves)
 * @param toAccountId    the account to debit (money arrives)
 * @param amountMinor    a positive amount in minor units
 * @param currency       ISO-4217 code; must match both accounts
 * @param idempotencyKey optional client key; when present, a replay returns the
 *                       original result instead of posting again
 */
public record TransferCommand(
        Long fromAccountId,
        Long toAccountId,
        long amountMinor,
        String currency,
        String idempotencyKey) {
}
