package com.rslima.ricash.ledgers.accounts;

/**
 * Lightweight account projection (no balance computation) for callers that
 * only need identity, display name and currency.
 */
public record AccountRef(String id, String name, String currency) {
}
