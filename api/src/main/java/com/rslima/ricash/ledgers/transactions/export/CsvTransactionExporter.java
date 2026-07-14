package com.rslima.ricash.ledgers.transactions.export;

import com.rslima.ricash.ledgers.transactions.TransactionExportRow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders export rows as RFC 4180 CSV: one line per transaction entry, CRLF
 * line endings, fields quoted only when they contain a comma, quote or line
 * break. The output is UTF-8 prefixed with a BOM so Excel decodes non-ASCII
 * descriptions correctly.
 */
public final class CsvTransactionExporter {

    private static final String BOM = "\uFEFF";
    private static final String HEADER = "transaction_id,entry_id,date,description,account_id,account_name,"
            + "entry_type,amount,currency,to_amount,to_currency,instrument_symbol,quantity,envelope_id";

    private CsvTransactionExporter() {
    }

    public static byte[] generate(List<TransactionExportRow> rows) {
        final var csv = new StringBuilder(BOM);
        csv.append(HEADER).append("\r\n");
        for (var row : rows) {
            csv.append(String.join(",",
                            field(row.transactionId()),
                            field(row.entryId()),
                            field(row.date().toString()),
                            field(row.description()),
                            field(row.accountId()),
                            field(row.accountName()),
                            field(row.type().name()),
                            number(row.amount()),
                            field(row.currency()),
                            number(row.toAmount()),
                            field(row.toCurrency()),
                            field(row.instrumentSymbol()),
                            number(row.quantity()),
                            field(row.envelopeId())))
                    .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String field(String value) {
        if (value == null) {
            return "";
        }
        var text = value;
        // Neutralize spreadsheet formula injection (OWASP): a leading trigger
        // character would make Excel/LibreOffice evaluate the cell. Applies to
        // text fields only — ids, dates and enum names never start with one,
        // and number() keeps legitimate negative amounts untouched.
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\r") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
