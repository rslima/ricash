package com.rslima.ricash.ledgers.exchangerates;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ExchangeRateMapper {
    ExchangeRateResource toResource(ExchangeRate exchangeRate);
}
