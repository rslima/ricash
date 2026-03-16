package com.rslima.ricash.ledgers;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

    private final TransactionMapper mapper = Mappers.getMapper(TransactionMapper.class);

    @Test
    void toResource_mapsAllFields() {
        var now = Instant.now();
        var date = LocalDate.of(2026, 1, 15);
        var debit = new TransactionEntry("acc-1", TransactionEntryType.DEBIT, new MonetaryAmount(BigDecimal.TEN, "USD"), null, "Checking");
        var credit = new TransactionEntry("acc-2", TransactionEntryType.CREDIT, new MonetaryAmount(BigDecimal.TEN, "USD"), null, "Savings");
        var transaction = new Transaction("txn-id", date, now, "Groceries", List.of(credit), List.of(debit));

        var result = mapper.toResource(transaction);

        assertThat(result.getId()).isEqualTo("txn-id");
        assertThat(result.getDate()).isEqualTo(date);
        assertThat(result.getDescription()).isEqualTo("Groceries");
        assertThat(result.getCreatedAt()).isEqualTo(now);
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    void toResource_combinesDebitAndCreditEntries() {
        var debit = new TransactionEntry("acc-1", TransactionEntryType.DEBIT, new MonetaryAmount(BigDecimal.TEN, "USD"), null, "Checking");
        var credit = new TransactionEntry("acc-2", TransactionEntryType.CREDIT, new MonetaryAmount(BigDecimal.TEN, "USD"), null, "Savings");
        var transaction = new Transaction("txn-id", LocalDate.now(), Instant.now(), "Test", List.of(credit), List.of(debit));

        var result = mapper.toResource(transaction);

        assertThat(result.getEntries()).hasSize(2);
        assertThat(result.getEntries().stream().map(TransactionResource.TransactionEntryResource::getType))
                .containsExactly("DEBIT", "CREDIT");
    }

    @Test
    void toResource_mapsConvertedAmount() {
        var debit = new TransactionEntry("acc-1", TransactionEntryType.DEBIT,
                new MonetaryAmount(new BigDecimal("1000"), "BRL"),
                new MonetaryAmount(new BigDecimal("180"), "USD"),
                "USD Account");
        var credit = new TransactionEntry("acc-2", TransactionEntryType.CREDIT,
                new MonetaryAmount(new BigDecimal("1000"), "BRL"), null, "BRL Account");
        var transaction = new Transaction("txn-id", LocalDate.now(), Instant.now(), "Transfer", List.of(credit), List.of(debit));

        var result = mapper.toResource(transaction);

        var debitEntry = result.getEntries().stream()
                .filter(e -> "DEBIT".equals(e.getType()))
                .findFirst().orElseThrow();
        assertThat(debitEntry.getToAmount()).isEqualByComparingTo(new BigDecimal("180"));
        assertThat(debitEntry.getToCurrency()).isEqualTo("USD");

        var creditEntry = result.getEntries().stream()
                .filter(e -> "CREDIT".equals(e.getType()))
                .findFirst().orElseThrow();
        assertThat(creditEntry.getToAmount()).isNull();
        assertThat(creditEntry.getToCurrency()).isNull();
    }

    @Test
    void toResource_mapsInstrumentAndEnvelopeFields() {
        var debit = new TransactionEntry("acc-1", TransactionEntryType.DEBIT,
                new MonetaryAmount(BigDecimal.TEN, "USD"), null, "Checking",
                "instr-1", new BigDecimal("5"), "PETR4", "env-1");
        var transaction = new Transaction("txn-id", LocalDate.now(), Instant.now(), "Buy stock", List.of(), List.of(debit));

        var result = mapper.toResource(transaction);

        var entry = result.getEntries().getFirst();
        assertThat(entry.getInstrumentId()).isEqualTo("instr-1");
        assertThat(entry.getQuantity()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(entry.getInstrumentSymbol()).isEqualTo("PETR4");
        assertThat(entry.getEnvelopeId()).isEqualTo("env-1");
    }
}
