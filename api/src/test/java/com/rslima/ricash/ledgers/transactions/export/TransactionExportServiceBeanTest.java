package com.rslima.ricash.ledgers.transactions.export;

import com.rslima.ricash.ledgers.Ledger;
import com.rslima.ricash.ledgers.LedgerAccess;
import com.rslima.ricash.ledgers.LedgerNotFoundException;
import com.rslima.ricash.ledgers.LedgerRepository;
import com.rslima.ricash.ledgers.accounts.Account;
import com.rslima.ricash.ledgers.accounts.AccountNotFoundException;
import com.rslima.ricash.ledgers.accounts.AccountStatus;
import com.rslima.ricash.ledgers.accounts.AccountType;
import com.rslima.ricash.ledgers.transactions.TransactionEntryType;
import com.rslima.ricash.ledgers.transactions.TransactionExportRow;
import com.rslima.ricash.ledgers.transactions.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionExportServiceBeanTest {

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "ledger-id";
    private static final String LEDGER_SLUG = "test-ledger";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    private TransactionExportServiceBean exportService;

    @BeforeEach
    void setUp() {
        exportService = new TransactionExportServiceBean(transactionRepository, new LedgerAccess(ledgerRepository));
    }

    @Test
    void export_unknownLedger_throwsLedgerNotFound() {
        when(ledgerRepository.findBySlug(USER_ID, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportService.export(USER_ID, "missing", csvRequest(null, false, null, null)))
                .isInstanceOf(LedgerNotFoundException.class);
    }

    @Test
    void export_unknownAccount_throwsAccountNotFound() {
        givenLedgerWithAccounts(checking());

        assertThatThrownBy(() -> exportService.export(USER_ID, LEDGER_SLUG,
                csvRequest("ghost", false, null, null)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void export_invalidFormat_throwsIllegalArgument() {
        assertThatThrownBy(() -> exportService.export(USER_ID, LEDGER_SLUG,
                new ExportRequest("xml", null, false, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xml");
    }

    @Test
    void export_fromAfterTo_throwsIllegalArgument() {
        assertThatThrownBy(() -> exportService.export(USER_ID, LEDGER_SLUG,
                csvRequest(null, false, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    void export_ofxForExpenseAccount_throwsIllegalArgument() {
        var groceries = account("groceries", "Groceries", AccountType.EXPENSE, List.of());
        givenLedgerWithAccounts(groceries);
        when(transactionRepository.listEntriesForExport(eq(LEDGER_ID), anyCollection(), isNull(), isNull()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> exportService.export(USER_ID, LEDGER_SLUG,
                new ExportRequest("ofx", "groceries", false, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ASSET and LIABILITY");
    }

    @Test
    void export_convertsInclusiveToBoundToExclusive() {
        givenLedgerWithAccounts(checking());
        when(transactionRepository.listEntriesForExport(any(), any(), any(), any())).thenReturn(List.of());

        exportService.export(USER_ID, LEDGER_SLUG,
                csvRequest(null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));

        verify(transactionRepository).listEntriesForExport(
                LEDGER_ID, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1));
    }

    @Test
    void export_singleAccountScope_passesOnlyThatAccount() {
        givenLedgerWithAccounts(checking());
        when(transactionRepository.listEntriesForExport(any(), any(), any(), any())).thenReturn(List.of());

        exportService.export(USER_ID, LEDGER_SLUG, csvRequest("checking", false, null, null));

        verify(transactionRepository).listEntriesForExport(LEDGER_ID, List.of("checking"), null, null);
    }

    @Test
    void export_includeChildren_passesWholeSubtree() {
        givenLedgerWithAccounts(checking());
        when(transactionRepository.listEntriesForExport(any(), any(), any(), any())).thenReturn(List.of());

        exportService.export(USER_ID, LEDGER_SLUG, csvRequest("checking", true, null, null));

        verify(transactionRepository).listEntriesForExport(
                LEDGER_ID, List.of("checking", "savings"), null, null);
    }

    @Test
    void export_csv_returnsCsvFileWithoutBalanceLookup() {
        givenLedgerWithAccounts(checking());
        when(transactionRepository.listEntriesForExport(any(), any(), any(), any())).thenReturn(List.of());

        var file = exportService.export(USER_ID, LEDGER_SLUG, csvRequest(null, false, null, null));

        assertThat(file.contentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(new String(file.content(), StandardCharsets.UTF_8)).contains("transaction_id,entry_id");
        verify(transactionRepository, never()).getAccountBalancesAsOf(any(), anyCollection(), any());
    }

    @Test
    void export_ofx_fetchesBalancesForStatementAccounts() {
        givenLedgerWithAccounts(checking());
        when(transactionRepository.listEntriesForExport(any(), any(), any(), any())).thenReturn(List.of());
        when(transactionRepository.getAccountBalancesAsOf(any(), anyCollection(), any()))
                .thenReturn(Map.of("checking", new BigDecimal("10.00")));

        var file = exportService.export(USER_ID, LEDGER_SLUG,
                new ExportRequest("OFX", "checking", true, null, LocalDate.of(2026, 6, 30)));

        assertThat(file.contentType()).isEqualTo("application/x-ofx");
        assertThat(new String(file.content(), StandardCharsets.UTF_8)).contains("OFXHEADER:100");
        verify(transactionRepository).getAccountBalancesAsOf(
                LEDGER_ID, List.of("checking", "savings"), LocalDate.of(2026, 7, 1));
    }

    @Test
    void export_yearBeyond9999_throwsIllegalArgument() {
        assertThatThrownBy(() -> exportService.export(USER_ID, LEDGER_SLUG,
                csvRequest(null, false, null, LocalDate.MAX)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("year");
    }

    @Test
    void export_ledgerWideOfx_emitsStatementsOnlyForActiveAssetAndLiabilityAccounts() {
        var card = account("card", "Card", AccountType.LIABILITY, List.of());
        var groceries = account("groceries", "Groceries", AccountType.EXPENSE, List.of());
        givenLedgerWithAccounts(checking(), card, groceries);
        // Activity on the liability and an expense account; checking stays idle.
        when(transactionRepository.listEntriesForExport(any(), any(), any(), any())).thenReturn(List.of(
                exportRow("e1", "card", TransactionEntryType.DEBIT),
                exportRow("e2", "groceries", TransactionEntryType.CREDIT)));
        when(transactionRepository.getAccountBalancesAsOf(any(), anyCollection(), any())).thenReturn(Map.of());

        var file = exportService.export(USER_ID, LEDGER_SLUG,
                new ExportRequest("ofx", null, false, null, null));

        var ofx = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(ofx).contains("<ACCTID>card");
        assertThat(ofx).doesNotContain("<ACCTID>checking");
        assertThat(ofx).doesNotContain("<ACCTID>groceries");
        verify(transactionRepository).getAccountBalancesAsOf(LEDGER_ID, List.of("card"), null);
    }

    @Test
    void export_buildsFilenamesFromScopeAndRange() {
        givenLedgerWithAccounts(checking());
        when(transactionRepository.listEntriesForExport(any(), any(), any(), any())).thenReturn(List.of());

        var all = exportService.export(USER_ID, LEDGER_SLUG, csvRequest(null, false, null, null));
        var range = exportService.export(USER_ID, LEDGER_SLUG,
                csvRequest(null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        var openEnded = exportService.export(USER_ID, LEDGER_SLUG,
                csvRequest(null, false, LocalDate.of(2026, 1, 1), null));
        var scoped = exportService.export(USER_ID, LEDGER_SLUG, csvRequest("checking", false, null, null));

        assertThat(all.filename()).isEqualTo("test-ledger-transactions-all.csv");
        assertThat(range.filename()).isEqualTo("test-ledger-transactions-2026-01-01_2026-06-30.csv");
        assertThat(openEnded.filename()).isEqualTo("test-ledger-transactions-2026-01-01_end.csv");
        assertThat(scoped.filename()).isEqualTo("test-ledger-checking-transactions-all.csv");
    }

    private void givenLedgerWithAccounts(Account... rootAccounts) {
        var ledger = new Ledger(LEDGER_ID, USER_ID, LEDGER_SLUG, "Test Ledger", null, "USD",
                Instant.now(), List.of(rootAccounts));
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(ledger));
    }

    /** Checking (ASSET) with one child, Savings (ASSET). */
    private static Account checking() {
        var savings = account("savings", "Savings", AccountType.ASSET, List.of());
        return account("checking", "Checking", AccountType.ASSET, List.of(savings));
    }

    private static Account account(String id, String name, AccountType type, List<Account> subAccounts) {
        return new Account(id, id, name, null, "USD", type, AccountStatus.ACTIVE,
                BigDecimal.ZERO, Instant.now(), null, subAccounts);
    }

    private static TransactionExportRow exportRow(String entryId, String accountId, TransactionEntryType type) {
        return new TransactionExportRow(
                "t-" + entryId, LocalDate.of(2026, 1, 10), Instant.EPOCH, "Entry " + entryId,
                entryId, accountId, "Account " + accountId, type,
                new BigDecimal("10.00"), "USD", null, null,
                null, null, null, null);
    }

    private static ExportRequest csvRequest(String accountId, boolean includeChildren,
                                            LocalDate from, LocalDate to) {
        return new ExportRequest("csv", accountId, includeChildren, from, to);
    }
}
