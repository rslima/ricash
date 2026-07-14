package com.rslima.ricash.ledgers.transactions.export;

import com.rslima.ricash.ledgers.transactions.TransactionExportRow;
import com.rslima.ricash.ledgers.transactions.TransactionEntryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTransactionExporterTest {

    private static final String HEADER = "transaction_id,entry_id,date,description,account_id,account_name,"
            + "entry_type,amount,currency,to_amount,to_currency,instrument_symbol,quantity,envelope_id";

    @Test
    void generate_emitsBomHeaderAndCrlfTerminatedRows() {
        var csv = csv(row("Salary", "100.00"));

        assertThat(csv).startsWith("\uFEFF" + HEADER + "\r\n");
        assertThat(csv).endsWith("\r\n");
        assertThat(csv.split("\r\n")).hasSize(2);
    }

    @Test
    void generate_withoutRows_stillEmitsHeader() {
        assertThat(csv()).isEqualTo("\uFEFF" + HEADER + "\r\n");
    }

    @Test
    void generate_writesPlainFieldsUnquoted() {
        var line = csv(row("Salary", "100.00")).split("\r\n")[1];

        assertThat(line).isEqualTo(
                "t1,e1,2026-01-10,Salary,acc-1,Checking,DEBIT,100.00,USD,,,,,");
    }

    @Test
    void generate_quotesAndEscapesSpecialCharacters() {
        var comma = csv(row("Groceries, market", "1.00")).split("\r\n")[1];
        var quote = csv(row("The \"good\" one", "1.00")).split("\r\n")[1];
        var newline = csv(row("Line1\nLine2", "1.00")).split("\r\n", 3)[1];

        assertThat(comma).contains("\"Groceries, market\"");
        assertThat(quote).contains("\"The \"\"good\"\" one\"");
        assertThat(newline).contains("\"Line1");
    }

    @Test
    void generate_roundTripsQuotedNewlineField() {
        var csv = csv(row("Line1\nLine2", "1.00"));

        assertThat(csv).contains("\"Line1\nLine2\"");
    }

    @Test
    void generate_neutralizesSpreadsheetFormulaTriggers() {
        var formula = csv(row("=HYPERLINK(\"http://evil\")", "1.00")).split("\r\n")[1];
        var plus = csv(row("+55 taxi", "1.00")).split("\r\n")[1];
        var negativeAmount = csv(row("Refund", "-1.00")).split("\r\n")[1];

        assertThat(formula).contains("'=HYPERLINK");
        assertThat(plus).contains("'+55 taxi");
        // number() fields keep their sign untouched.
        assertThat(negativeAmount).contains(",-1.00,");
    }

    @Test
    void generate_writesOptionalColumnsWhenPresent() {
        var row = new TransactionExportRow("t1", LocalDate.of(2026, 1, 10), Instant.EPOCH, "Buy stock",
                "e1", "acc-1", "Brokerage", TransactionEntryType.DEBIT,
                new BigDecimal("10.00"), "USD", new BigDecimal("9.00"), "EUR",
                "instr-1", new BigDecimal("0.12345678"), "VWCE", "env-1");

        var line = csv(row).split("\r\n")[1];

        assertThat(line).isEqualTo(
                "t1,e1,2026-01-10,Buy stock,acc-1,Brokerage,DEBIT,10.00,USD,9.00,EUR,VWCE,0.12345678,env-1");
    }

    private static String csv(TransactionExportRow... rows) {
        return new String(CsvTransactionExporter.generate(List.of(rows)), StandardCharsets.UTF_8);
    }

    private static TransactionExportRow row(String description, String amount) {
        return new TransactionExportRow("t1", LocalDate.of(2026, 1, 10), Instant.EPOCH, description,
                "e1", "acc-1", "Checking", TransactionEntryType.DEBIT,
                new BigDecimal(amount), "USD", null, null,
                null, null, null, null);
    }
}
