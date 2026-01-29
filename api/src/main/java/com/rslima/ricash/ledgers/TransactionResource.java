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
import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("transactions")
public class TransactionResource extends RepresentationModel<TransactionResource> {
    @JsonApiId
    private String id;
    private LocalDate date;
    private String description;
    private BigDecimal amount;
    private String currency;
    private Instant createdAt;
    private List<TransactionEntryResource> entries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionEntryResource {
        private String accountId;
        private String accountName;
        private BigDecimal amount;
        private String type;
    }
}
