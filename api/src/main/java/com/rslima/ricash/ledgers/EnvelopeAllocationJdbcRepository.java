package com.rslima.ricash.ledgers;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class EnvelopeAllocationJdbcRepository implements EnvelopeAllocationRepository {
    private final JdbcClient jdbcClient;

    record DBEnvelopeAllocation(String id, String envelopeId, int periodYear, int periodMonth,
                                 BigDecimal allocatedAmount, String notes, Instant createdAt, Instant updatedAt) {
    }

    @Override
    public List<EnvelopeAllocation> findByEnvelopeId(String envelopeId) {
        return jdbcClient.sql("""
                        SELECT id, envelope_id, period_year, period_month, allocated_amount, notes, created_at, updated_at
                        FROM envelope_allocations
                        WHERE envelope_id = :envelopeId
                        ORDER BY period_year DESC, period_month DESC
                        """)
                .param("envelopeId", envelopeId)
                .query(DBEnvelopeAllocation.class)
                .list()
                .stream()
                .map(this::toAllocation)
                .toList();
    }

    @Override
    public Optional<EnvelopeAllocation> findByEnvelopeIdAndPeriod(String envelopeId, int year, int month) {
        return jdbcClient.sql("""
                        SELECT id, envelope_id, period_year, period_month, allocated_amount, notes, created_at, updated_at
                        FROM envelope_allocations
                        WHERE envelope_id = :envelopeId AND period_year = :year AND period_month = :month
                        """)
                .param("envelopeId", envelopeId)
                .param("year", year)
                .param("month", month)
                .query(DBEnvelopeAllocation.class)
                .optional()
                .map(this::toAllocation);
    }

    @Override
    public EnvelopeAllocation upsert(String envelopeId, int year, int month, BigDecimal allocatedAmount, String notes) {
        var existing = findByEnvelopeIdAndPeriod(envelopeId, year, month);

        if (existing.isPresent()) {
            jdbcClient.sql("""
                            UPDATE envelope_allocations
                            SET allocated_amount = :allocatedAmount, notes = :notes, updated_at = :updatedAt
                            WHERE envelope_id = :envelopeId AND period_year = :year AND period_month = :month
                            """)
                    .param("envelopeId", envelopeId)
                    .param("year", year)
                    .param("month", month)
                    .param("allocatedAmount", allocatedAmount)
                    .param("notes", notes)
                    .param("updatedAt", Timestamp.from(Instant.now()))
                    .update();
        } else {
            var id = UuidCreator.getTimeOrderedEpoch().toString();
            var now = Instant.now();
            jdbcClient.sql("""
                            INSERT INTO envelope_allocations (id, envelope_id, period_year, period_month, allocated_amount, notes, created_at, updated_at)
                            VALUES (:id, :envelopeId, :year, :month, :allocatedAmount, :notes, :createdAt, :updatedAt)
                            """)
                    .param("id", id)
                    .param("envelopeId", envelopeId)
                    .param("year", year)
                    .param("month", month)
                    .param("allocatedAmount", allocatedAmount)
                    .param("notes", notes)
                    .param("createdAt", Timestamp.from(now))
                    .param("updatedAt", Timestamp.from(now))
                    .update();
        }

        return findByEnvelopeIdAndPeriod(envelopeId, year, month).orElseThrow();
    }

    @Override
    public void deleteByEnvelopeId(String envelopeId) {
        jdbcClient.sql("""
                        DELETE FROM envelope_allocations WHERE envelope_id = :envelopeId
                        """)
                .param("envelopeId", envelopeId)
                .update();
    }

    @Override
    public BigDecimal sumAllocatedForPeriod(String ledgerId, int year, int month) {
        var result = jdbcClient.sql("""
                        SELECT COALESCE(SUM(ea.allocated_amount), 0)
                        FROM envelope_allocations ea
                        JOIN envelopes e ON ea.envelope_id = e.id
                        WHERE e.ledger_id = :ledgerId AND ea.period_year = :year AND ea.period_month = :month
                        """)
                .param("ledgerId", ledgerId)
                .param("year", year)
                .param("month", month)
                .query(BigDecimal.class)
                .single();
        return result != null ? result : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal calculateSpentForEnvelope(String envelopeId, int year, int month) {
        var result = jdbcClient.sql("""
                        SELECT COALESCE(SUM(
                            CASE
                                WHEN te.to_amount IS NOT NULL THEN te.to_amount
                                ELSE te.amount
                            END
                        ), 0)
                        FROM transaction_entries te
                        JOIN transactions t ON te.transaction_id = t.id
                        WHERE te.envelope_id = :envelopeId
                          AND te.type = 'DEBIT'
                          AND EXTRACT(YEAR FROM t.date) = :year
                          AND EXTRACT(MONTH FROM t.date) = :month
                        """)
                .param("envelopeId", envelopeId)
                .param("year", year)
                .param("month", month)
                .query(BigDecimal.class)
                .single();
        return result != null ? result : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal calculateIncomeForPeriod(String ledgerId, int year, int month) {
        // Income is CREDIT entries to INCOME type accounts
        var result = jdbcClient.sql("""
                        SELECT COALESCE(SUM(
                            CASE
                                WHEN te.to_amount IS NOT NULL THEN te.to_amount
                                ELSE te.amount
                            END
                        ), 0)
                        FROM transaction_entries te
                        JOIN transactions t ON te.transaction_id = t.id
                        JOIN accounts a ON te.account_id = a.id
                        WHERE a.ledger_id = :ledgerId
                          AND a.type = 'INCOME'
                          AND te.type = 'CREDIT'
                          AND EXTRACT(YEAR FROM t.date) = :year
                          AND EXTRACT(MONTH FROM t.date) = :month
                        """)
                .param("ledgerId", ledgerId)
                .param("year", year)
                .param("month", month)
                .query(BigDecimal.class)
                .single();
        return result != null ? result : BigDecimal.ZERO;
    }

    private EnvelopeAllocation toAllocation(DBEnvelopeAllocation db) {
        return new EnvelopeAllocation(
                db.id(),
                db.envelopeId(),
                db.periodYear(),
                db.periodMonth(),
                db.allocatedAmount(),
                db.notes(),
                db.createdAt(),
                db.updatedAt()
        );
    }
}
