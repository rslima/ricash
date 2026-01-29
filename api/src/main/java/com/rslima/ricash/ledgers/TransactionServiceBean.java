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
public class TransactionServiceBean implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final LedgerRepository ledgerRepository;

    @Override
    public Page<Transaction> listLedgerTransactions(String userId, String ledgerSlug, PageRequest pageRequest) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        return transactionRepository.listLedgerTransactions(ledger.id(), pageRequest);
    }

    @Override
    public Page<Transaction> listAccountTransactions(String userId, String ledgerSlug, String accountId, PageRequest pageRequest) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        return transactionRepository.listAccountTransactions(ledger.id(), accountId, pageRequest);
    }

    @Override
    public Optional<Transaction> find(String userId, String ledgerSlug, String transactionId) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        return transactionRepository.findById(ledger.id(), transactionId);
    }

    @Override
    public Transaction create(String userId, String ledgerSlug, CreateTransactionRequest request) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        // Validate double-entry bookkeeping: debits must equal credits
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (var entry : request.entries()) {
            if (entry.type() == TransactionEntryType.DEBIT) {
                totalDebits = totalDebits.add(entry.amount());
            } else {
                totalCredits = totalCredits.add(entry.amount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalArgumentException("Transaction is not balanced: debits (" + totalDebits + ") must equal credits (" + totalCredits + ")");
        }

        // Convert request entries to domain entries
        List<TransactionEntry> debitEntries = request.entries().stream()
                .filter(e -> e.type() == TransactionEntryType.DEBIT)
                .map(e -> new TransactionEntry(
                        e.accountId(),
                        TransactionEntryType.DEBIT,
                        new MonetaryAmount(e.amount(), ledger.currency()),
                        null
                ))
                .toList();

        List<TransactionEntry> creditEntries = request.entries().stream()
                .filter(e -> e.type() == TransactionEntryType.CREDIT)
                .map(e -> new TransactionEntry(
                        e.accountId(),
                        TransactionEntryType.CREDIT,
                        new MonetaryAmount(e.amount(), ledger.currency()),
                        null
                ))
                .toList();

        final var transaction = new Transaction(
                UuidCreator.getTimeOrderedEpoch().toString(),
                request.date(),
                Instant.now(),
                request.description(),
                creditEntries,
                debitEntries
        );

        return transactionRepository.create(ledger.id(), transaction);
    }

    @Override
    public Transaction update(String userId, String ledgerSlug, String transactionId, UpdateTransactionRequest request) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        // Verify transaction exists
        final var existing = transactionRepository.findById(ledger.id(), transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        // Validate double-entry bookkeeping: debits must equal credits
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (var entry : request.entries()) {
            if (entry.type() == TransactionEntryType.DEBIT) {
                totalDebits = totalDebits.add(entry.amount());
            } else {
                totalCredits = totalCredits.add(entry.amount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalArgumentException("Transaction is not balanced: debits (" + totalDebits + ") must equal credits (" + totalCredits + ")");
        }

        // Convert request entries to domain entries
        List<TransactionEntry> debitEntries = request.entries().stream()
                .filter(e -> e.type() == TransactionEntryType.DEBIT)
                .map(e -> new TransactionEntry(
                        e.accountId(),
                        TransactionEntryType.DEBIT,
                        new MonetaryAmount(e.amount(), ledger.currency()),
                        null
                ))
                .toList();

        List<TransactionEntry> creditEntries = request.entries().stream()
                .filter(e -> e.type() == TransactionEntryType.CREDIT)
                .map(e -> new TransactionEntry(
                        e.accountId(),
                        TransactionEntryType.CREDIT,
                        new MonetaryAmount(e.amount(), ledger.currency()),
                        null
                ))
                .toList();

        final var transaction = new Transaction(
                transactionId,
                request.date(),
                existing.createdAt(),
                request.description(),
                creditEntries,
                debitEntries
        );

        return transactionRepository.update(ledger.id(), transaction);
    }

    @Override
    public void delete(String userId, String ledgerSlug, String transactionId) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        transactionRepository.delete(ledger.id(), transactionId);
    }

    private Ledger getLedgerBySlug(String userId, String ledgerSlug) {
        return ledgerRepository.findBySlug(userId, ledgerSlug)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerSlug));
    }
}
