package com.rslima.ricash.ledgers;

import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.RepresentationModel;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("envelope-allocations")
public class EnvelopeAllocationResource extends RepresentationModel<EnvelopeAllocationResource> {
    @JsonApiId
    private String id;
    private String envelopeId;
    private int periodYear;
    private int periodMonth;
    private BigDecimal allocatedAmount;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
