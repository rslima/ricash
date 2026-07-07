package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.ledgers.LedgerNotFoundException;
import com.rslima.ricash.ledgers.LedgerRepository;
import com.rslima.ricash.ledgers.MonetaryAmount;
import com.rslima.ricash.ledgers.accounts.Account;
import com.rslima.ricash.ledgers.accounts.AccountRepository;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateService;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class TransactionServiceBean implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final LedgerRepository ledgerRepository;
    private final AccountRepository accountRepository;
    private final ExchangeRateService exchangeRateService;

    @Override
    public Page<Transaction> listLedgerTransactions(String userId, String ledgerSlug, PageRequest pageRequest) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.listLedgerTransactions(ledgerId, pageRequest);
    }

    @Override
    public Page<Transaction> searchByDescription(String userId, String ledgerSlug, String description, PageRequest pageRequest) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.searchByDescription(ledgerId, description, pageRequest);
    }

    @Override
    public Page<Transaction> listAccountTransactions(String userId, String ledgerSlug, String accountId, PageRequest pageRequest) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.listAccountTransactions(ledgerId, accountId, pageRequest);
    }

    @Override
    public Page<Transaction> listCategoryTransactions(String userId, String ledgerSlug, String accountId, int year, int month, PageRequest pageRequest) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.listCategoryTransactions(ledgerId, accountId, year, month, pageRequest);
    }

    @Override
    public Page<CategoryTransaction> listCategoryTransactionAmounts(String userId, String ledgerSlug, String accountId, int year, int month, PageRequest pageRequest) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.listCategoryTransactionAmounts(ledgerId, accountId, year, month, pageRequest);
    }

    @Override
    public Optional<Transaction> find(String userId, String ledgerSlug, String transactionId) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.findById(ledgerId, transactionId);
    }

    @Override
    @Transactional
    public Transaction create(String userId, String ledgerSlug, CreateTransactionRequest request) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);

        // Process entries with currency conversions
        List<TransactionEntry> processedEntries = processEntries(ledgerId, request.entries(), request.date());

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

        transactionRepository.create(ledgerId, transaction);

        // Fetch the created transaction to get account names populated
        return transactionRepository.findById(ledgerId, transaction.id())
                .orElseThrow(() -> new TransactionNotFoundException(transaction.id()));
    }

    @Override
    @Transactional
    public Transaction update(String userId, String ledgerSlug, String transactionId, UpdateTransactionRequest request) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);

        // Verify transaction exists
        final var existing = transactionRepository.findById(ledgerId, transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        // Process entries with currency conversions
        List<TransactionEntry> processedEntries = processEntries(ledgerId, request.entries(), request.date());

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

        transactionRepository.update(ledgerId, transaction);

        // Fetch the updated transaction to get account names populated
        return transactionRepository.findById(ledgerId, transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Override
    @Transactional
    public void delete(String userId, String ledgerSlug, String transactionId) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        transactionRepository.delete(ledgerId, transactionId);
    }

    private List<TransactionEntry> processEntries(String ledgerId, List<? extends TransactionEntryRequest> requestEntries, java.time.LocalDate transactionDate) {
        List<TransactionEntry> entries = new ArrayList<>();

        for (var requestEntry : requestEntries) {
            // Fetch account to get its currency
            Account account = accountRepository.findById(ledgerId, requestEntry.accountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + requestEntry.accountId()));

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
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.findDistinctDescriptions(ledgerId);
    }

    @Override
    public List<Transaction> getTransactionTemplates(String userId, String ledgerSlug) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.findTransactionTemplates(ledgerId);
    }

    @Override
    public MonthlyReport getMonthlyReport(String userId, String ledgerSlug, int year, int month) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.getMonthlyReport(ledgerId, year, month);
    }

    @Override
    public MonthlyExpenseBreakdown getMonthlyExpenseBreakdown(String userId, String ledgerSlug, int year, int month) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.getMonthlyExpenseBreakdown(ledgerId, year, month);
    }

    @Override
    public MonthlyIncomeBreakdown getMonthlyIncomeBreakdown(String userId, String ledgerSlug, int year, int month) {
        final var ledgerId = getLedgerId(userId, ledgerSlug);
        return transactionRepository.getMonthlyIncomeBreakdown(ledgerId, year, month);
    }

    private String getLedgerId(String userId, String ledgerSlug) {
        return ledgerRepository.findIdBySlug(userId, ledgerSlug)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerSlug));
    }
}
