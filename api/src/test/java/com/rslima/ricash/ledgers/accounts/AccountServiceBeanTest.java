package com.rslima.ricash.ledgers.accounts;

import com.rslima.ricash.ledgers.LedgerRef;
import com.rslima.ricash.ledgers.LedgerAccess;
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
import static org.mockito.Mockito.never;
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
        accountService = new AccountServiceBean(accountRepository, new LedgerAccess(ledgerRepository), slugService);
    }

    @Test
    void listLedgerAccounts_delegatesToRepository() {
        var pageRequest = PageRequest.of(0, 20);
        var account = createTestAccount();
        var page = new PageImpl<>(List.of(account), pageRequest, 1);

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.listLedgerAccounts(LEDGER_ID, pageRequest)).thenReturn(page);

        var result = accountService.listLedgerAccounts(USER_ID, LEDGER_SLUG, pageRequest);

        assertThat(result.getContent()).hasSize(1);
        verify(accountRepository).listLedgerAccounts(LEDGER_ID, pageRequest);
    }

    @Test
    void find_delegatesToRepository() {
        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));

        var result = accountService.find(USER_ID, LEDGER_SLUG, ACCOUNT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void create_generatesUUIDSlugAndDefaults() {
        var request = new CreateAccountRequest("Checking", "Main account", "USD", AccountType.ASSET, null);
        var captor = ArgumentCaptor.forClass(Account.class);

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
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

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
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

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.update(LEDGER_ID, ACCOUNT_ID, "Updated", "Desc", AccountType.ASSET, "USD", null))
                .thenReturn(updatedAccount);

        var result = accountService.update(USER_ID, LEDGER_SLUG, ACCOUNT_ID, request);

        assertThat(result).isNotNull();
    }

    @Test
    void update_notFound_throws() {
        var request = new UpdateAccountRequest("Updated", "Desc", AccountType.ASSET, "USD", null);

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.update(USER_ID, LEDGER_SLUG, ACCOUNT_ID, request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void create_parentInLedger_isAccepted() {
        var request = new CreateAccountRequest("Groceries", null, "USD", AccountType.EXPENSE, "parent-id");

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findRefsByIds(LEDGER_ID, List.of("parent-id")))
                .thenReturn(List.of(new AccountRef("parent-id", "Food", "USD")));
        when(slugService.slugify("Groceries")).thenReturn("groceries");
        when(accountRepository.create(eq(LEDGER_ID), any(Account.class))).thenAnswer(inv -> inv.getArgument(1));

        var result = accountService.create(USER_ID, LEDGER_SLUG, request);

        assertThat(result.parentAccountId()).isEqualTo("parent-id");
    }

    @Test
    void create_parentOutsideLedger_throwsWithoutWriting() {
        var request = new CreateAccountRequest("Groceries", null, "USD", AccountType.EXPENSE, "foreign-id");

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findRefsByIds(LEDGER_ID, List.of("foreign-id"))).thenReturn(List.of());

        assertThatThrownBy(() -> accountService.create(USER_ID, LEDGER_SLUG, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parent account not found: foreign-id");
        verify(accountRepository, never()).create(any(), any());
    }

    @Test
    void update_selfAsParent_throwsWithoutWriting() {
        var request = new UpdateAccountRequest("Checking", null, AccountType.ASSET, "USD", ACCOUNT_ID);

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findRefsByIds(LEDGER_ID, List.of(ACCOUNT_ID)))
                .thenReturn(List.of(new AccountRef(ACCOUNT_ID, "Checking", "USD")));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> accountService.update(USER_ID, LEDGER_SLUG, ACCOUNT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be its own parent");
        verify(accountRepository, never()).update(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_descendantAsParent_throwsWithoutWriting() {
        var request = new UpdateAccountRequest("Checking", null, AccountType.ASSET, "USD", "grandchild-id");

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findRefsByIds(LEDGER_ID, List.of("grandchild-id")))
                .thenReturn(List.of(new AccountRef("grandchild-id", "Leaf", "USD")));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of("child-id"));
        when(accountRepository.findChildAccountIds(LEDGER_ID, "child-id")).thenReturn(List.of("grandchild-id"));
        when(accountRepository.findChildAccountIds(LEDGER_ID, "grandchild-id")).thenReturn(List.of());

        assertThatThrownBy(() -> accountService.update(USER_ID, LEDGER_SLUG, ACCOUNT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descendants");
        verify(accountRepository, never()).update(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void delete_storedCycle_terminates() {
        // Data written before V15 / the parent checks could hold a cycle; the
        // subtree walk must still finish instead of recursing forever.
        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of("child-id"));
        when(accountRepository.findChildAccountIds(LEDGER_ID, "child-id")).thenReturn(List.of(ACCOUNT_ID));

        accountService.delete(USER_ID, LEDGER_SLUG, ACCOUNT_ID);

        verify(accountRepository).delete(LEDGER_ID, "child-id");
        verify(accountRepository).delete(LEDGER_ID, ACCOUNT_ID);
    }

    @Test
    void delete_leafAccount() {
        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of());
        when(accountRepository.hasTransactions(ACCOUNT_ID)).thenReturn(false);

        accountService.delete(USER_ID, LEDGER_SLUG, ACCOUNT_ID);

        verify(accountRepository).delete(LEDGER_ID, ACCOUNT_ID);
    }

    @Test
    void delete_accountWithTransactions_throws() {
        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of());
        when(accountRepository.hasTransactions(ACCOUNT_ID)).thenReturn(true);

        assertThatThrownBy(() -> accountService.delete(USER_ID, LEDGER_SLUG, ACCOUNT_ID))
                .isInstanceOf(AccountHasTransactionsException.class);
    }

    @Test
    void delete_parentWithChildren_deletesRecursively() {
        var childId = "child-id";

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
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

        when(ledgerRepository.findRefBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(accountRepository.findById(LEDGER_ID, ACCOUNT_ID)).thenReturn(Optional.of(createTestAccount()));
        when(accountRepository.findChildAccountIds(LEDGER_ID, ACCOUNT_ID)).thenReturn(List.of(childId));
        when(accountRepository.findChildAccountIds(LEDGER_ID, childId)).thenReturn(List.of());
        when(accountRepository.hasTransactions(ACCOUNT_ID)).thenReturn(false);
        when(accountRepository.hasTransactions(childId)).thenReturn(true);

        assertThatThrownBy(() -> accountService.delete(USER_ID, LEDGER_SLUG, ACCOUNT_ID))
                .isInstanceOf(AccountHasTransactionsException.class);
    }

    private LedgerRef createTestLedger() {
        return new LedgerRef(LEDGER_ID, LEDGER_SLUG, "USD");
    }

    private Account createTestAccount() {
        return new Account(ACCOUNT_ID, "checking", "Checking", "Main account", "USD", AccountType.ASSET, AccountStatus.ACTIVE, BigDecimal.ZERO, Instant.now(), null, List.of());
    }
}
