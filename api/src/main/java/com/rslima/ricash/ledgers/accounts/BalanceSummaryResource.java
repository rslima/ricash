package com.rslima.ricash.ledgers.accounts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSummaryResource {
    private String id;
    private Map<String, BigDecimal> balanceByCurrency;
}
