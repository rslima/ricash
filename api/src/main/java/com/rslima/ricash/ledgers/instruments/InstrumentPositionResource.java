package com.rslima.ricash.ledgers.instruments;

import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.RepresentationModel;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("positions")
public class InstrumentPositionResource extends RepresentationModel<InstrumentPositionResource> {
    @JsonApiId
    private String instrumentId;
    private String instrumentSymbol;
    private String instrumentName;
    private InstrumentType instrumentType;
    private String currency;
    private BigDecimal quantity;
    private BigDecimal totalCost;
    private BigDecimal averageCost;
    private BigDecimal currentPrice;
    private BigDecimal currentValue;
    private BigDecimal unrealizedGain;
    private BigDecimal unrealizedGainPercent;
}
