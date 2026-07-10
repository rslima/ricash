package com.rslima.ricash.ledgers;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRicashApplication.class)
@Transactional
class LedgerJdbcRepositoryTest {

    @Autowired
    private JdbcClient jdbcClient;

    private LedgerJdbcRepository repository;
    private DbFixtures fixtures;

    @BeforeEach
    void setUp() {
        repository = new LedgerJdbcRepository(jdbcClient);
        fixtures = new DbFixtures(jdbcClient);

        fixtures.cleanAll();
        fixtures.insertUser("user-1");
        fixtures.insertUser("user-2");
    }

    @Test
    void create_insertsLedgerIntoDatabase() {
        var ledger = new Ledger(
                "ledger-1",
                "user-1",
                "my-ledger",
                "My Ledger",
                "My Description",
                "USD",
                Instant.now(),
                List.of()
        );

        var result = repository.create(ledger);

        assertThat(result).isEqualTo(ledger);

        var count = jdbcClient.sql("SELECT COUNT(*) FROM ledgers WHERE id = :id")
                .param("id", "ledger-1")
                .query(Long.class)
                .single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void create_withNullDescription_insertsLedger() {
        var ledger = new Ledger(
                "ledger-2",
                "user-1",
                "my-ledger",
                "My Ledger",
                null,
                "EUR",
                Instant.now(),
                List.of()
        );

        var result = repository.create(ledger);

        assertThat(result.description()).isNull();
    }

    @Test
    void findBySlug_returnsLedger() {
        fixtures.insertLedger("ledger-1", "user-1", "Test Ledger", "Description", "USD");

        var result = repository.findBySlug("user-1", "test-ledger");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("ledger-1");
        assertThat(result.get().userId()).isEqualTo("user-1");
        assertThat(result.get().name()).isEqualTo("Test Ledger");
        assertThat(result.get().description()).isEqualTo("Description");
        assertThat(result.get().currency()).isEqualTo("USD");
    }

    @Test
    void findBySlug_withAccounts_returnsLedgerWithAccounts() {
        fixtures.insertLedger("ledger-1", "user-1", "Test Ledger", "Description", "USD");
        fixtures.insertAccount("account-1", "ledger-1", null, "Checking", "Main checking", "USD", "ASSET", "ACTIVE");
        fixtures.insertAccount("account-2", "ledger-1", null, "Savings", "Main savings", "USD", "ASSET", "ACTIVE");

        var result = repository.findBySlug("user-1", "test-ledger");

        assertThat(result).isPresent();
        assertThat(result.get().accounts()).hasSize(2);
    }

    @Test
    void findBySlug_withNestedAccounts_returnsAccountTree() {
        fixtures.insertLedger("ledger-1", "user-1", "Test Ledger", "Description", "USD");
        fixtures.insertAccount("account-1", "ledger-1", null, "Assets", "All assets", "USD", "ASSET", "ACTIVE");
        fixtures.insertAccount("account-2", "ledger-1", "account-1", "Checking", "Main checking", "USD", "ASSET", "ACTIVE");

        var result = repository.findBySlug("user-1", "test-ledger");

        assertThat(result).isPresent();
        assertThat(result.get().accounts()).hasSize(1);
        assertThat(result.get().accounts().getFirst().name()).isEqualTo("Assets");
        assertThat(result.get().accounts().getFirst().subAccounts()).hasSize(1);
        assertThat(result.get().accounts().getFirst().subAccounts().getFirst().name()).isEqualTo("Checking");
    }

    @Test
    void findBySlug_wrongUser_returnsEmpty() {
        fixtures.insertLedger("ledger-1", "user-1", "Test Ledger", "Description", "USD");

        var result = repository.findBySlug("user-2", "test-ledger");

        assertThat(result).isEmpty();
    }

    @Test
    void findBySlug_notFound_returnsEmpty() {
        var result = repository.findBySlug("user-1", "nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void listUserLedgers_returnsUserLedgers() {
        fixtures.insertLedger("ledger-1", "user-1", "Ledger 1", "Description 1", "USD");
        fixtures.insertLedger("ledger-2", "user-1", "Ledger 2", "Description 2", "EUR");
        fixtures.insertLedger("ledger-3", "user-2", "Other Ledger", "Other Description", "GBP");

        var result = repository.listUserLedgers("user-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Ledger::name)
                .containsExactlyInAnyOrder("Ledger 1", "Ledger 2");
    }

    @Test
    void listUserLedgers_withPagination_returnsCorrectPage() {
        for (int i = 1; i <= 5; i++) {
            fixtures.insertLedger("ledger-" + i, "user-1", "Ledger " + i, "Description " + i, "USD");
        }

        var result = repository.listUserLedgers("user-1", PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void listUserLedgers_paginationReportsRealTotalAndStablePages() {
        for (int i = 1; i <= 5; i++) {
            fixtures.insertLedger("ledger-" + i, "user-1", "Ledger " + i, "Description " + i, "USD");
        }

        var page0 = repository.listUserLedgers("user-1", PageRequest.of(0, 2));
        var page1 = repository.listUserLedgers("user-1", PageRequest.of(1, 2));
        var page2 = repository.listUserLedgers("user-1", PageRequest.of(2, 2));

        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page2.getContent()).hasSize(1);

        // With a deterministic ORDER BY, the three pages partition the five
        // ledgers without overlaps or gaps.
        var seen = new java.util.ArrayList<String>();
        page0.forEach(l -> seen.add(l.id()));
        page1.forEach(l -> seen.add(l.id()));
        page2.forEach(l -> seen.add(l.id()));
        assertThat(seen).containsExactlyInAnyOrder("ledger-1", "ledger-2", "ledger-3", "ledger-4", "ledger-5");
    }

    @Test
    void listUserLedgers_emptyResult_returnsEmptyPage() {
        var result = repository.listUserLedgers("user-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listUserLedgers_withAccounts_returnsLedgersWithAccounts() {
        fixtures.insertLedger("ledger-1", "user-1", "Ledger 1", "Description 1", "USD");
        fixtures.insertAccount("account-1", "ledger-1", null, "Checking", "Main checking", "USD", "ASSET", "ACTIVE");

        var result = repository.listUserLedgers("user-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().accounts()).hasSize(1);
    }

}
