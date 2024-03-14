package com.rslima.ricash.ledgers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Transaction(String id, LocalDate date, Instant createdAt, String description, List<TransactionEntry> creditEntries, List<TransactionEntry> debitEntries) {

}
