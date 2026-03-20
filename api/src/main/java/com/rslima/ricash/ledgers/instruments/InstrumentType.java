package com.rslima.ricash.ledgers.instruments;

/**
 * Types of financial instruments that can be tracked.
 */
public enum InstrumentType {
    STOCK,           // Individual stocks (e.g., PETR4, AAPL)
    ETF,             // Exchange-traded funds
    TREASURY_BOND,   // Government bonds (e.g., Tesouro Direto)
    FIXED_INCOME,    // Fixed income instruments (e.g., CDB, LCI, LCA)
    FUND             // Investment funds
}
