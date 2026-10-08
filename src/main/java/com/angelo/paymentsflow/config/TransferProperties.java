package com.angelo.paymentsflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable behaviour for transfer posting, bound from the {@code paymentsflow.transfer}
 * configuration prefix.
 *
 * @param maxRetries how many times a transfer is retried when it loses the
 *                   optimistic-lock race before the request is rejected with 409
 */
@ConfigurationProperties(prefix = "paymentsflow.transfer")
public record TransferProperties(int maxRetries) {

    public TransferProperties {
        if (maxRetries < 1) {
            maxRetries = 8;
        }
    }
}
