package com.angelo.paymentsflow.domain;

import java.util.Currency;

/**
 * An amount of money expressed as an integer number of <em>minor units</em>
 * (cents for most currencies) together with its ISO-4217 currency code.
 *
 * <p>Money is never represented as {@code double} or {@code float}: IEEE-754
 * cannot represent most decimal fractions exactly ({@code 0.1 + 0.2 != 0.3}),
 * so floating-point ledgers accumulate rounding error and a balance that should
 * net to zero does not. Integer arithmetic on minor units is exact, so a
 * balanced journal entry balances to the last cent.
 *
 * <p>This is a pure value object. Conversion to a human-readable string happens
 * only at the presentation edge via {@link #format()}, never in the arithmetic.
 */
public record Money(long minorUnits, String currency) {

    public Money {
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        currency = currency.toUpperCase();
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code, got: " + currency);
        }
    }

    public static Money of(long minorUnits, String currency) {
        return new Money(minorUnits, currency);
    }

    /** Returns a copy with {@code delta} minor units added (same currency). */
    public Money plus(long delta) {
        return new Money(minorUnits + delta, currency);
    }

    /**
     * Formats for display only, e.g. {@code 150000 USD -> "1500.00 USD"}, using
     * the currency's default fraction digits (falling back to 2).
     */
    public String format() {
        int digits = 2;
        try {
            int d = Currency.getInstance(currency).getDefaultFractionDigits();
            if (d >= 0) {
                digits = d;
            }
        } catch (IllegalArgumentException ignored) {
            // Non-ISO placeholder currency in a test; keep the 2-digit default.
        }
        long scale = (long) Math.pow(10, digits);
        boolean negative = minorUnits < 0;
        long magnitude = Math.abs(minorUnits);
        long whole = magnitude / scale;
        long fraction = magnitude % scale;
        return String.format("%s%d.%0" + digits + "d %s", negative ? "-" : "", whole, fraction, currency);
    }
}
