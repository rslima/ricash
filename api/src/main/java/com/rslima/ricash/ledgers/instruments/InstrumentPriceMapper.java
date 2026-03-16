package com.rslima.ricash.ledgers.instruments;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InstrumentPriceMapper {

    @Mapping(target = "instrumentSymbol", ignore = true)
    InstrumentPriceResource toResource(InstrumentPrice price);

    default InstrumentPriceResource toResource(InstrumentPrice price, Instrument instrument) {
        InstrumentPriceResource resource = toResource(price);
        if (instrument != null) {
            resource.setInstrumentSymbol(instrument.symbol());
        }
        return resource;
    }
}
