package com.angelo.paymentsflow.service;

import com.angelo.paymentsflow.domain.Account;
import com.angelo.paymentsflow.domain.AccountType;
import com.angelo.paymentsflow.domain.Money;
import com.angelo.paymentsflow.exception.AccountNotFoundException;
import com.angelo.paymentsflow.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for creating and reading accounts. */
@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account create(String name, AccountType type, String currency) {
        return accounts.save(new Account(name, type, currency));
    }

    @Transactional(readOnly = true)
    public Account get(Long id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Money balanceOf(Long id) {
        return get(id).getBalance();
    }
}
