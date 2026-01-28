package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

public interface AccountRepository {
    Page<Account> listLedgerAccounts(String ledgerId, PageRequest pageRequest);

    Optional<Account> findById(String ledgerId, String accountId);

    Account create(String ledgerId, Account account);
}
