package com.rslima.ricash.ledgers;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class AccountServiceBean implements AccountService {
    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final SlugService slugService;

    @Override
    public Page<Account> listLedgerAccounts(String userId, String ledgerSlug, PageRequest pageRequest) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        return accountRepository.listLedgerAccounts(ledger.id(), pageRequest);
    }

    @Override
    public Optional<Account> find(String userId, String ledgerSlug, String accountId) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        return accountRepository.findById(ledger.id(), accountId);
    }

    @Override
    public Account create(String userId, String ledgerSlug, CreateAccountRequest request) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        final var baseSlug = slugService.slugify(request.name());
        final var slug = generateUniqueSlug(ledger.id(), baseSlug);

        final var account = new Account(
                UuidCreator.getTimeOrderedEpoch().toString(),
                slug,
                request.name(),
                request.description(),
                request.currency(),
                request.type(),
                AccountStatus.ACTIVE,
                BigDecimal.ZERO,
                Instant.now(),
                request.parentAccountId(),
                List.of()
        );

        return accountRepository.create(ledger.id(), account);
    }

    @Override
    public Account update(String userId, String ledgerSlug, String accountId, UpdateAccountRequest request) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        // Verify account exists
        accountRepository.findById(ledger.id(), accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return accountRepository.update(
                ledger.id(),
                accountId,
                request.name(),
                request.description(),
                request.type(),
                request.currency(),
                request.parentAccountId()
        );
    }

    @Override
    public void delete(String userId, String ledgerSlug, String accountId) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        // Verify account exists
        accountRepository.findById(ledger.id(), accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // Collect all account IDs to delete (account + all descendants)
        List<String> accountIdsToDelete = new java.util.ArrayList<>();
        collectAccountIdsRecursively(ledger.id(), accountId, accountIdsToDelete);

        // Check if any of the accounts have transactions
        for (String id : accountIdsToDelete) {
            if (accountRepository.hasTransactions(id)) {
                throw new AccountHasTransactionsException(id);
            }
        }

        // Delete accounts in reverse order (children first)
        java.util.Collections.reverse(accountIdsToDelete);
        for (String id : accountIdsToDelete) {
            accountRepository.delete(ledger.id(), id);
        }
    }

    private void collectAccountIdsRecursively(String ledgerId, String accountId, List<String> accountIds) {
        accountIds.add(accountId);
        List<String> childIds = accountRepository.findChildAccountIds(ledgerId, accountId);
        for (String childId : childIds) {
            collectAccountIdsRecursively(ledgerId, childId, accountIds);
        }
    }

    private String generateUniqueSlug(String ledgerId, String baseSlug) {
        String slug = baseSlug;
        int counter = 1;

        while (accountRepository.existsBySlug(ledgerId, slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }

    private Ledger getLedgerBySlug(String userId, String ledgerSlug) {
        return ledgerRepository.findBySlug(userId, ledgerSlug)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerSlug));
    }
}
