package com.rslima.ricash.ledgers;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerMapperTest {

    private final LedgerMapper mapper = Mappers.getMapper(LedgerMapper.class);

    @Test
    void toResource_mapsAllFields() {
        var now = Instant.now();
        var ledger = new Ledger(
                "ledger-id",
                "user-id",
                "test-ledger",
                "Test Ledger",
                "Test Description",
                "USD",
                now,
                List.of(),
                List.of()
        );

        var result = mapper.toResource(ledger);

        assertThat(result.getId()).isEqualTo("ledger-id");
        assertThat(result.getName()).isEqualTo("Test Ledger");
        assertThat(result.getDescription()).isEqualTo("Test Description");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toResource_mapsUserIdToUserResource() {
        var ledger = new Ledger(
                "ledger-id",
                "user-id",
                "test-ledger",
                "Test Ledger",
                "Test Description",
                "USD",
                Instant.now(),
                List.of(),
                List.of()
        );

        var result = mapper.toResource(ledger);

        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo("user-id");
    }

    @Test
    void toResource_withNullUserId_mapsToNullUserResource() {
        var ledger = new Ledger(
                "ledger-id",
                null,
                "test-ledger",
                "Test Ledger",
                "Test Description",
                "USD",
                Instant.now(),
                List.of(),
                List.of()
        );

        var result = mapper.toResource(ledger);

        assertThat(result.getUser()).isNull();
    }

    @Test
    void toResource_mapsAccounts() {
        var account = new Account(
                "account-id",
                "checking",
                "Checking",
                "Main checking",
                "USD",
                AccountType.ASSET,
                AccountStatus.ACTIVE,
                BigDecimal.ZERO,
                Instant.now(),
                null,
                List.of()
        );
        var ledger = new Ledger(
                "ledger-id",
                "user-id",
                "test-ledger",
                "Test Ledger",
                "Test Description",
                "USD",
                Instant.now(),
                List.of(account),
                List.of()
        );

        var result = mapper.toResource(ledger);

        assertThat(result.getAccounts()).hasSize(1);
        assertThat(result.getAccounts().getFirst().getId()).isEqualTo("account-id");
        assertThat(result.getAccounts().getFirst().getName()).isEqualTo("Checking");
    }

    @Test
    void toResource_mapsNestedAccounts() {
        var subAccount = new Account(
                "sub-account-id",
                "sub-account",
                "Sub Account",
                "Sub description",
                "USD",
                AccountType.ASSET,
                AccountStatus.ACTIVE,
                BigDecimal.ZERO,
                Instant.now(),
                "account-id",
                List.of()
        );
        var account = new Account(
                "account-id",
                "parent-account",
                "Parent Account",
                "Parent description",
                "USD",
                AccountType.ASSET,
                AccountStatus.ACTIVE,
                BigDecimal.ZERO,
                Instant.now(),
                null,
                List.of(subAccount)
        );
        var ledger = new Ledger(
                "ledger-id",
                "user-id",
                "test-ledger",
                "Test Ledger",
                "Test Description",
                "USD",
                Instant.now(),
                List.of(account),
                List.of()
        );

        var result = mapper.toResource(ledger);

        assertThat(result.getAccounts()).hasSize(1);
        assertThat(result.getAccounts().getFirst().getSubAccounts()).hasSize(1);
        assertThat(result.getAccounts().getFirst().getSubAccounts().getFirst().getName()).isEqualTo("Sub Account");
    }

    @Test
    void toAccountResource_mapsAllFields() {
        var now = Instant.now();
        var account = new Account(
                "account-id",
                "checking",
                "Checking",
                "Main checking account",
                "USD",
                AccountType.ASSET,
                AccountStatus.ACTIVE,
                BigDecimal.valueOf(100.50),
                now,
                null,
                List.of()
        );

        var result = mapper.toResource(account);

        assertThat(result.getId()).isEqualTo("account-id");
        assertThat(result.getName()).isEqualTo("Checking");
        assertThat(result.getDescription()).isEqualTo("Main checking account");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getType()).isEqualTo(AccountType.ASSET);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getBalance()).isEqualTo(BigDecimal.valueOf(100.50));
        assertThat(result.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toAccountResources_mapsListOfAccounts() {
        var account1 = new Account("id-1", "account-1", "Account 1", "Desc 1", "USD", AccountType.ASSET, AccountStatus.ACTIVE, BigDecimal.ZERO, Instant.now(), null, List.of());
        var account2 = new Account("id-2", "account-2", "Account 2", "Desc 2", "EUR", AccountType.LIABILITY, AccountStatus.INACTIVE, BigDecimal.ZERO, Instant.now(), null, List.of());

        var result = mapper.toAccountResources(List.of(account1, account2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Account 1");
        assertThat(result.get(1).getName()).isEqualTo("Account 2");
    }
}
