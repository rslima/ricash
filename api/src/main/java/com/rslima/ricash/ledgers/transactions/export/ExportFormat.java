package com.rslima.ricash.ledgers.transactions.export;

/** The file formats a transaction export can be rendered as. */
public enum ExportFormat {
    CSV("csv", "text/csv; charset=UTF-8"),
    OFX("ofx", "application/x-ofx");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static ExportFormat parse(String raw) {
        for (var format : values()) {
            if (format.name().equalsIgnoreCase(raw)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported export format: " + raw);
    }
}
