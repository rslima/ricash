package com.rslima.ricash.ledgers.accounts;

import com.rslima.ricash.ledgers.Ledger;
import com.rslima.ricash.ledgers.LedgerRepository;
import com.rslima.ricash.ledgers.SlugService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceBeanTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private SlugService slugService;

    private AccountServiceBean accountService;

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "ledger-id";
    private static final String LEDGER_SLUG = "test-ledger";
    private static final String ACCOUNT_ID = "account-id";

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceBean(accountRepository, ledgerRepository, slugService);
    }

    @Test
    void listLedgerAccounts_delegatesToRepository() {
        var pageRequest = PageRequest.of(0, 20);
        var account = createTestAccount();
        var page = new PageImpl<>(List.of(account), pageRequest, 1);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.listLedgerAccounts(LEDGER_ID, pageRequest)).thenReturn(page);

        var result = accountService.listLedgerAccounts(USER_ID, LEDGER_SLUG, pageRequest);

        assertThat(result.getContent()).hasSize(1);
        verify(accountRepository).listLedgerAccounts(LEDGER_ID, pageRequest);
    }

    @Test
    void find_delegatesToRepository() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));

        var result = accountService.find(USER_ID, LEDGER_SLUG, ACCOUNT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void create_generatesUUIDSlugAndDefaults() {
        var request = new CreateAccountRequest("Checking", "Main account", "USD", AccountType.ASSET, null);
        var captor = ArgumentCaptor.forClass(Account.class);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(slugService.slugify("Checking")).thenReturn("checking");
        when(accountRepository.existsBySlug(LEDGER_ID, "checking")).thenReturn(false);
        when(accountRepository.create(eq(LEDGER_ID), any(Account.class))).thenAnswer(inv -> inv.getArgument(1));

        var result = accountService.create(USER_ID, LEDGER_SLUG, request);

        verify(accountRepository).create(eq(LEDGER_ID), captor.capture());
        var captured = captor.getValue();
        assertThat(captured.id()).isNotNull().hasSize(36);
        assertThat(captured.slug()).isEqualTo("checking");
        assertThat(captured.name()).isEqualTo("Checking");
        assertThat(captured.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(captured.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void create_slugCollisionAppendsCounter() {
        var request = new CreateAccountRequest("Checking", null, "USD", AccountType.ASSET, null);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(slugService.slugify("Checking")).thenReturn("checking");
        when(accountRepository.existsBySlug(LEDGER_ID, "checking")).thenReturn(true);
        when(accountRepository.existsBySlug(LEDGER_ID, "checking-1")).thenReturn(false);
        when(accountRepository.create(eq(LEDGER_ID), any(Account.class))).thenAnswer(inv -> inv.getArgument(1));

        var result = accountService.create(USER_ID, LEDGER_SLUG, request);

        var captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).create(eq(LEDGER_ID), captor.capture());
        assertThat(captor.getValue().slug()).isEqualTo("checking-1");
    }

    @Test
    void update_existingAccount() {
        var request = new UpdateAccountRequest("Updated", "Desc", AccountType.ASSET, "USD", null);
        var updatedAccount = createTestAccount();

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.update(LEDGER_ID, ACCOUNT_ID, "Updated", "Desc", AccountType.ASSET, "USD", null))
                .thenReturn(updatedAccount);

        var result = accountService.update(USER_ID, LEDGER_SLUG, ACCOUNT_ID, request);

        assertThat(result).isNotNull();
    }

    @Test
    void update_notFound_throws() {
        var request = new UpdateAccountRequest("Updated", "Desc", AccountType.ASSET, "USD", null);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.update(USER_ID, LEDGER_SLUG, ACCOUNT_ID, request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void delete_leafAccount() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of());
        when(accountRepository.hasTransactions(ACCOUNT_ID)).thenReturn(false);

        accountService.delete(USER_ID, LEDGER_SLUG, ACCOUNT_ID);

        verify(accountRepository).delete(LEDGER_ID, ACCOUNT_ID);
    }

    @Test
    void delete_accountWithTransactions_throws() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of());
        when(accountRepository.hasTransactions(ACCOUNT_ID)).thenReturn(true);

        assertThatThrownBy(() -> accountService.delete(USER_ID, LEDGER_SLUG, ACCOUNT_ID))
                .isInstanceOf(AccountHasTransactionsException.class);
    }

    @Test
    void delete_parentWithChildren_deletesRecursively() {
        var childId = "child-id";

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of(childId));
        when(accountRepository.findChildAccountIds(LEDGER_ID, childId)).thenReturn(List.of());
        when(accountRepository.hasTransactions(ACCOUNT_ID)).thenReturn(false);
        when(accountRepository.hasTransactions(childId)).thenReturn(false);

        accountService.delete(USER_ID, LEDGER_SLUG, ACCOUNT_ID);

        // Children deleted first (reverse order)
        verify(accountRepository).delete(LEDGER_ID, childId);
        verify(accountRepository).delete(LEDGER_ID, ACCOUNT_ID);
    }

    @Test
    void delete_childHasTransactions_throws() {
        var childId = "child-id";

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of(childId));
        when(accountRepository.findChildAccountIds(LEDGER_ID, childId)).thenReturn(List.of());
        when(accountRepository.hasTransactions(ACCOUNT_ID)).thenReturn(false);
        when(accountRepository.hasTransactions(childId)).thenReturn(true);

        assertThatThrownBy(() -> accountService.delete(USER_ID, LEDGER_SLUG, ACCOUNT_ID))
                .isInstanceOf(AccountHasTransactionsException.class);
    }

    private Ledger createTestLedger() {
        return new Ledger(LEDGER_ID, USER_ID, LEDGER_SLUG, "Test Ledger", "Description", "USD", Instant.now(), List.of(), List.of());
    }

    private Account createTestAccount() {
        return new Account(ACCOUNT_ID, "checking", "Checking", "Main account", "USD", AccountType.ASSET, AccountStatus.ACTIVE, BigDecimal.ZERO, Instant.now(), null, List.of());
    }
}
