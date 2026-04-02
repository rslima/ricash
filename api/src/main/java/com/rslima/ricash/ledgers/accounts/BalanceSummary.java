package com.rslima.ricash.ledgers.accounts;

import java.math.BigDecimal;
import java.util.Map;

public record BalanceSummary(Map<String, BigDecimal> balanceByCurrency) {
}
