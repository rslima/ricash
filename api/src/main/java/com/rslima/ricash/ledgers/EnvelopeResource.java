package com.rslima.ricash.ledgers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiRelationships;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("envelopes")
public class EnvelopeResource extends RepresentationModel<EnvelopeResource> {
    @JsonApiId
    private String id;
    private String name;
    private String description;
    private String currency;
    private EnvelopeType type;
    private EnvelopeStatus status;
    private Instant createdAt;
    private String parentEnvelopeId;
    @JsonApiRelationships("subenvelopes")
    @JsonIgnore
    private List<EnvelopeResource> subEnvelopes;
}
