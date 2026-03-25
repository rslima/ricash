package com.rslima.ricash.ledgers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceBeanTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    private TransactionServiceBean transactionService;

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "ledger-id";
    private static final String LEDGER_SLUG = "test-ledger";
    private static final String TRANSACTION_ID = "txn-id";
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceBean(transactionRepository, ledgerRepository, accountRepository, exchangeRateService);
    }

    @Test
    void listLedgerTransactions_delegatesToRepository() {
        var pageRequest = PageRequest.of(0, 20);
        var transaction = createTestTransaction();
        var page = new PageImpl<>(List.of(transaction), pageRequest, 1);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(transactionRepository.listLedgerTransactions(LEDGER_ID, pageRequest)).thenReturn(page);

        var result = transactionService.listLedgerTransactions(USER_ID, LEDGER_SLUG, pageRequest);

        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository).listLedgerTransactions(LEDGER_ID, pageRequest);
    }

    @Test
    void listAccountTransactions_delegatesToRepository() {
        var pageRequest = PageRequest.of(0, 20);
        var accountId = "account-1";
        var transaction = createTestTransaction();
        var page = new PageImpl<>(List.of(transaction), pageRequest, 1);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(transactionRepository.listAccountTransactions(LEDGER_ID, accountId, pageRequest)).thenReturn(page);

        var result = transactionService.listAccountTransactions(USER_ID, LEDGER_SLUG, accountId, pageRequest);

        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository).listAccountTransactions(LEDGER_ID, accountId, pageRequest);
    }

    @Test
    void find_delegatesToRepository() {
        var transaction = createTestTransaction();

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(transactionRepository.findById(LEDGER_ID, TRANSACTION_ID)).thenReturn(Optional.of(transaction));

        var result = transactionService.find(USER_ID, LEDGER_SLUG, TRANSACTION_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(TRANSACTION_ID);
    }

    @Test
    void delete_delegatesToRepository() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));

        transactionService.delete(USER_ID, LEDGER_SLUG, TRANSACTION_ID);

        verify(transactionRepository).delete(LEDGER_ID, TRANSACTION_ID);
    }

    @Test
    void getDistinctDescriptions_delegatesToRepository() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(transactionRepository.findDistinctDescriptions(LEDGER_ID)).thenReturn(List.of("Groceries", "Rent"));

        var result = transactionService.getDistinctDescriptions(USER_ID, LEDGER_SLUG);

        assertThat(result).containsExactly("Groceries", "Rent");
    }

    @Test
    void getTransactionTemplates_delegatesToRepository() {
        var template = createTestTransaction();
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(transactionRepository.findTransactionTemplates(LEDGER_ID)).thenReturn(List.of(template));

        var result = transactionService.getTransactionTemplates(USER_ID, LEDGER_SLUG);

        assertThat(result).hasSize(1);
    }

    @Test
    void create_singleCurrencyBalancedTransaction() {
        var account = createTestAccount("acc-1", "Checking", "USD");
        var request = new CreateTransactionRequest(DATE, "Groceries", List.of(
                new CreateTransactionRequest.EntryRequest("acc-1", BigDecimal.TEN, "USD", null, null, TransactionEntryType.DEBIT, null, null, null),
                new CreateTransactionRequest.EntryRequest("acc-1", BigDecimal.TEN, "USD", null, null, TransactionEntryType.CREDIT, null, null, null)
        ));

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(eq(LEDGER_ID), eq("acc-1"))).thenReturn(Optional.of(account));
        when(transactionRepository.findById(eq(LEDGER_ID), any())).thenReturn(Optional.of(createTestTransaction()));

        var result = transactionService.create(USER_ID, LEDGER_SLUG, request);

        assertThat(result).isNotNull();
        verify(transactionRepository).create(eq(LEDGER_ID), any(Transaction.class));
    }

    @Test
    void create_multiCurrencyWithExplicitConversion() {
        var usdAccount = createTestAccount("acc-usd", "USD Account", "USD");
        var brlAccount = createTestAccount("acc-brl", "BRL Account", "BRL");

        var request = new CreateTransactionRequest(DATE, "Transfer", List.of(
                new CreateTransactionRequest.EntryRequest("acc-usd", new BigDecimal("1000"), "BRL", new BigDecimal("180"), "USD", TransactionEntryType.DEBIT, null, null, null),
                new CreateTransactionRequest.EntryRequest("acc-brl", new BigDecimal("1000"), "BRL", null, null, TransactionEntryType.CREDIT, null, null, null)
        ));

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, "acc-usd")).thenReturn(Optional.of(usdAccount));
        when(accountRepository.findById(LEDGER_ID, "acc-brl")).thenReturn(Optional.of(brlAccount));
        when(transactionRepository.findById(eq(LEDGER_ID), any())).thenReturn(Optional.of(createTestTransaction()));

        var result = transactionService.create(USER_ID, LEDGER_SLUG, request);

        assertThat(result).isNotNull();
        verify(transactionRepository).create(eq(LEDGER_ID), any(Transaction.class));
    }

    @Test
    void create_multiCurrencyWithAutoConversion() {
        var usdAccount = createTestAccount("acc-usd", "USD Account", "USD");
        var brlAccount = createTestAccount("acc-brl", "BRL Account", "BRL");

        var request = new CreateTransactionRequest(DATE, "Transfer", List.of(
                new CreateTransactionRequest.EntryRequest("acc-usd", new BigDecimal("1000"), "BRL", null, null, TransactionEntryType.DEBIT, null, null, null),
                new CreateTransactionRequest.EntryRequest("acc-brl", new BigDecimal("1000"), "BRL", null, null, TransactionEntryType.CREDIT, null, null, null)
        ));

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, "acc-usd")).thenReturn(Optional.of(usdAccount));
        when(accountRepository.findById(LEDGER_ID, "acc-brl")).thenReturn(Optional.of(brlAccount));
        when(exchangeRateService.convert(any(MonetaryAmount.class), eq("USD"), eq(DATE)))
                .thenReturn(Optional.of(new MonetaryAmount(new BigDecimal("180.00"), "USD")));
        when(transactionRepository.findById(eq(LEDGER_ID), any())).thenReturn(Optional.of(createTestTransaction()));

        var result = transactionService.create(USER_ID, LEDGER_SLUG, request);

        assertThat(result).isNotNull();
        verify(exchangeRateService).convert(any(MonetaryAmount.class), eq("USD"), eq(DATE));
    }

    @Test
    void create_autoConversionNoRateAvailable_throws() {
        var usdAccount = createTestAccount("acc-usd", "USD Account", "USD");
        var brlAccount = createTestAccount("acc-brl", "BRL Account", "BRL");

        var request = new CreateTransactionRequest(DATE, "Transfer", List.of(
                new CreateTransactionRequest.EntryRequest("acc-usd", new BigDecimal("1000"), "BRL", null, null, TransactionEntryType.DEBIT, null, null, null),
                new CreateTransactionRequest.EntryRequest("acc-brl", new BigDecimal("1000"), "BRL", null, null, TransactionEntryType.CREDIT, null, null, null)
        ));

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, "acc-usd")).thenReturn(Optional.of(usdAccount));
        when(exchangeRateService.convert(any(MonetaryAmount.class), eq("USD"), eq(DATE)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(USER_ID, LEDGER_SLUG, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no exchange rate available");
    }

    @Test
    void create_toCurrencyMismatch_throws() {
        var usdAccount = createTestAccount("acc-usd", "USD Account", "USD");

        var request = new CreateTransactionRequest(DATE, "Transfer", List.of(
                new CreateTransactionRequest.EntryRequest("acc-usd", new BigDecimal("1000"), "BRL", new BigDecimal("180"), "EUR", TransactionEntryType.DEBIT, null, null, null),
                new CreateTransactionRequest.EntryRequest("acc-usd", new BigDecimal("1000"), "BRL", null, null, TransactionEntryType.CREDIT, null, null, null)
        ));

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, "acc-usd")).thenReturn(Optional.of(usdAccount));

        assertThatThrownBy(() -> transactionService.create(USER_ID, LEDGER_SLUG, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match account currency");
    }

    @Test
    void create_unbalancedEntries_throws() {
        var account = createTestAccount("acc-1", "Checking", "USD");

        var request = new CreateTransactionRequest(DATE, "Bad", List.of(
                new CreateTransactionRequest.EntryRequest("acc-1", BigDecimal.TEN, "USD", null, null, TransactionEntryType.DEBIT, null, null, null),
                new CreateTransactionRequest.EntryRequest("acc-1", BigDecimal.ONE, "USD", null, null, TransactionEntryType.CREDIT, null, null, null)
        ));

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(eq(LEDGER_ID), eq("acc-1"))).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> transactionService.create(USER_ID, LEDGER_SLUG, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not balanced");
    }

    @Test
    void update_existingTransaction() {
        var account = createTestAccount("acc-1", "Checking", "USD");
        var existing = createTestTransaction();
        var request = new UpdateTransactionRequest(DATE, "Updated", List.of(
                new UpdateTransactionRequest.EntryRequest("acc-1", BigDecimal.TEN, "USD", null, null, TransactionEntryType.DEBIT, null, null, null),
                new UpdateTransactionRequest.EntryRequest("acc-1", BigDecimal.TEN, "USD", null, null, TransactionEntryType.CREDIT, null, null, null)
        ));

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(transactionRepository.findById(LEDGER_ID, TRANSACTION_ID)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(eq(LEDGER_ID), eq("acc-1"))).thenReturn(Optional.of(account));

        var result = transactionService.update(USER_ID, LEDGER_SLUG, TRANSACTION_ID, request);

        assertThat(result).isNotNull();
        verify(transactionRepository).update(eq(LEDGER_ID), any(Transaction.class));
    }

    @Test
    void update_notFound_throws() {
        var request = new UpdateTransactionRequest(DATE, "Updated", List.of(
                new UpdateTransactionRequest.EntryRequest("acc-1", BigDecimal.TEN, "USD", null, null, TransactionEntryType.DEBIT, null, null, null)
        ));

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(transactionRepository.findById(LEDGER_ID, TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(USER_ID, LEDGER_SLUG, TRANSACTION_ID, request))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void ledgerNotFound_throws() {
        var pageRequest = PageRequest.of(0, 20);
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.listLedgerTransactions(USER_ID, LEDGER_SLUG, pageRequest))
                .isInstanceOf(LedgerNotFoundException.class);
    }

    private Ledger createTestLedger() {
        return new Ledger(LEDGER_ID, USER_ID, LEDGER_SLUG, "Test Ledger", "Description", "USD", Instant.now(), List.of(), List.of());
    }

    private Transaction createTestTransaction() {
        var debit = new TransactionEntry("acc-1", TransactionEntryType.DEBIT, new MonetaryAmount(BigDecimal.TEN, "USD"), null, "Checking");
        var credit = new TransactionEntry("acc-2", TransactionEntryType.CREDIT, new MonetaryAmount(BigDecimal.TEN, "USD"), null, "Savings");
        return new Transaction(TRANSACTION_ID, DATE, Instant.now(), "Test Transaction", List.of(credit), List.of(debit));
    }

    private Account createTestAccount(String id, String name, String currency) {
        return new Account(id, name.toLowerCase().replace(" ", "-"), name, null, currency, AccountType.ASSET, AccountStatus.ACTIVE, BigDecimal.ZERO, Instant.now(), null, List.of());
    }
}
