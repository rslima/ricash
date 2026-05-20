package com.rslima.ricash.ledgers.transactions;

import java.math.BigDecimal;
import java.util.Map;

public record MonthlyExpenseBreakdown(
        int year,
        int month,
        Map<String, BigDecimal> expensesByAccountId
) {
}
