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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("accounts")
public class AccountResource extends RepresentationModel<AccountResource> {
    @JsonApiId
    private String id;
    private String slug;
    private String name;
    private String description;
    private String currency;
    private AccountType type;
    private AccountStatus status;
    private BigDecimal balance;
    private Instant createdAt;
    private String parentAccountId;
    @JsonApiRelationships("subaccounts")
    @JsonIgnore
    private List<AccountResource> subAccounts;
}
