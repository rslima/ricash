package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises envelope-account mapping replacement through the Spring-proxied
 * EnvelopeService. NOT @Transactional so the rollback (or partial-write)
 * behavior of setMappingsForEnvelope's delete-then-insert sequence is visible.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
class EnvelopeAccountMappingJdbcRepositoryTest {

    private static final String USER = "user-1";
    private static final String LEDGER_SLUG = "main";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EnvelopeService envelopeService;

    @BeforeEach
    void setUp() {
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser(USER);
        fixtures.insertLedger("ledger-1", USER, "Main", "USD");
        fixtures.insertAccount("acct-1", "ledger-1", null, "Groceries", "USD", "EXPENSE");
        fixtures.insertAccount("acct-2", "ledger-1", null, "Restaurants", "USD", "EXPENSE");
        fixtures.insertEnvelope("food", "ledger-1", "Food", "USD");
    }

    @Test
    void setEnvelopeAccounts_replacesExistingMappings() {
        envelopeService.setEnvelopeAccounts(USER, LEDGER_SLUG, "food", List.of("acct-1"));
        assertThat(envelopeService.getEnvelopeAccounts(USER, LEDGER_SLUG, "food")).containsExactly("acct-1");

        envelopeService.setEnvelopeAccounts(USER, LEDGER_SLUG, "food", List.of("acct-2"));
        assertThat(envelopeService.getEnvelopeAccounts(USER, LEDGER_SLUG, "food")).containsExactly("acct-2");
    }

    @Test
    void setEnvelopeAccounts_failingInsert_preservesExistingMappings() {
        envelopeService.setEnvelopeAccounts(USER, LEDGER_SLUG, "food", List.of("acct-1"));

        // "ghost-acct" violates the account FK after the existing mappings were
        // already deleted and acct-2 already inserted.
        assertThatThrownBy(() ->
                envelopeService.setEnvelopeAccounts(USER, LEDGER_SLUG, "food", List.of("acct-2", "ghost-acct")))
                .isInstanceOf(DataAccessException.class);

        assertThat(envelopeService.getEnvelopeAccounts(USER, LEDGER_SLUG, "food"))
                .as("failed replacement must leave the previous mappings intact")
                .containsExactly("acct-1");
    }
}
