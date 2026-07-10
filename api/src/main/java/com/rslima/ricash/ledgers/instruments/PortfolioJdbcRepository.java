package com.rslima.ricash.ledgers.instruments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class PortfolioJdbcRepository implements PortfolioRepository {

    private final JdbcClient jdbcClient;

    // DEBIT entries increase quantity (buys), CREDIT entries decrease quantity (sells).
    private static final String POSITION_SELECT = """
            SELECT
                te.instrument_id,
                SUM(CASE WHEN te.type = 'DEBIT' THEN te.quantity ELSE 0 END) AS debit_quantity,
                SUM(CASE WHEN te.type = 'CREDIT' THEN te.quantity ELSE 0 END) AS credit_quantity,
                SUM(CASE WHEN te.type = 'DEBIT' THEN COALESCE(te.to_amount, te.amount) ELSE 0 END) AS debit_amount,
                SUM(CASE WHEN te.type = 'CREDIT' THEN COALESCE(te.to_amount, te.amount) ELSE 0 END) AS credit_amount
            FROM transaction_entries te
            JOIN transactions t ON te.transaction_id = t.id
            WHERE t.ledger_id = :ledgerId
              AND te.instrument_id IS NOT NULL
            """;

    @Override
    public List<PositionData> aggregatePositions(String ledgerId) {
        log.debug("Aggregating all positions for ledger {}", ledgerId);

        return jdbcClient.sql(POSITION_SELECT + " GROUP BY te.instrument_id")
                .param("ledgerId", ledgerId)
                .query(PositionData.class)
                .list();
    }

    @Override
    public List<PositionData> aggregatePositions(String ledgerId, String accountId) {
        log.debug("Aggregating positions for account {} in ledger {}", accountId, ledgerId);

        return jdbcClient.sql(POSITION_SELECT + " AND te.account_id = :accountId GROUP BY te.instrument_id")
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .query(PositionData.class)
                .list();
    }
}
