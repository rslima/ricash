package com.rslima.ricash.ledgers;

import com.rslima.ricash.TestRicashApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
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

    @BeforeEach
    void setUp() {
        repository = new LedgerJdbcRepository(jdbcClient);

        // Clean up and insert test users
        jdbcClient.sql("DELETE FROM transaction_entries").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM accounts").update();
        jdbcClient.sql("DELETE FROM ledgers").update();
        jdbcClient.sql("DELETE FROM user_roles").update();
        jdbcClient.sql("DELETE FROM users").update();

        jdbcClient.sql("INSERT INTO users (id, username, password, salt, status, email) VALUES ('user-1', 'testuser', 'pass', 'salt', 'ACTIVE', 'test@example.com')").update();
        jdbcClient.sql("INSERT INTO users (id, username, password, salt, status, email) VALUES ('user-2', 'otheruser', 'pass', 'salt', 'ACTIVE', 'other@example.com')").update();
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
                List.of(),
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
                List.of(),
                List.of()
        );

        var result = repository.create(ledger);

        assertThat(result.description()).isNull();
    }

    @Test
    void findById_returnsLedger() {
        insertLedger("ledger-1", "user-1", "Test Ledger", "Description", "USD");

        var result = repository.findById("user-1", "ledger-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("ledger-1");
        assertThat(result.get().userId()).isEqualTo("user-1");
        assertThat(result.get().name()).isEqualTo("Test Ledger");
        assertThat(result.get().description()).isEqualTo("Description");
        assertThat(result.get().currency()).isEqualTo("USD");
    }

    @Test
    void findById_withAccounts_returnsLedgerWithAccounts() {
        insertLedger("ledger-1", "user-1", "Test Ledger", "Description", "USD");
        insertAccount("account-1", "ledger-1", null, "Checking", "Main checking", "USD", "ASSET", "ACTIVE");
        insertAccount("account-2", "ledger-1", null, "Savings", "Main savings", "USD", "ASSET", "ACTIVE");

        var result = repository.findById("user-1", "ledger-1");

        assertThat(result).isPresent();
        assertThat(result.get().accounts()).hasSize(2);
    }

    @Test
    void findById_withNestedAccounts_returnsAccountTree() {
        insertLedger("ledger-1", "user-1", "Test Ledger", "Description", "USD");
        insertAccount("account-1", "ledger-1", null, "Assets", "All assets", "USD", "ASSET", "ACTIVE");
        insertAccount("account-2", "ledger-1", "account-1", "Checking", "Main checking", "USD", "ASSET", "ACTIVE");

        var result = repository.findById("user-1", "ledger-1");

        assertThat(result).isPresent();
        assertThat(result.get().accounts()).hasSize(1);
        assertThat(result.get().accounts().getFirst().name()).isEqualTo("Assets");
        assertThat(result.get().accounts().getFirst().subAccounts()).hasSize(1);
        assertThat(result.get().accounts().getFirst().subAccounts().getFirst().name()).isEqualTo("Checking");
    }

    @Test
    void findById_wrongUser_returnsEmpty() {
        insertLedger("ledger-1", "user-1", "Test Ledger", "Description", "USD");

        var result = repository.findById("user-2", "ledger-1");

        assertThat(result).isEmpty();
    }

    @Test
    void findById_notFound_returnsEmpty() {
        var result = repository.findById("user-1", "nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void listUserLedgers_returnsUserLedgers() {
        insertLedger("ledger-1", "user-1", "Ledger 1", "Description 1", "USD");
        insertLedger("ledger-2", "user-1", "Ledger 2", "Description 2", "EUR");
        insertLedger("ledger-3", "user-2", "Other Ledger", "Other Description", "GBP");

        var result = repository.listUserLedgers("user-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Ledger::name)
                .containsExactlyInAnyOrder("Ledger 1", "Ledger 2");
    }

    @Test
    void listUserLedgers_withPagination_returnsCorrectPage() {
        for (int i = 1; i <= 5; i++) {
            insertLedger("ledger-" + i, "user-1", "Ledger " + i, "Description " + i, "USD");
        }

        var result = repository.listUserLedgers("user-1", PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void listUserLedgers_emptyResult_returnsEmptyPage() {
        var result = repository.listUserLedgers("user-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listUserLedgers_withAccounts_returnsLedgersWithAccounts() {
        insertLedger("ledger-1", "user-1", "Ledger 1", "Description 1", "USD");
        insertAccount("account-1", "ledger-1", null, "Checking", "Main checking", "USD", "ASSET", "ACTIVE");

        var result = repository.listUserLedgers("user-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().accounts()).hasSize(1);
    }

    private void insertLedger(String id, String userId, String name, String description, String currency) {
        var slug = name.toLowerCase().replaceAll("\\s+", "-");
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES (:id, :userId, :slug, :name, :description, :currency, :createdAt)
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("slug", slug)
                .param("name", name)
                .param("description", description)
                .param("currency", currency)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertAccount(String id, String ledgerId, String parentAccountId, String name, String description, String currency, String type, String status) {
        var slug = name.toLowerCase().replaceAll("\\s+", "-");
        jdbcClient.sql("""
                        INSERT INTO accounts (id, ledger_id, parent_account_id, slug, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, :parentAccountId, :slug, :name, :description, :currency, :type, :status, :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", ledgerId)
                .param("parentAccountId", parentAccountId)
                .param("slug", slug)
                .param("name", name)
                .param("description", description)
                .param("currency", currency)
                .param("type", type)
                .param("status", status)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }
}
