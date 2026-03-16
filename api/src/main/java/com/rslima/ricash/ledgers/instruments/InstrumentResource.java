package com.rslima.ricash.ledgers.instruments;

import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("instruments")
public class InstrumentResource extends RepresentationModel<InstrumentResource> {
    @JsonApiId
    private String id;
    private String ledgerId;
    private String symbol;
    private String name;
    private InstrumentType type;
    private String currency;
    private String market;
    private String isin;
    private InstrumentStatus status;
    private Instant createdAt;
}
