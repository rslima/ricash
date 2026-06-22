package com.rslima.ricash.ledgers;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Helpers for month-bounded date filters.
 *
 * <p>Queries filter transactions by month using a half-open range
 * {@code [monthStart, monthEnd)} rather than {@code EXTRACT(YEAR/MONTH FROM date)}
 * so the predicate stays sargable and can use {@code idx_transactions_ledger_date}.
 */
public final class DateRanges {

    private DateRanges() {
    }

    /** First day of the given month (inclusive lower bound). */
    public static Date monthStart(int year, int month) {
        return Date.valueOf(LocalDate.of(year, month, 1));
    }

    /** First day of the following month (exclusive upper bound). */
    public static Date monthEnd(int year, int month) {
        return Date.valueOf(LocalDate.of(year, month, 1).plusMonths(1));
    }
}
