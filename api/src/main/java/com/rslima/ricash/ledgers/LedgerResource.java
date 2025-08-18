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

@Data @EqualsAndHashCode(callSuper = false) @NoArgsConstructor @AllArgsConstructor @JsonApiTypeForClass("ledgers")
public class LedgerResource extends RepresentationModel<LedgerResource> {
    @JsonApiId
    private String        id;
    private String        name;
    private String        description;
    private String        currency;
    private Instant       createdAt;
    @JsonApiRelationships("accounts")
    @JsonIgnore
    private List<AccountResource> accounts;

}
