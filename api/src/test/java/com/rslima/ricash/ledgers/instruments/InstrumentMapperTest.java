package com.rslima.ricash.ledgers.instruments;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentMapperTest {

    private final InstrumentMapper mapper = Mappers.getMapper(InstrumentMapper.class);

    @Test
    void toResource_mapsAllFields() {
        var now = Instant.now();
        var instrument = new Instrument(
                "instr-id", "ledger-id", "PETR4", "Petrobras PN",
                InstrumentType.STOCK, "BRL", "B3", "BRPETRACNPR6",
                InstrumentStatus.ACTIVE, now
        );

        var result = mapper.toResource(instrument);

        assertThat(result.getId()).isEqualTo("instr-id");
        assertThat(result.getLedgerId()).isEqualTo("ledger-id");
        assertThat(result.getSymbol()).isEqualTo("PETR4");
        assertThat(result.getName()).isEqualTo("Petrobras PN");
        assertThat(result.getType()).isEqualTo(InstrumentType.STOCK);
        assertThat(result.getCurrency()).isEqualTo("BRL");
        assertThat(result.getMarket()).isEqualTo("B3");
        assertThat(result.getIsin()).isEqualTo("BRPETRACNPR6");
        assertThat(result.getStatus()).isEqualTo(InstrumentStatus.ACTIVE);
        assertThat(result.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toResource_mapsNullOptionalFields() {
        var instrument = new Instrument(
                "instr-id", "ledger-id", "BTC", "Bitcoin",
                InstrumentType.ETF, "USD", null, null,
                InstrumentStatus.ACTIVE, Instant.now()
        );

        var result = mapper.toResource(instrument);

        assertThat(result.getMarket()).isNull();
        assertThat(result.getIsin()).isNull();
    }

    @Test
    void toResource_mapsInactiveStatus() {
        var instrument = new Instrument(
                "instr-id", "ledger-id", "OLD", "Old Fund",
                InstrumentType.FUND, "BRL", "B3", null,
                InstrumentStatus.INACTIVE, Instant.now()
        );

        var result = mapper.toResource(instrument);

        assertThat(result.getStatus()).isEqualTo(InstrumentStatus.INACTIVE);
    }
}
