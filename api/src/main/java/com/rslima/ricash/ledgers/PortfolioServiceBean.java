package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class PortfolioServiceBean implements PortfolioService {

    private final JdbcClient jdbcClient;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentPriceRepository instrumentPriceRepository;

    private static final int AMOUNT_SCALE = 2;
    private static final int QUANTITY_SCALE = 8;

    @Override
    public List<InstrumentPosition> getPositions(String ledgerId, String accountId) {
        log.debug("Calculating positions for account {} in ledger {}", accountId, ledgerId);

        // Query to get aggregated position data per instrument for a specific account
        // DEBIT entries increase quantity (buys), CREDIT entries decrease quantity (sells)
        List<PositionData> positionData = jdbcClient.sql("""
                SELECT
                    te.instrument_id,
                    SUM(CASE WHEN te.type = 'DEBIT' THEN te.quantity ELSE 0 END) AS debit_quantity,
                    SUM(CASE WHEN te.type = 'CREDIT' THEN te.quantity ELSE 0 END) AS credit_quantity,
                    SUM(CASE WHEN te.type = 'DEBIT' THEN COALESCE(te.to_amount, te.amount) ELSE 0 END) AS debit_amount,
                    SUM(CASE WHEN te.type = 'CREDIT' THEN COALESCE(te.to_amount, te.amount) ELSE 0 END) AS credit_amount
                FROM transaction_entries te
                JOIN transactions t ON te.transaction_id = t.id
                WHERE t.ledger_id = :ledgerId
                  AND te.account_id = :accountId
                  AND te.instrument_id IS NOT NULL
                GROUP BY te.instrument_id
                """)
            .param("ledgerId", ledgerId)
            .param("accountId", accountId)
            .query((rs, rowNum) -> new PositionData(
                rs.getString("instrument_id"),
                rs.getBigDecimal("debit_quantity"),
                rs.getBigDecimal("credit_quantity"),
                rs.getBigDecimal("debit_amount"),
                rs.getBigDecimal("credit_amount")
            ))
            .list();

        return buildPositions(ledgerId, positionData);
    }

    @Override
    public List<InstrumentPosition> getAllPositions(String ledgerId) {
        log.debug("Calculating all positions for ledger {}", ledgerId);

        // Query to get aggregated position data per instrument across all accounts
        List<PositionData> positionData = jdbcClient.sql("""
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
                GROUP BY te.instrument_id
                """)
            .param("ledgerId", ledgerId)
            .query((rs, rowNum) -> new PositionData(
                rs.getString("instrument_id"),
                rs.getBigDecimal("debit_quantity"),
                rs.getBigDecimal("credit_quantity"),
                rs.getBigDecimal("debit_amount"),
                rs.getBigDecimal("credit_amount")
            ))
            .list();

        return buildPositions(ledgerId, positionData);
    }

    private List<InstrumentPosition> buildPositions(String ledgerId, List<PositionData> positionData) {
        if (positionData.isEmpty()) {
            return List.of();
        }

        // Load instruments
        Map<String, Instrument> instrumentMap = new HashMap<>();
        for (Instrument instrument : instrumentRepository.findAllByLedgerId(ledgerId)) {
            instrumentMap.put(instrument.id(), instrument);
        }

        // Load latest prices
        Map<String, BigDecimal> latestPrices = new HashMap<>();
        for (InstrumentPrice price : instrumentPriceRepository.findLatestPricesByLedgerId(ledgerId)) {
            latestPrices.put(price.instrumentId(), price.price());
        }

        List<InstrumentPosition> positions = new ArrayList<>();

        for (PositionData data : positionData) {
            Instrument instrument = instrumentMap.get(data.instrumentId());
            if (instrument == null) {
                log.warn("Instrument not found: {}", data.instrumentId());
                continue;
            }

            // Calculate net quantity: debits - credits
            BigDecimal quantity = data.debitQuantity().subtract(data.creditQuantity())
                .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);

            // Skip zero positions
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // Calculate total cost: debits - credits (net investment)
            BigDecimal totalCost = data.debitAmount().subtract(data.creditAmount())
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

            // Calculate average cost
            BigDecimal averageCost = quantity.compareTo(BigDecimal.ZERO) != 0
                ? totalCost.divide(quantity, AMOUNT_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            // Get current price
            BigDecimal currentPrice = latestPrices.get(data.instrumentId());

            // Calculate current value and gains
            BigDecimal currentValue = null;
            BigDecimal unrealizedGain = null;
            BigDecimal unrealizedGainPercent = null;

            if (currentPrice != null) {
                currentValue = quantity.multiply(currentPrice).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
                unrealizedGain = currentValue.subtract(totalCost).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

                if (totalCost.compareTo(BigDecimal.ZERO) != 0) {
                    unrealizedGainPercent = unrealizedGain.divide(totalCost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                }
            }

            positions.add(new InstrumentPosition(
                instrument.id(),
                instrument.symbol(),
                instrument.name(),
                instrument.type(),
                instrument.currency(),
                quantity,
                totalCost,
                averageCost,
                currentPrice,
                currentValue,
                unrealizedGain,
                unrealizedGainPercent
            ));
        }

        return positions;
    }

    private record PositionData(
        String instrumentId,
        BigDecimal debitQuantity,
        BigDecimal creditQuantity,
        BigDecimal debitAmount,
        BigDecimal creditAmount
    ) {}
}
