package com.rslima.ricash.ledgers.transactions.export;

import com.rslima.ricash.ledgers.accounts.Account;
import com.rslima.ricash.ledgers.accounts.AccountStatus;
import com.rslima.ricash.ledgers.accounts.AccountType;
import com.rslima.ricash.ledgers.transactions.TransactionExportRow;
import com.rslima.ricash.ledgers.transactions.TransactionEntryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OfxTransactionExporterTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:30:00Z");

    @Test
    void generate_emitsSgmlHeaderAndSignonBlock() {
        var ofx = ofx(List.of(asset("checking", "Checking", "USD")), Map.of(), null, null, Map.of());

        assertThat(ofx).startsWith("OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:103\r\n");
        assertThat(ofx).contains("ENCODING:UTF-8");
        assertThat(ofx).contains("\r\n\r\n<OFX>\r\n");
        assertThat(ofx).contains("<DTSERVER>20260714103000\r\n");
        assertThat(ofx).contains("<LANGUAGE>ENG");
        assertThat(ofx).endsWith("</BANKMSGSRSV1>\r\n</OFX>\r\n");
    }

    @Test
    void generate_signsAmountsByEntryTypeAndKeysTrnTypeToSign() {
        var account = asset("checking", "Checking", "USD");
        var rows = List.of(
                row("e1", "checking", TransactionEntryType.DEBIT, "100.00", "USD", "Salary"),
                row("e2", "checking", TransactionEntryType.CREDIT, "40.00", "USD", "Groceries"));

        var ofx = ofx(List.of(account), Map.of("checking", rows), null, null, Map.of());

        assertThat(ofx).contains("<TRNTYPE>CREDIT\r\n<DTPOSTED>20260110\r\n<TRNAMT>100.00\r\n<FITID>e1");
        assertThat(ofx).contains("<TRNTYPE>DEBIT\r\n<DTPOSTED>20260110\r\n<TRNAMT>-40.00\r\n<FITID>e2");
    }

    @Test
    void generate_usesConvertedAmountWhenEntryCurrencyDiffers() {
        var account = asset("eur-cash", "EUR Cash", "EUR");
        var rows = List.of(convertedRow("e1", "eur-cash", TransactionEntryType.DEBIT,
                "10.00", "USD", "9.00", "EUR", "FX top-up"));

        var ofx = ofx(List.of(account), Map.of("eur-cash", rows), null, null, Map.of());

        assertThat(ofx).contains("<TRNAMT>9.00\r\n");
    }

    @Test
    void generate_escapesSgmlSpecialCharactersInNameAndMemo() {
        var account = asset("checking", "Checking", "USD");
        var rows = List.of(row("e1", "checking", TransactionEntryType.DEBIT, "1.00", "USD", "Fish & <chips>"));

        var ofx = ofx(List.of(account), Map.of("checking", rows), null, null, Map.of());

        assertThat(ofx).contains("<NAME>Fish &amp; &lt;chips&gt;\r\n");
        assertThat(ofx).contains("<MEMO>Fish &amp; &lt;chips&gt;\r\n");
    }

    @Test
    void generate_truncatesNameTo32CharactersButKeepsFullMemo() {
        var longDescription = "A very long transaction description that exceeds the limit";
        var account = asset("checking", "Checking", "USD");
        var rows = List.of(row("e1", "checking", TransactionEntryType.DEBIT, "1.00", "USD", longDescription));

        var ofx = ofx(List.of(account), Map.of("checking", rows), null, null, Map.of());

        assertThat(ofx).contains("<NAME>" + longDescription.substring(0, 32) + "\r\n");
        assertThat(ofx).contains("<MEMO>" + longDescription + "\r\n");
    }

    @Test
    void generate_emitsOneStatementPerAccountOrderedByName() {
        var savings = asset("savings", "Savings", "USD");
        var card = liability("card", "Credit Card", "EUR");

        var ofx = ofx(List.of(savings, card), Map.of(), null, null, Map.of());

        assertThat(ofx.indexOf("<ACCTID>card")).isLessThan(ofx.indexOf("<ACCTID>savings"));
        assertThat(ofx).contains("<TRNUID>card\r\n");
        assertThat(ofx).contains("<CURDEF>EUR\r\n<BANKACCTFROM>\r\n<BANKID>RICASH\r\n<ACCTID>card\r\n<ACCTTYPE>CREDITLINE");
        assertThat(ofx).contains("<CURDEF>USD\r\n<BANKACCTFROM>\r\n<BANKID>RICASH\r\n<ACCTID>savings\r\n<ACCTTYPE>CHECKING");
    }

    @Test
    void generate_usesRequestedRangeForTransactionListBounds() {
        var account = asset("checking", "Checking", "USD");

        var ofx = ofx(List.of(account), Map.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), Map.of());

        assertThat(ofx).contains("<DTSTART>20260101\r\n<DTEND>20260630\r\n");
        assertThat(ofx).contains("<DTASOF>20260630\r\n");
    }

    @Test
    void generate_fallsBackToEntryDatesThenTodayForBounds() {
        var account = asset("checking", "Checking", "USD");
        var rows = List.of(
                row("e1", "checking", TransactionEntryType.DEBIT, "1.00", "USD", "First", LocalDate.of(2026, 2, 3)),
                row("e2", "checking", TransactionEntryType.DEBIT, "1.00", "USD", "Last", LocalDate.of(2026, 3, 4)));

        var withRows = ofx(List.of(account), Map.of("checking", rows), null, null, Map.of());
        var withoutRows = ofx(List.of(account), Map.of(), null, null, Map.of());

        assertThat(withRows).contains("<DTSTART>20260203\r\n<DTEND>20260304\r\n");
        assertThat(withRows).contains("<DTASOF>20260714\r\n");
        assertThat(withoutRows).contains("<DTSTART>20260714\r\n<DTEND>20260714\r\n");
    }

    @Test
    void generate_writesLedgerBalanceFromSuppliedBalances() {
        var account = asset("checking", "Checking", "USD");

        var ofx = ofx(List.of(account), Map.of(), null, null, Map.of("checking", new BigDecimal("125.00")));

        assertThat(ofx).contains("<LEDGERBAL>\r\n<BALAMT>125.00\r\n");
    }

    @Test
    void generate_defaultsLedgerBalanceToZero() {
        var account = asset("checking", "Checking", "USD");

        var ofx = ofx(List.of(account), Map.of(), null, null, Map.of());

        assertThat(ofx).contains("<BALAMT>0.00\r\n");
    }

    @Test
    void generate_normalizesLineBreaksInNameAndMemo() {
        var account = asset("checking", "Checking", "USD");
        var rows = List.of(row("e1", "checking", TransactionEntryType.DEBIT, "1.00", "USD", "Rent\nJuly\r\nfirst"));

        var ofx = ofx(List.of(account), Map.of("checking", rows), null, null, Map.of());

        assertThat(ofx).contains("<MEMO>Rent July first\r\n");
        assertThat(ofx).contains("<NAME>Rent July first\r\n");
    }

    @Test
    void generate_truncationNeverSplitsASurrogatePair() {
        var thirtyOneChars = "0123456789012345678901234567890";
        var account = asset("checking", "Checking", "USD");
        var rows = List.of(row("e1", "checking", TransactionEntryType.DEBIT, "1.00", "USD", thirtyOneChars + "🍕"));

        var ofx = ofx(List.of(account), Map.of("checking", rows), null, null, Map.of());

        // The pizza emoji's surrogate pair straddles the 32-code-unit cut; the
        // whole pair must be dropped rather than emitting a replacement char.
        assertThat(ofx).contains("<NAME>" + thirtyOneChars + "\r\n");
        assertThat(ofx).doesNotContain("<NAME>" + thirtyOneChars + "?");
    }

    @Test
    void generate_skipsEntriesMatchingNeitherAccountNorConvertedCurrency() {
        // Bookable state: the account's currency was edited to EUR after a
        // USD entry with no conversion was recorded.
        var account = asset("cash", "Cash", "EUR");
        var rows = List.of(
                row("e1", "cash", TransactionEntryType.DEBIT, "10.00", "USD", "Pre-change entry"),
                row("e2", "cash", TransactionEntryType.DEBIT, "5.00", "EUR", "Post-change entry"));

        var ofx = ofx(List.of(account), Map.of("cash", rows), null, null, Map.of());

        assertThat(ofx).doesNotContain("<FITID>e1");
        assertThat(ofx).contains("<FITID>e2");
    }

    @Test
    void generate_encodesUuidAccountIdsTo22CharAcctId() {
        var account = asset("0197b7e2-5555-7777-8888-999999999999", "Checking", "USD");

        var ofx = ofx(List.of(account), Map.of(), null, null, Map.of());

        var acctId = ofx.lines()
                .filter(line -> line.startsWith("<ACCTID>"))
                .map(line -> line.substring("<ACCTID>".length()))
                .findFirst().orElseThrow();
        // OFX 1.03 limits ACCTID to 22 chars; the UUID's 128 bits are exactly
        // 22 base64url characters.
        assertThat(acctId).hasSize(22);
        // TRNUID (A-36) keeps the raw account id.
        assertThat(ofx).contains("<TRNUID>0197b7e2-5555-7777-8888-999999999999\r\n");
    }

    @Test
    void generate_emptyStatementWithPastToBound_neverInvertsDateRange() {
        var account = asset("checking", "Checking", "USD");

        // "today" is 2026-07-14; an inactive statement bounded by a past `to`
        // must not fall back to today for DTSTART.
        var ofx = ofx(List.of(account), Map.of(), null, LocalDate.of(2025, 1, 31), Map.of());

        assertThat(ofx).contains("<DTSTART>20250131\r\n<DTEND>20250131\r\n");
    }

    @Test
    void generate_isDeterministicForFixedInstant() {
        var account = asset("checking", "Checking", "USD");
        var rows = Map.of("checking",
                List.of(row("e1", "checking", TransactionEntryType.DEBIT, "1.00", "USD", "Salary")));

        var first = ofx(List.of(account), rows, null, null, Map.of());
        var second = ofx(List.of(account), rows, null, null, Map.of());

        assertThat(first).isEqualTo(second);
    }

    private static String ofx(List<Account> accounts, Map<String, List<TransactionExportRow>> rowsByAccountId,
                              LocalDate from, LocalDate to, Map<String, BigDecimal> balances) {
        return new String(
                OfxTransactionExporter.generate(accounts, rowsByAccountId, from, to, balances, NOW),
                StandardCharsets.UTF_8);
    }

    private static Account asset(String id, String name, String currency) {
        return account(id, name, currency, AccountType.ASSET);
    }

    private static Account liability(String id, String name, String currency) {
        return account(id, name, currency, AccountType.LIABILITY);
    }

    private static Account account(String id, String name, String currency, AccountType type) {
        return new Account(id, id, name, null, currency, type, AccountStatus.ACTIVE,
                BigDecimal.ZERO, Instant.EPOCH, null, List.of());
    }

    private static TransactionExportRow row(String entryId, String accountId, TransactionEntryType type,
                                            String amount, String currency, String description) {
        return row(entryId, accountId, type, amount, currency, description, LocalDate.of(2026, 1, 10));
    }

    private static TransactionExportRow row(String entryId, String accountId, TransactionEntryType type,
                                            String amount, String currency, String description, LocalDate date) {
        return new TransactionExportRow("t-" + entryId, date, Instant.EPOCH, description,
                entryId, accountId, "Account " + accountId, type,
                new BigDecimal(amount), currency, null, null,
                null, null, null, null);
    }

    private static TransactionExportRow convertedRow(String entryId, String accountId, TransactionEntryType type,
                                                     String amount, String currency,
                                                     String toAmount, String toCurrency, String description) {
        return new TransactionExportRow("t-" + entryId, LocalDate.of(2026, 1, 10), Instant.EPOCH, description,
                entryId, accountId, "Account " + accountId, type,
                new BigDecimal(amount), currency, new BigDecimal(toAmount), toCurrency,
                null, null, null, null);
    }
}
