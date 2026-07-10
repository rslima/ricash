package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.ledgers.Ledger;
import com.rslima.ricash.ledgers.LedgerAccess;
import com.rslima.ricash.ledgers.MonetaryAmount;
import com.rslima.ricash.ledgers.accounts.AccountRef;
import com.rslima.ricash.ledgers.accounts.AccountRepository;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateService;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class TransactionServiceBean implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final LedgerAccess ledgerAccess;
    private final AccountRepository accountRepository;
    private final ExchangeRateService exchangeRateService;

    @Override
    public Page<Transaction> listLedgerTransactions(String userId, String ledgerSlug, Pageable pageRequest) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.listLedgerTransactions(ledger.id(), pageRequest);
    }

    @Override
    public Page<Transaction> searchByDescription(String userId, String ledgerSlug, String description, Pageable pageRequest) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.searchByDescription(ledger.id(), description, pageRequest);
    }

    @Override
    public Page<Transaction> listAccountTransactions(String userId, String ledgerSlug, String accountId, Pageable pageRequest) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.listAccountTransactions(ledger.id(), accountId, pageRequest);
    }

    @Override
    public Page<Transaction> listCategoryTransactions(String userId, String ledgerSlug, String accountId, int year, int month, Pageable pageRequest) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.listCategoryTransactions(ledger.id(), accountId, year, month, pageRequest);
    }

    @Override
    public Page<CategoryTransaction> listCategoryTransactionAmounts(String userId, String ledgerSlug, String accountId, int year, int month, Pageable pageRequest) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.listCategoryTransactionAmounts(ledger.id(), accountId, year, month, pageRequest);
    }

    @Override
    public Optional<Transaction> find(String userId, String ledgerSlug, String transactionId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.findById(ledger.id(), transactionId);
    }

    @Override
    @Transactional
    public Transaction create(String userId, String ledgerSlug, CreateTransactionRequest request) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        // Process entries with currency conversions
        List<TransactionEntry> processedEntries = processEntries(ledger, request.entries(), request.date());

        // Validate multi-currency balance
        validateMultiCurrencyBalance(processedEntries);

        // Separate debits and credits
        List<TransactionEntry> debitEntries = processedEntries.stream()
                .filter(e -> e.type() == TransactionEntryType.DEBIT)
                .toList();

        List<TransactionEntry> creditEntries = processedEntries.stream()
                .filter(e -> e.type() == TransactionEntryType.CREDIT)
                .toList();

        final var transaction = new Transaction(
                UuidCreator.getTimeOrderedEpoch().toString(),
                request.date(),
                Instant.now(),
                request.description(),
                creditEntries,
                debitEntries
        );

        transactionRepository.create(ledger.id(), transaction);

        // Fetch the created transaction to get account names populated
        return transactionRepository.findById(ledger.id(), transaction.id())
                .orElseThrow(() -> new TransactionNotFoundException(transaction.id()));
    }

    @Override
    @Transactional
    public Transaction update(String userId, String ledgerSlug, String transactionId, UpdateTransactionRequest request) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        // Verify transaction exists
        final var existing = transactionRepository.findById(ledger.id(), transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        // Process entries with currency conversions
        List<TransactionEntry> processedEntries = processEntries(ledger, request.entries(), request.date());

        // Validate multi-currency balance
        validateMultiCurrencyBalance(processedEntries);

        // Separate debits and credits
        List<TransactionEntry> debitEntries = processedEntries.stream()
                .filter(e -> e.type() == TransactionEntryType.DEBIT)
                .toList();

        List<TransactionEntry> creditEntries = processedEntries.stream()
                .filter(e -> e.type() == TransactionEntryType.CREDIT)
                .toList();

        final var transaction = new Transaction(
                transactionId,
                request.date(),
                existing.createdAt(),
                request.description(),
                creditEntries,
                debitEntries
        );

        transactionRepository.update(ledger.id(), transaction);

        // Fetch the updated transaction to get account names populated
        return transactionRepository.findById(ledger.id(), transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Override
    @Transactional
    public void delete(String userId, String ledgerSlug, String transactionId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        transactionRepository.delete(ledger.id(), transactionId);
    }

    private List<TransactionEntry> processEntries(Ledger ledger, List<TransactionEntryRequest> requestEntries, java.time.LocalDate transactionDate) {
        // One batched lookup instead of one (expensive, balance-computing)
        // findById per entry - entries only need the account's currency and name.
        Set<String> accountIds = requestEntries.stream()
                .map(TransactionEntryRequest::accountId)
                .collect(Collectors.toSet());
        Map<String, AccountRef> accountsById = accountRepository.findRefsByIds(ledger.id(), accountIds).stream()
                .collect(Collectors.toMap(AccountRef::id, account -> account));

        List<TransactionEntry> entries = new ArrayList<>();

        for (var requestEntry : requestEntries) {
            AccountRef account = accountsById.get(requestEntry.accountId());
            if (account == null) {
                throw new IllegalArgumentException("Account not found: " + requestEntry.accountId());
            }

            MonetaryAmount originalAmount = new MonetaryAmount(requestEntry.amount(), requestEntry.currency());
            MonetaryAmount convertedAmount = null;

            // Check if currency conversion is needed
            if (!requestEntry.currency().equals(account.currency())) {
                // If toAmount and toCurrency are provided, use them
                if (requestEntry.toAmount() != null && requestEntry.toCurrency() != null) {
                    if (!requestEntry.toCurrency().equals(account.currency())) {
                        throw new IllegalArgumentException(
                                "Converted currency (" + requestEntry.toCurrency() +
                                        ") must match account currency (" + account.currency() + ") for account " + account.name()
                        );
                    }
                    convertedAmount = new MonetaryAmount(requestEntry.toAmount(), requestEntry.toCurrency());
                    log.debug("Using provided conversion: {} {} -> {} {}",
                        requestEntry.amount(), requestEntry.currency(),
                        requestEntry.toAmount(), requestEntry.toCurrency());
                } else {
                    // Auto-convert using exchange rate service
                    convertedAmount = exchangeRateService.convert(originalAmount, account.currency(), transactionDate)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Cannot convert " + requestEntry.currency() + " to " + account.currency() +
                                            " for account " + account.name() + " - no exchange rate available for date " + transactionDate
                            ));
                    log.debug("Auto-converted: {} {} -> {} {}",
                        originalAmount.amount(), originalAmount.currency(),
                        convertedAmount.amount(), convertedAmount.currency());
                }
            }

            entries.add(new TransactionEntry(
                    requestEntry.accountId(),
                    requestEntry.type(),
                    originalAmount,
                    convertedAmount,
                    account.name(),
                    requestEntry.instrumentId(),
                    requestEntry.quantity(),
                    null,  // instrumentSymbol will be populated by the repository on fetch
                    requestEntry.envelopeId()
            ));
        }

        return entries;
    }

    /**
     * Validates that transaction entries balance for each currency.
     * Groups entries by the ORIGINAL currency (entry.amount().currency()) and ensures
     * debits equal credits for each currency group.
     *
     * This allows multi-currency transactions where, for example:
     * - Debit 1067.93 BRL from USD account (converted to 191.88 USD)
     * - Credit 1067.93 BRL to BRL account
     * Both entries are in BRL (the transaction currency), so they balance.
     */
    private void validateMultiCurrencyBalance(List<TransactionEntry> entries) {
        // Group by ORIGINAL currency (the transaction currency, not the account currency)
        Map<String, List<TransactionEntry>> byCurrency = entries.stream()
                .collect(Collectors.groupingBy(entry -> entry.amount().currency()));

        for (Map.Entry<String, List<TransactionEntry>> currencyGroup : byCurrency.entrySet()) {
            String currency = currencyGroup.getKey();
            List<TransactionEntry> currencyEntries = currencyGroup.getValue();

            BigDecimal debits = BigDecimal.ZERO;
            BigDecimal credits = BigDecimal.ZERO;

            for (TransactionEntry entry : currencyEntries) {
                // Use the ORIGINAL amount (transaction currency), not the converted amount
                BigDecimal amount = entry.amount().amount();

                if (entry.type() == TransactionEntryType.DEBIT) {
                    debits = debits.add(amount);
                } else {
                    credits = credits.add(amount);
                }
            }

            if (debits.compareTo(credits) != 0) {
                throw new IllegalArgumentException(
                        "Transaction is not balanced for currency " + currency +
                                ": debits (" + debits + ") must equal credits (" + credits + ")"
                );
            }

            log.debug("Currency {} is balanced: debits = credits = {}", currency, debits);
        }
    }

    @Override
    public List<String> getDistinctDescriptions(String userId, String ledgerSlug) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.findDistinctDescriptions(ledger.id());
    }

    @Override
    public List<Transaction> getTransactionTemplates(String userId, String ledgerSlug) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.findTransactionTemplates(ledger.id());
    }

    @Override
    public MonthlyReport getMonthlyReport(String userId, String ledgerSlug, int year, int month) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.getMonthlyReport(ledger.id(), year, month);
    }

    @Override
    public MonthlyExpenseBreakdown getMonthlyExpenseBreakdown(String userId, String ledgerSlug, int year, int month) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.getMonthlyExpenseBreakdown(ledger.id(), year, month);
    }

    @Override
    public MonthlyIncomeBreakdown getMonthlyIncomeBreakdown(String userId, String ledgerSlug, int year, int month) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return transactionRepository.getMonthlyIncomeBreakdown(ledger.id(), year, month);
    }

}
