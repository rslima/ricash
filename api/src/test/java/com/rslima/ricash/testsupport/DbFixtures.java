package com.rslima.ricash.testsupport;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Shared raw-SQL fixtures for Testcontainers integration tests. Inserts bypass
 * the repositories on purpose so tests can seed state independently of the
 * code under test. Slugs are derived from names (lowercased, spaces to dashes).
 */
public class DbFixtures {

    private final JdbcClient jdbcClient;

    public DbFixtures(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Deletes all user-scoped rows in FK-safe order. Global exchange_rates rows are left alone. */
    public void cleanAll() {
        jdbcClient.sql("DELETE FROM transaction_entries").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM envelope_allocations").update();
        jdbcClient.sql("DELETE FROM envelope_account_mappings").update();
        jdbcClient.sql("DELETE FROM envelopes").update();
        jdbcClient.sql("DELETE FROM instrument_prices").update();
        jdbcClient.sql("DELETE FROM instruments").update();
        jdbcClient.sql("DELETE FROM accounts").update();
        jdbcClient.sql("DELETE FROM ledgers").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    public void insertUser(String id) {
        jdbcClient.sql("INSERT INTO users (id) VALUES (:id)").param("id", id).update();
    }

    public void insertLedger(String id, String userId, String name, String currency) {
        insertLedger(id, userId, name, null, currency);
    }

    public void insertLedger(String id, String userId, String name, String description, String currency) {
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES (:id, :userId, :slug, :name, :description, :currency, :createdAt)
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("slug", slugify(name))
                .param("name", name)
                .param("description", description)
                .param("currency", currency)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    public void insertAccount(String id, String ledgerId, String parentAccountId, String name, String currency, String type) {
        insertAccount(id, ledgerId, parentAccountId, name, null, currency, type, "ACTIVE");
    }

    public void insertAccount(String id, String ledgerId, String parentAccountId, String name, String description,
                              String currency, String type, String status) {
        jdbcClient.sql("""
                        INSERT INTO accounts (id, ledger_id, parent_account_id, slug, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, :parentAccountId, :slug, :name, :description, :currency, :type, :status, :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", ledgerId)
                .param("parentAccountId", parentAccountId)
                .param("slug", slugify(name))
                .param("name", name)
                .param("description", description)
                .param("currency", currency)
                .param("type", type)
                .param("status", status)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    public void insertTransaction(String id, String ledgerId, String description, LocalDate date) {
        jdbcClient.sql("""
                        INSERT INTO transactions (id, ledger_id, description, date, created_at)
                        VALUES (:id, :ledgerId, :description, :date, :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", ledgerId)
                .param("description", description)
                .param("date", Date.valueOf(date))
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    public void insertEntry(String transactionId, String accountId, String amount, String type, String currency) {
        insertEntry(transactionId, accountId, amount, type, currency, null, null, null);
    }

    public void insertEntry(String transactionId, String accountId, String amount, String type,
                            String currency, String toAmount, String toCurrency) {
        insertEntry(transactionId, accountId, amount, type, currency, toAmount, toCurrency, null);
    }

    public void insertEntry(String transactionId, String accountId, String amount, String type,
                            String currency, String toAmount, String toCurrency, String envelopeId) {
        jdbcClient.sql("""
                        INSERT INTO transaction_entries (id, transaction_id, account_id, amount, type, currency, to_amount, to_currency, envelope_id)
                        VALUES (:id, :transactionId, :accountId, :amount, :type, :currency, :toAmount, :toCurrency, :envelopeId)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("transactionId", transactionId)
                .param("accountId", accountId)
                .param("amount", new BigDecimal(amount))
                .param("type", type)
                .param("currency", currency)
                .param("toAmount", toAmount != null ? new BigDecimal(toAmount) : null)
                .param("toCurrency", toCurrency)
                .param("envelopeId", envelopeId)
                .update();
    }

    public void insertInstrumentEntry(String transactionId, String accountId, String amount, String type,
                                      String currency, String instrumentId, String quantity) {
        jdbcClient.sql("""
                        INSERT INTO transaction_entries (id, transaction_id, account_id, amount, type, currency, instrument_id, quantity)
                        VALUES (:id, :transactionId, :accountId, :amount, :type, :currency, :instrumentId, :quantity)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("transactionId", transactionId)
                .param("accountId", accountId)
                .param("amount", new BigDecimal(amount))
                .param("type", type)
                .param("currency", currency)
                .param("instrumentId", instrumentId)
                .param("quantity", new BigDecimal(quantity))
                .update();
    }

    public void insertEnvelope(String id, String ledgerId, String name, String currency) {
        jdbcClient.sql("""
                        INSERT INTO envelopes (id, ledger_id, parent_envelope_id, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, null, :name, null, :currency, 'EXPENSE', 'ACTIVE', :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", ledgerId)
                .param("name", name)
                .param("currency", currency)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    public void insertEnvelopeAllocation(String envelopeId, int year, int month, String allocatedAmount) {
        jdbcClient.sql("""
                        INSERT INTO envelope_allocations (id, envelope_id, period_year, period_month, allocated_amount)
                        VALUES (:id, :envelopeId, :year, :month, :allocatedAmount)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("envelopeId", envelopeId)
                .param("year", year)
                .param("month", month)
                .param("allocatedAmount", new BigDecimal(allocatedAmount))
                .update();
    }

    public void insertInstrument(String id, String ledgerId, String symbol, String name, String type, String currency) {
        insertInstrument(id, ledgerId, symbol, name, type, currency, null, "ACTIVE");
    }

    public void insertInstrument(String id, String ledgerId, String symbol, String name, String type, String currency,
                                 String isin, String status) {
        jdbcClient.sql("""
                        INSERT INTO instruments (id, ledger_id, symbol, name, type, currency, isin, status, created_at)
                        VALUES (:id, :ledgerId, :symbol, :name, :type, :currency, :isin, :status, :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", ledgerId)
                .param("symbol", symbol)
                .param("name", name)
                .param("type", type)
                .param("currency", currency)
                .param("isin", isin)
                .param("status", status)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    public void insertInstrumentPrice(String id, String instrumentId, String price, LocalDate effectiveDate) {
        jdbcClient.sql("""
                        INSERT INTO instrument_prices (id, instrument_id, price, effective_date, source, created_at)
                        VALUES (:id, :instrumentId, :price, :effectiveDate, 'MANUAL', :createdAt)
                        """)
                .param("id", id)
                .param("instrumentId", instrumentId)
                .param("price", new BigDecimal(price))
                .param("effectiveDate", Date.valueOf(effectiveDate))
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private static String slugify(String name) {
        return name.toLowerCase().replaceAll("\\s+", "-");
    }
}
