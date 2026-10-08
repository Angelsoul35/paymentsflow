package com.angelo.paymentsflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.angelo.paymentsflow.exception.UnbalancedEntryException;
import org.junit.jupiter.api.Test;

/** Pure-domain tests of the double-entry invariant and posting arithmetic. */
class JournalEntryInvariantTest {

    private Account asset(String name) {
        return new Account(name, AccountType.ASSET, "USD");
    }

    @Test
    void balancedEntryIsAccepted() {
        Account from = asset("from");
        Account to = asset("to");

        JournalEntry entry = new JournalEntry("USD", "balanced");
        entry.addPosting(to, Direction.DEBIT, 1500);
        entry.addPosting(from, Direction.CREDIT, 1500);

        assertThatCode(entry::validateBalanced).doesNotThrowAnyException();
        assertThat(entry.totalDebits()).isEqualTo(entry.totalCredits());
    }

    @Test
    void unbalancedEntryIsRejected() {
        JournalEntry entry = new JournalEntry("USD", "unbalanced");
        entry.addPosting(asset("to"), Direction.DEBIT, 1500);
        entry.addPosting(asset("from"), Direction.CREDIT, 1400);

        assertThatThrownBy(entry::validateBalanced)
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessageContaining("unbalanced");
    }

    @Test
    void entryWithFewerThanTwoPostingsIsRejected() {
        JournalEntry entry = new JournalEntry("USD", "single");
        entry.addPosting(asset("only"), Direction.DEBIT, 1000);

        assertThatThrownBy(entry::validateBalanced)
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessageContaining("at least two postings");
    }

    @Test
    void nonPositiveAmountIsRejected() {
        JournalEntry entry = new JournalEntry("USD", "zero");
        entry.addPosting(asset("to"), Direction.DEBIT, 0);
        entry.addPosting(asset("from"), Direction.CREDIT, 0);

        assertThatThrownBy(entry::validateBalanced)
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void debitIncreasesAssetAndCreditDecreasesIt() {
        Account cash = new Account("cash", AccountType.ASSET, "USD");
        cash.apply(Direction.DEBIT, 1000);
        assertThat(cash.getBalanceMinor()).isEqualTo(1000);
        cash.apply(Direction.CREDIT, 400);
        assertThat(cash.getBalanceMinor()).isEqualTo(600);
    }

    @Test
    void creditIncreasesIncomeAndDebitDecreasesIt() {
        Account revenue = new Account("revenue", AccountType.INCOME, "USD");
        revenue.apply(Direction.CREDIT, 1000);
        assertThat(revenue.getBalanceMinor()).isEqualTo(1000);
        revenue.apply(Direction.DEBIT, 250);
        assertThat(revenue.getBalanceMinor()).isEqualTo(750);
    }

    @Test
    void signedEffectFollowsNormalSide() {
        assertThat(Direction.DEBIT.signedEffect(AccountType.ASSET, 100)).isEqualTo(100);
        assertThat(Direction.CREDIT.signedEffect(AccountType.ASSET, 100)).isEqualTo(-100);
        assertThat(Direction.CREDIT.signedEffect(AccountType.LIABILITY, 100)).isEqualTo(100);
        assertThat(Direction.DEBIT.signedEffect(AccountType.LIABILITY, 100)).isEqualTo(-100);
    }
}
