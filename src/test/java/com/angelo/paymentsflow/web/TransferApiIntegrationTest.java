package com.angelo.paymentsflow.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelo.paymentsflow.repository.AccountRepository;
import com.angelo.paymentsflow.repository.IdempotencyRecordRepository;
import com.angelo.paymentsflow.repository.JournalEntryRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TransferApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private JournalEntryRepository journalEntries;
    @Autowired
    private IdempotencyRecordRepository idempotencyRecords;

    @BeforeEach
    void clean() {
        idempotencyRecords.deleteAll();
        journalEntries.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void successfulTransferUpdatesBothBalances() throws Exception {
        long equity = createAccount("owner:equity", "EQUITY", "USD");
        long alice = createAccount("alice:checking", "ASSET", "USD");
        long bob = createAccount("bob:checking", "ASSET", "USD");

        // Fund alice from equity: a valid opening-balance entry (debit asset, credit equity).
        postTransfer(equity, alice, 500_000, "USD", null).andExpect(status().isCreated());

        // Move 1,500.00 from alice to bob.
        postTransfer(alice, bob, 150_000, "USD", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("POSTED")))
                .andExpect(jsonPath("$.amount", is(150000)))
                .andExpect(jsonPath("$.fromAccountId", is((int) alice)))
                .andExpect(jsonPath("$.toAccountId", is((int) bob)))
                .andExpect(jsonPath("$.postings.length()", is(2)));

        assertBalance(alice, 350_000);
        assertBalance(bob, 150_000);
        assertBalance(equity, 500_000);
    }

    @Test
    void replayingSameIdempotencyKeyDoesNotDoublePost() throws Exception {
        long equity = createAccount("owner:equity", "EQUITY", "USD");
        long alice = createAccount("alice:checking", "ASSET", "USD");
        long bob = createAccount("bob:checking", "ASSET", "USD");
        postTransfer(equity, alice, 500_000, "USD", null).andExpect(status().isCreated());

        String first = postTransfer(alice, bob, 150_000, "USD", "key-abc-123")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int firstId = JsonPath.parse(first).read("$.id", Integer.class);

        // Same key again: must return the SAME transfer with HTTP 200 and not post again.
        String replay = postTransfer(alice, bob, 150_000, "USD", "key-abc-123")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int replayId = JsonPath.parse(replay).read("$.id", Integer.class);

        org.assertj.core.api.Assertions.assertThat(replayId).isEqualTo(firstId);

        // Balances reflect exactly one posting, and only one journal entry exists for the move.
        assertBalance(alice, 350_000);
        assertBalance(bob, 150_000);
        org.assertj.core.api.Assertions.assertThat(journalEntries.count()).isEqualTo(2);
    }

    @Test
    void unknownAccountInTransferReturns422() throws Exception {
        long alice = createAccount("alice:checking", "ASSET", "USD");
        postTransfer(alice, 999_999, 100, "USD", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("transfer-rejected")))
                .andExpect(jsonPath("$.detail", is("unknown account: 999999")));
    }

    @Test
    void currencyMismatchReturns422() throws Exception {
        long usd = createAccount("usd:wallet", "ASSET", "USD");
        long eur = createAccount("eur:wallet", "ASSET", "EUR");
        postTransfer(usd, eur, 100, "USD", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("transfer-rejected")));
    }

    @Test
    void sameAccountTransferReturns422() throws Exception {
        long a = createAccount("a", "ASSET", "USD");
        postTransfer(a, a, 100, "USD", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("must differ")));
    }

    @Test
    void negativeAmountReturns400() throws Exception {
        long a = createAccount("a", "ASSET", "USD");
        long b = createAccount("b", "ASSET", "USD");
        postTransfer(a, b, -100, "USD", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("validation-failed")))
                .andExpect(jsonPath("$.errors[0].field", is("amount")));
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("validation-failed")));
    }

    @Test
    void getsAPostedTransfer() throws Exception {
        long equity = createAccount("owner:equity", "EQUITY", "USD");
        long alice = createAccount("alice:checking", "ASSET", "USD");
        String created = postTransfer(equity, alice, 10_000, "USD", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.parse(created).read("$.id", Integer.class);

        mvc.perform(get("/transfers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.amount", is(10000)))
                .andExpect(jsonPath("$.postings.length()", is(2)));
    }

    @Test
    void unknownTransferReturns404() throws Exception {
        mvc.perform(get("/transfers/{id}", 123456))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("transfer-not-found")));
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.ResultActions postTransfer(
            long from, long to, long amount, String currency, String idempotencyKey) throws Exception {
        var request = post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\":%d,\"toAccountId\":%d,\"amount\":%d,\"currency\":\"%s\"}"
                        .formatted(from, to, amount, currency));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    private long createAccount(String name, String type, String currency) throws Exception {
        String body = mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"type\":\"%s\",\"currency\":\"%s\"}"
                                .formatted(name, type, currency)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Integer.class).longValue();
    }

    private void assertBalance(long accountId, long expectedMinor) throws Exception {
        mvc.perform(get("/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.amount", is((int) expectedMinor)));
    }
}
