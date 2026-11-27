package com.rslima.ricash.ledgers;

import com.rslima.ricash.ledgers.accounts.AccountType;
import com.rslima.ricash.ledgers.accounts.CreateAccountRequest;
import com.rslima.ricash.ledgers.transactions.TransactionEntryRequest;
import com.rslima.ricash.ledgers.transactions.TransactionEntryType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyCodeTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "BRL", "GBX"})
    void threeUppercaseLetters_areValid(String currency) {
        assertThat(validator.validate(account(currency))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "US", "USDT", "U$D", " USD"})
    void otherShapes_areRejectedWithAReadableMessage(String currency) {
        Set<ConstraintViolation<CreateAccountRequest>> violations = validator.validate(account(currency));

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath()).hasToString("currency");
            assertThat(v.getMessage()).isEqualTo("must be a 3-letter uppercase currency code, e.g. USD");
        });
    }

    @Test
    void optionalCurrency_mayBeNull() {
        var entry = new TransactionEntryRequest("acc-1", BigDecimal.TEN, "USD", null, null,
                TransactionEntryType.DEBIT, null, null, null);

        assertThat(validator.validate(entry)).isEmpty();
    }

    @Test
    void optionalCurrency_isCheckedWhenPresent() {
        var entry = new TransactionEntryRequest("acc-1", BigDecimal.TEN, "USD", BigDecimal.ONE, "eur",
                TransactionEntryType.DEBIT, null, null, null);

        assertThat(validator.validate(entry)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("toCurrency");
    }

    private static CreateAccountRequest account(String currency) {
        return new CreateAccountRequest("Cash", null, currency, AccountType.ASSET, null);
    }
}
