package com.rslima.ricash.ledgers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EnvelopeAccountMappingRepository {
    List<EnvelopeAccountMapping> findByEnvelopeId(String envelopeId);

    Optional<EnvelopeAccountMapping> findByAccountId(String accountId);

    void setMappingsForEnvelope(String envelopeId, List<String> accountIds);

    void deleteByEnvelopeId(String envelopeId);

    Map<String, String> findAllMappingsForLedger(String ledgerId);
}
