package com.rslima.ricash.ledgers.transactions;

import java.math.BigDecimal;
import java.util.Map;

public record MonthlyReport(
        int year,
        int month,
        Map<String, BigDecimal> incomeByCurrency,
        Map<String, BigDecimal> expensesByCurrency
) {
}
