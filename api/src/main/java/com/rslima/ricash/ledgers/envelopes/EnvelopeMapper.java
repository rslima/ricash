package com.rslima.ricash.ledgers.envelopes;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EnvelopeMapper {

    EnvelopeResource toResource(Envelope envelope);

    List<EnvelopeResource> toEnvelopeResources(List<Envelope> envelopes);

    EnvelopeAllocationResource toResource(EnvelopeAllocation allocation);

    EnvelopeBalanceResource toResource(EnvelopeBalance balance);

    List<EnvelopeBalanceResource> toEnvelopeBalanceResources(List<EnvelopeBalance> balances);
}
