package com.rslima.ricash.ledgers.transactions.export;

public interface TransactionExportService {
    ExportFile export(String userId, String ledgerSlug, ExportRequest request);
}
