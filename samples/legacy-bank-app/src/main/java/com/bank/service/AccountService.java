package com.bank.service;

import com.bank.model.Account;
import com.bank.repository.AccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public Optional<Account> find(Long id) {
        return repository.findById(id);
    }

    public Account open(Account account) {
        if (account.getBalance() == null) {
            account.setBalance(BigDecimal.ZERO);
        }
        return repository.save(account);
    }

    public List<Account> page(int page, int size) {
        return repository.findAll(PageRequest.of(page, size)).getContent();
    }
}
