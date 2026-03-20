package com.rslima.ricash.ledgers.accounts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

public interface AccountService {
    Page<Account> listLedgerAccounts(String userId, String ledgerSlug, PageRequest pageRequest);

    Optional<Account> find(String userId, String ledgerSlug, String accountId);

    Account create(String userId, String ledgerSlug, CreateAccountRequest request);

    Account update(String userId, String ledgerSlug, String accountId, UpdateAccountRequest request);

    void delete(String userId, String ledgerSlug, String accountId);
}
