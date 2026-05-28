package com.rslima.ricash.ledgers.transactions;

import java.math.BigDecimal;
import java.util.Map;

public record MonthlyIncomeBreakdown(
        int year,
        int month,
        Map<String, BigDecimal> incomeByAccountId
) {
}
