package com.rslima.ricash.ledgers.instruments;

import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.RepresentationModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("instrument-prices")
public class InstrumentPriceResource extends RepresentationModel<InstrumentPriceResource> {
    @JsonApiId
    private String id;
    private String instrumentId;
    private String instrumentSymbol;
    private BigDecimal price;
    private LocalDate effectiveDate;
    private String source;
    private Instant createdAt;
}
