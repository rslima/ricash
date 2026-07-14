package com.rslima.ricash.ledgers.transactions.export;

import com.rslima.ricash.ledgers.accounts.Account;
import com.rslima.ricash.ledgers.accounts.AccountType;
import com.rslima.ricash.ledgers.transactions.TransactionExportRow;
import com.rslima.ricash.ledgers.transactions.TransactionEntryType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders one OFX 1.03 SGML bank-statement document with one {@code STMTRS}
 * block per account. SGML style: leaf elements are not closed, aggregates are.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Sign: a DEBIT entry on the statement account yields a positive
 *       {@code TRNAMT}, a CREDIT a negative one, for every account type. For
 *       assets that is the usual money-in/money-out view; for liabilities a
 *       payment shows positive and new debt negative, like card statements.</li>
 *   <li>{@code TRNTYPE} is keyed to the sign of {@code TRNAMT}, not to the
 *       ledger entry type.</li>
 *   <li>{@code FITID} is the entry id (UUIDv7), stable across exports so
 *       importers can detect duplicates.</li>
 *   <li>{@code ENCODING:UTF-8} deviates from the strict 1.03 spec (USASCII)
 *       deliberately: descriptions are not restricted to Latin-1 and modern
 *       importers accept UTF-8.</li>
 *   <li>{@code ACCTTYPE} maps ASSET to CHECKING and LIABILITY to CREDITLINE —
 *       the schema cannot distinguish credit cards (which strictly belong in a
 *       {@code CCSTMTRS} message set) from other liabilities.</li>
 * </ul>
 */
public final class OfxTransactionExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String CRLF = "\r\n";

    private OfxTransactionExporter() {
    }

    /**
     * @param statementAccounts accounts to emit one statement each for
     * @param rowsByAccountId   export rows keyed by the account the entry posts to
     * @param from              requested range start, nullable
     * @param to                requested range end (inclusive), nullable
     * @param balancesByAccountId as-of balances (debits minus credits) for {@code LEDGERBAL}
     * @param now               generation time, injected for deterministic output
     */
    public static byte[] generate(List<Account> statementAccounts,
                                  Map<String, List<TransactionExportRow>> rowsByAccountId,
                                  LocalDate from,
                                  LocalDate to,
                                  Map<String, BigDecimal> balancesByAccountId,
                                  Instant now) {
        final var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        final var ofx = new StringBuilder();

        ofx.append("OFXHEADER:100").append(CRLF)
                .append("DATA:OFXSGML").append(CRLF)
                .append("VERSION:103").append(CRLF)
                .append("SECURITY:NONE").append(CRLF)
                .append("ENCODING:UTF-8").append(CRLF)
                .append("CHARSET:NONE").append(CRLF)
                .append("COMPRESSION:NONE").append(CRLF)
                .append("OLDFILEUID:NONE").append(CRLF)
                .append("NEWFILEUID:NONE").append(CRLF)
                .append(CRLF);

        ofx.append("<OFX>").append(CRLF)
                .append("<SIGNONMSGSRSV1>").append(CRLF)
                .append("<SONRS>").append(CRLF)
                .append("<STATUS><CODE>0<SEVERITY>INFO</STATUS>").append(CRLF)
                .append("<DTSERVER>").append(DATE_TIME.format(now.atOffset(ZoneOffset.UTC))).append(CRLF)
                .append("<LANGUAGE>ENG").append(CRLF)
                .append("</SONRS>").append(CRLF)
                .append("</SIGNONMSGSRSV1>").append(CRLF)
                .append("<BANKMSGSRSV1>").append(CRLF);

        final var orderedAccounts = statementAccounts.stream()
                .sorted(Comparator.comparing(Account::name).thenComparing(Account::id))
                .toList();
        for (var account : orderedAccounts) {
            appendStatement(ofx, account,
                    rowsByAccountId.getOrDefault(account.id(), List.of()),
                    from, to,
                    balancesByAccountId.getOrDefault(account.id(), BigDecimal.ZERO),
                    today);
        }

        ofx.append("</BANKMSGSRSV1>").append(CRLF)
                .append("</OFX>").append(CRLF);

        return ofx.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendStatement(StringBuilder ofx, Account account, List<TransactionExportRow> rows,
                                        LocalDate from, LocalDate to, BigDecimal balance, LocalDate today) {
        // Empty-rows fallbacks prefer the opposite explicit bound over today so
        // a statement with no activity never emits DTSTART after DTEND.
        final var start = from != null ? from
                : rows.stream().map(TransactionExportRow::date).min(Comparator.naturalOrder())
                        .orElse(to != null ? to : today);
        final var end = to != null ? to
                : rows.stream().map(TransactionExportRow::date).max(Comparator.naturalOrder())
                        .orElse(from != null ? from : today);

        ofx.append("<STMTTRNRS>").append(CRLF)
                .append("<TRNUID>").append(account.id()).append(CRLF)
                .append("<STATUS><CODE>0<SEVERITY>INFO</STATUS>").append(CRLF)
                .append("<STMTRS>").append(CRLF)
                .append("<CURDEF>").append(account.currency()).append(CRLF)
                .append("<BANKACCTFROM>").append(CRLF)
                .append("<BANKID>RICASH").append(CRLF)
                .append("<ACCTID>").append(acctId(account)).append(CRLF)
                .append("<ACCTTYPE>").append(acctType(account.type())).append(CRLF)
                .append("</BANKACCTFROM>").append(CRLF)
                .append("<BANKTRANLIST>").append(CRLF)
                .append("<DTSTART>").append(DATE.format(start)).append(CRLF)
                .append("<DTEND>").append(DATE.format(end)).append(CRLF);

        for (var row : rows) {
            appendTransaction(ofx, account, row);
        }

        ofx.append("</BANKTRANLIST>").append(CRLF)
                .append("<LEDGERBAL>").append(CRLF)
                .append("<BALAMT>").append(money(balance)).append(CRLF)
                .append("<DTASOF>").append(DATE.format(to != null ? to : today)).append(CRLF)
                .append("</LEDGERBAL>").append(CRLF)
                .append("</STMTRS>").append(CRLF)
                .append("</STMTTRNRS>").append(CRLF);
    }

    private static void appendTransaction(StringBuilder ofx, Account account, TransactionExportRow row) {
        final var amount = signedAmountIn(account, row);
        if (amount == null) {
            // Entry in neither the account currency nor converted to it (its
            // account's currency was changed after booking): the balance query
            // counts it as 0, so omit it here too to keep the statement's
            // transactions consistent with LEDGERBAL.
            return;
        }
        final var description = row.description() != null ? row.description() : "";

        ofx.append("<STMTTRN>").append(CRLF)
                .append("<TRNTYPE>").append(amount.signum() < 0 ? "DEBIT" : "CREDIT").append(CRLF)
                .append("<DTPOSTED>").append(DATE.format(row.date())).append(CRLF)
                .append("<TRNAMT>").append(money(amount)).append(CRLF)
                .append("<FITID>").append(row.entryId()).append(CRLF)
                .append("<NAME>").append(escape(truncate(description, 32))).append(CRLF)
                .append("<MEMO>").append(escape(description)).append(CRLF)
                .append("</STMTTRN>").append(CRLF);
    }

    /**
     * The entry amount expressed in the statement account's currency: the
     * converted amount when the entry was booked in another currency, else the
     * original amount. Returns null for an entry matching neither currency
     * (possible when the account's currency was edited after booking), the same
     * entries the balance query counts as 0.
     */
    private static BigDecimal signedAmountIn(Account account, TransactionExportRow row) {
        final BigDecimal amount;
        if (account.currency().equals(row.currency())) {
            amount = row.amount();
        } else if (account.currency().equals(row.toCurrency()) && row.toAmount() != null) {
            amount = row.toAmount();
        } else {
            return null;
        }
        return row.type() == TransactionEntryType.DEBIT ? amount : amount.negate();
    }

    /**
     * ACCTID is limited to 22 characters (A-22) in OFX 1.03, but account ids
     * are 36-char UUIDs. The UUID's 128 bits encode to exactly 22 base64url
     * characters, keeping the id unique and stable across exports (importers
     * key duplicate detection on BANKACCTFROM). Non-UUID ids (test fixtures)
     * are truncated instead.
     */
    private static String acctId(Account account) {
        try {
            final var uuid = UUID.fromString(account.id());
            final var bits = ByteBuffer.allocate(16)
                    .putLong(uuid.getMostSignificantBits())
                    .putLong(uuid.getLeastSignificantBits())
                    .array();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bits);
        } catch (IllegalArgumentException e) {
            return truncate(account.id(), 22);
        }
    }

    private static String acctType(AccountType type) {
        return type == AccountType.LIABILITY ? "CREDITLINE" : "CHECKING";
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        // Never cut a surrogate pair in half: an unpaired high surrogate would
        // be written as a replacement byte.
        final var cut = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, cut);
    }

    private static String escape(String value) {
        // Line breaks would split the CRLF-delimited leaf value across lines.
        return value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
