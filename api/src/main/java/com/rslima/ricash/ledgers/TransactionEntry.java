package com.rslima.ricash.ledgers;

import java.math.BigDecimal;

/**
 * Represents an entry in a transaction (debit or credit).
 * Supports multi-currency transactions through optional convertedAmount field.
 * Supports investment tracking through optional instrumentId and quantity fields.
 *
 * @param accountId the account this entry affects
 * @param type DEBIT or CREDIT
 * @param amount the original amount in the transaction currency
 * @param convertedAmount optional converted amount when currency differs from account currency
 * @param accountName the name of the account (for display purposes)
 * @param instrumentId optional instrument ID for investment transactions
 * @param quantity optional quantity for investment transactions
 * @param instrumentSymbol optional instrument symbol for display purposes
 */
public record TransactionEntry(
    String accountId,
    TransactionEntryType type,
    MonetaryAmount amount,
    MonetaryAmount convertedAmount,
    String accountName,
    String instrumentId,
    BigDecimal quantity,
    String instrumentSymbol
) {
    /**
     * Constructor for backward compatibility without instrument fields.
     */
    public TransactionEntry(
            String accountId,
            TransactionEntryType type,
            MonetaryAmount amount,
            MonetaryAmount convertedAmount,
            String accountName
    ) {
        this(accountId, type, amount, convertedAmount, accountName, null, null, null);
    }
}
