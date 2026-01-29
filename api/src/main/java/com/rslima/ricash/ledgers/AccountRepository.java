package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import java.util.List;

public interface AccountRepository {
    Page<Account> listLedgerAccounts(String ledgerId, PageRequest pageRequest);

    Optional<Account> findById(String ledgerId, String accountId);

    Account create(String ledgerId, Account account);

    Account update(String ledgerId, String accountId, String name, String description, AccountType type, String currency, String parentAccountId);

    boolean existsBySlug(String ledgerId, String slug);

    List<String> findChildAccountIds(String ledgerId, String accountId);

    boolean hasTransactions(String accountId);

    void delete(String ledgerId, String accountId);
}
