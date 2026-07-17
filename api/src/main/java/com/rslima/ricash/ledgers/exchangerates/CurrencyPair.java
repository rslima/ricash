package com.rslima.ricash.ledgers.exchangerates;

/** A directed currency pair as stored in exchange_rates (from -> to). */
public record CurrencyPair(String fromCurrency, String toCurrency) {
}
