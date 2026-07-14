package com.rslima.ricash.ledgers.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One transaction entry flattened with its transaction's fields, as consumed
 * by the export formats. Unlike {@link TransactionEntry} it carries the entry
 * id, which exports need as a stable unique key (e.g. the OFX FITID).
 */
public record TransactionExportRow(
        String transactionId,
        LocalDate date,
        Instant createdAt,
        String description,
        String entryId,
        String accountId,
        String accountName,
        TransactionEntryType type,
        BigDecimal amount,
        String currency,
        BigDecimal toAmount,
        String toCurrency,
        String instrumentId,
        BigDecimal quantity,
        String instrumentSymbol,
        String envelopeId) {
}
