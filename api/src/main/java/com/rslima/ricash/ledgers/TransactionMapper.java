package com.rslima.ricash.ledgers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TransactionMapper {

    @Mapping(target = "amount", source = ".", qualifiedByName = "calculateTotalAmount")
    @Mapping(target = "currency", source = ".", qualifiedByName = "extractCurrency")
    @Mapping(target = "entries", source = ".", qualifiedByName = "combineEntries")
    TransactionResource toResource(Transaction transaction);

    @Named("calculateTotalAmount")
    default BigDecimal calculateTotalAmount(Transaction transaction) {
        // Sum up all debit amounts (or credit amounts - they should be equal)
        return transaction.debitEntries().stream()
                .map(e -> e.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Named("extractCurrency")
    default String extractCurrency(Transaction transaction) {
        // Get currency from the first entry
        if (!transaction.debitEntries().isEmpty()) {
            return transaction.debitEntries().getFirst().amount().currency();
        }
        if (!transaction.creditEntries().isEmpty()) {
            return transaction.creditEntries().getFirst().amount().currency();
        }
        return null;
    }

    @Named("combineEntries")
    default List<TransactionResource.TransactionEntryResource> combineEntries(Transaction transaction) {
        List<TransactionResource.TransactionEntryResource> entries = new ArrayList<>();

        for (var entry : transaction.debitEntries()) {
            entries.add(new TransactionResource.TransactionEntryResource(
                    entry.accountId(),
                    entry.accountName(),
                    entry.amount().amount(),
                    entry.amount().currency(),
                    entry.convertedAmount() != null ? entry.convertedAmount().amount() : null,
                    entry.convertedAmount() != null ? entry.convertedAmount().currency() : null,
                    entry.type().name(),
                    entry.instrumentId(),
                    entry.quantity(),
                    entry.instrumentSymbol(),
                    entry.envelopeId()
            ));
        }

        for (var entry : transaction.creditEntries()) {
            entries.add(new TransactionResource.TransactionEntryResource(
                    entry.accountId(),
                    entry.accountName(),
                    entry.amount().amount(),
                    entry.amount().currency(),
                    entry.convertedAmount() != null ? entry.convertedAmount().amount() : null,
                    entry.convertedAmount() != null ? entry.convertedAmount().currency() : null,
                    entry.type().name(),
                    entry.instrumentId(),
                    entry.quantity(),
                    entry.instrumentSymbol(),
                    entry.envelopeId()
            ));
        }

        return entries;
    }
}
