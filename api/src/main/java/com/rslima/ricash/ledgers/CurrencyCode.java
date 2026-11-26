package com.rslima.ricash.ledgers;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A three-letter uppercase currency code such as {@code USD} or {@code BRL}.
 * Shape only, not a lookup against ISO 4217, so codes the providers use
 * (e.g. {@code GBX}) still pass. Null is valid; pair with {@code @NotBlank}
 * for required fields.
 *
 * <p>Requests whose service uppercases the code itself (exchange rates,
 * instruments) accept any case instead, via
 * {@code @Pattern(regexp = CurrencyCode.ANY_CASE, message = CurrencyCode.ANY_CASE_MESSAGE)}.
 */
@Pattern(regexp = "[A-Z]{3}")
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Documented
public @interface CurrencyCode {

    /** Same shape in any letter case, for requests normalized by their service. */
    String ANY_CASE = "[A-Za-z]{3}";

    String ANY_CASE_MESSAGE = "must be a 3-letter currency code, e.g. USD";

    @OverridesAttribute(constraint = Pattern.class, name = "message")
    String message() default "must be a 3-letter uppercase currency code, e.g. USD";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
