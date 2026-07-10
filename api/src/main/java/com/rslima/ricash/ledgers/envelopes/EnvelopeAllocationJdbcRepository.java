package com.rslima.ricash.ledgers.envelopes;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.rslima.ricash.ledgers.DateRanges.monthEnd;
import static com.rslima.ricash.ledgers.DateRanges.monthStart;

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
    public List<MonthlyActivity> findMonthlyActivityByLedger(String ledgerId, int uptoYear, int uptoMonth) {
        return jdbcClient.sql("""
                        SELECT envelope_id, period_year, period_month,
                               SUM(allocated) AS allocated, SUM(spent) AS spent
                        FROM (
                            SELECT ea.envelope_id, ea.period_year, ea.period_month,
                                   ea.allocated_amount AS allocated, 0::numeric AS spent
                            FROM envelope_allocations ea
                            JOIN envelopes e ON e.id = ea.envelope_id
                            WHERE e.ledger_id = :ledgerId
                            UNION ALL
                            SELECT ems.envelope_id, ems.period_year, ems.period_month,
                                   0::numeric, ems.spent_total
                            FROM envelope_monthly_summary ems
                            JOIN envelopes e ON e.id = ems.envelope_id
                            WHERE e.ledger_id = :ledgerId
                        ) activity
                        WHERE period_year >= 2020
                          AND (period_year, period_month) <= (:year, :month)
                        GROUP BY envelope_id, period_year, period_month
                        ORDER BY envelope_id, period_year, period_month
                        """)
                .param("ledgerId", ledgerId)
                .param("year", uptoYear)
                .param("month", uptoMonth)
                .query(MonthlyActivity.class)
                .list();
    }

    @Override
    public List<MonthlyActivity> findMonthlyActivityByEnvelope(String envelopeId, int uptoYear, int uptoMonth) {
        return jdbcClient.sql("""
                        SELECT envelope_id, period_year, period_month,
                               SUM(allocated) AS allocated, SUM(spent) AS spent
                        FROM (
                            SELECT envelope_id, period_year, period_month,
                                   allocated_amount AS allocated, 0::numeric AS spent
                            FROM envelope_allocations
                            WHERE envelope_id = :envelopeId
                            UNION ALL
                            SELECT envelope_id, period_year, period_month,
                                   0::numeric, spent_total
                            FROM envelope_monthly_summary
                            WHERE envelope_id = :envelopeId
                        ) activity
                        WHERE period_year >= 2020
                          AND (period_year, period_month) <= (:year, :month)
                        GROUP BY envelope_id, period_year, period_month
                        ORDER BY period_year, period_month
                        """)
                .param("envelopeId", envelopeId)
                .param("year", uptoYear)
                .param("month", uptoMonth)
                .query(MonthlyActivity.class)
                .list();
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
                          AND t.date >= :periodStart
                          AND t.date < :periodEnd
                        """)
                .param("ledgerId", ledgerId)
                .param("periodStart", monthStart(year, month))
                .param("periodEnd", monthEnd(year, month))
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
