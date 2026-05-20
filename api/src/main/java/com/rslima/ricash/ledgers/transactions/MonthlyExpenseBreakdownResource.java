package com.rslima.ricash.ledgers.transactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyExpenseBreakdownResource {
    private String id;
    private int year;
    private int month;
    private Map<String, BigDecimal> expensesByAccountId;
}
