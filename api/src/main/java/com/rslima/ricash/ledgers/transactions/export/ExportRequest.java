package com.rslima.ricash.ledgers.transactions.export;

import java.time.LocalDate;

/**
 * Parameters of a transaction export. {@code accountId} null means the whole
 * ledger; {@code includeChildren} widens an account scope to its subtree and
 * is ignored without an account. Both date bounds are inclusive and nullable
 * (= unbounded).
 */
public record ExportRequest(
        String format,
        String accountId,
        boolean includeChildren,
        LocalDate from,
        LocalDate to) {
}
