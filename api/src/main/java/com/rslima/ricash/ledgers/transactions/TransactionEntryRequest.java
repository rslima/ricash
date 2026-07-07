package com.rslima.ricash.ledgers.transactions;

import java.math.BigDecimal;

/**
 * Common shape of a transaction entry as submitted by clients; implemented by
 * the create and update request entry records (their accessors match).
 */
public interface TransactionEntryRequest {
    String accountId();

    BigDecimal amount();

    String currency();

    BigDecimal toAmount();

    String toCurrency();

    TransactionEntryType type();

    String instrumentId();

    BigDecimal quantity();

    String envelopeId();
}
