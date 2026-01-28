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

        final var account = new Account(
                UuidCreator.getTimeOrderedEpoch().toString(),
                request.name(),
                request.description(),
                request.currency(),
                request.type(),
                AccountStatus.ACTIVE,
                BigDecimal.ZERO,
                Instant.now(),
                List.of()
        );

        return accountRepository.create(ledger.id(), account);
    }

    private Ledger getLedgerBySlug(String userId, String ledgerSlug) {
        return ledgerRepository.findBySlug(userId, ledgerSlug)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerSlug));
    }
}
