package com.rslima.ricash.ledgers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvelopeBalanceResource {
    private String envelopeId;
    private int periodYear;
    private int periodMonth;
    private BigDecimal rollover;
    private BigDecimal allocated;
    private BigDecimal spent;
    private BigDecimal available;
}
