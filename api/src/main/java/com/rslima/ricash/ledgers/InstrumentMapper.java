package com.rslima.ricash.ledgers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InstrumentMapper {
    InstrumentResource toResource(Instrument instrument);
}
