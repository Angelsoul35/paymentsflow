package com.angelo.paymentsflow.web;

import com.angelo.paymentsflow.domain.Account;
import com.angelo.paymentsflow.service.AccountService;
import com.angelo.paymentsflow.web.dto.AccountResponse;
import com.angelo.paymentsflow.web.dto.BalanceResponse;
import com.angelo.paymentsflow.web.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accounts.create(request.name(), request.type(), request.currency());
        return ResponseEntity
                .created(URI.create("/accounts/" + account.getId()))
                .body(AccountResponse.of(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return AccountResponse.of(accounts.get(id));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        return BalanceResponse.of(id, accounts.balanceOf(id));
    }
}
