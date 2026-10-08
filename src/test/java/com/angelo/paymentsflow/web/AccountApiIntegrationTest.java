package com.angelo.paymentsflow.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelo.paymentsflow.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accounts;

    @BeforeEach
    void clean() {
        accounts.deleteAll();
    }

    @Test
    void createsAccountWithZeroBalance() throws Exception {
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"alice:checking","type":"ASSET","currency":"USD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("alice:checking")))
                .andExpect(jsonPath("$.type", is("ASSET")))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.balance.amount", is(0)))
                .andExpect(jsonPath("$.balance.formatted", is("0.00 USD")));
    }

    @Test
    void getsAnExistingAccount() throws Exception {
        long id = createAccount("bob:checking", "ASSET", "USD");

        mvc.perform(get("/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)))
                .andExpect(jsonPath("$.name", is("bob:checking")));
    }

    @Test
    void getBalanceReturnsZeroForNewAccount() throws Exception {
        long id = createAccount("eur:wallet", "ASSET", "EUR");

        mvc.perform(get("/accounts/{id}/balance", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is((int) id)))
                .andExpect(jsonPath("$.balance.amount", is(0)))
                .andExpect(jsonPath("$.balance.currency", is("EUR")));
    }

    @Test
    void unknownAccountReturns404ProblemDetail() throws Exception {
        mvc.perform(get("/accounts/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title", is("Account not found")))
                .andExpect(jsonPath("$.code", is("account-not-found")));
    }

    @Test
    void invalidCurrencyReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","type":"ASSET","currency":"US"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("validation-failed")))
                .andExpect(jsonPath("$.errors[0].field", is("currency")));
    }

    @Test
    void unknownAccountTypeReturns400() throws Exception {
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","type":"BANANA","currency":"USD"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private long createAccount(String name, String type, String currency) throws Exception {
        String body = mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"type\":\"%s\",\"currency\":\"%s\"}"
                                .formatted(name, type, currency)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Integer.class).longValue();
    }
}
