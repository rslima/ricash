package com.rslima.ricash.ledgers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSummaryResource {
    private String id;
    private int periodYear;
    private int periodMonth;
    private BigDecimal toBeBudgeted;
    private List<EnvelopeBalanceResource> envelopeBalances;
}
