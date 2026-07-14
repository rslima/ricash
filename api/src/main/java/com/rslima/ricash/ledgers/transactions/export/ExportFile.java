package com.rslima.ricash.ledgers.transactions.export;

/** A rendered export ready to be served as a file download. */
public record ExportFile(String filename, String contentType, byte[] content) {
}
