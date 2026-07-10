package com.rslima.ricash.ledgers.envelopes;

import java.util.List;
import java.util.Map;

public interface EnvelopeAccountMappingRepository {
    List<EnvelopeAccountMapping> findByEnvelopeId(String envelopeId);

    void setMappingsForEnvelope(String envelopeId, List<String> accountIds);

    void deleteByEnvelopeId(String envelopeId);

    Map<String, String> findAllMappingsForLedger(String ledgerId);
}
