package com.rslima.ricash.ledgers.accounts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Optional;

import java.util.List;

public interface AccountRepository {
    Page<Account> listLedgerAccounts(String ledgerId, Pageable pageRequest);

    Optional<Account> findById(String ledgerId, String accountId);

    /**
     * Batch-loads lightweight account projections by id, without the expensive
     * balance rollup. Unknown ids are simply absent from the result.
     */
    List<AccountRef> findRefsByIds(String ledgerId, Collection<String> accountIds);

    Account create(String ledgerId, Account account);

    Account update(String ledgerId, String accountId, String name, String description, AccountType type, String currency, String parentAccountId);

    boolean existsBySlug(String ledgerId, String slug);

    List<String> findChildAccountIds(String ledgerId, String accountId);

    boolean hasTransactions(String accountId);

    void delete(String ledgerId, String accountId);

    BalanceSummary getBalanceSummary(String ledgerId);
}
