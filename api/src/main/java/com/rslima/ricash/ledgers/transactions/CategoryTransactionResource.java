package com.rslima.ricash.ledgers.transactions;

import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.RepresentationModel;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("category-transactions")
public class CategoryTransactionResource extends RepresentationModel<CategoryTransactionResource> {
    @JsonApiId
    private String id;
    private LocalDate date;
    private String description;
    private BigDecimal amount;
    private String currency;
}
