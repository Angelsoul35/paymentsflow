package com.angelo.paymentsflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void formatsMinorUnitsWithTwoDecimals() {
        assertThat(Money.of(150000, "USD").format()).isEqualTo("1500.00 USD");
        assertThat(Money.of(5, "USD").format()).isEqualTo("0.05 USD");
        assertThat(Money.of(0, "EUR").format()).isEqualTo("0.00 EUR");
    }

    @Test
    void formatsNegativeBalancesWithSign() {
        assertThat(Money.of(-150000, "USD").format()).isEqualTo("-1500.00 USD");
        assertThat(Money.of(-5, "USD").format()).isEqualTo("-0.05 USD");
    }

    @Test
    void normalisesCurrencyToUpperCase() {
        assertThat(Money.of(100, "usd").currency()).isEqualTo("USD");
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> Money.of(100, "US"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(100, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plusIsExact() {
        assertThat(Money.of(100, "USD").plus(50).minorUnits()).isEqualTo(150);
        assertThat(Money.of(100, "USD").plus(-150).minorUnits()).isEqualTo(-50);
    }
}
