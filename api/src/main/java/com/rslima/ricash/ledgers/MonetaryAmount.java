package com.rslima.ricash.ledgers;

import java.math.BigDecimal;

public record MonetaryAmount(BigDecimal amount, String currency) {
}
