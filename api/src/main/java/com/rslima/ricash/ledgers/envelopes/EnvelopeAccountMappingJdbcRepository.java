package com.rslima.ricash.ledgers.envelopes;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class EnvelopeAccountMappingJdbcRepository implements EnvelopeAccountMappingRepository {
    private final JdbcClient jdbcClient;

    record DBMapping(String id, String envelopeId, String accountId) {
    }

    @Override
    public List<EnvelopeAccountMapping> findByEnvelopeId(String envelopeId) {
        return jdbcClient.sql("""
                        SELECT id, envelope_id, account_id
                        FROM envelope_account_mappings
                        WHERE envelope_id = :envelopeId
                        """)
                .param("envelopeId", envelopeId)
                .query(DBMapping.class)
                .list()
                .stream()
                .map(db -> new EnvelopeAccountMapping(db.id(), db.envelopeId(), db.accountId()))
                .toList();
    }

    @Override
    public Optional<EnvelopeAccountMapping> findByAccountId(String accountId) {
        return jdbcClient.sql("""
                        SELECT id, envelope_id, account_id
                        FROM envelope_account_mappings
                        WHERE account_id = :accountId
                        """)
                .param("accountId", accountId)
                .query(DBMapping.class)
                .optional()
                .map(db -> new EnvelopeAccountMapping(db.id(), db.envelopeId(), db.accountId()));
    }

    @Override
    public void setMappingsForEnvelope(String envelopeId, List<String> accountIds) {
        // First, remove existing mappings for this envelope
        jdbcClient.sql("""
                        DELETE FROM envelope_account_mappings WHERE envelope_id = :envelopeId
                        """)
                .param("envelopeId", envelopeId)
                .update();

        // Also remove any mappings for these accounts (since each account can only map to one envelope)
        if (!accountIds.isEmpty()) {
            for (String accountId : accountIds) {
                jdbcClient.sql("""
                                DELETE FROM envelope_account_mappings WHERE account_id = :accountId
                                """)
                        .param("accountId", accountId)
                        .update();
            }
        }

        // Insert new mappings
        for (String accountId : accountIds) {
            var id = UuidCreator.getTimeOrderedEpoch().toString();
            jdbcClient.sql("""
                            INSERT INTO envelope_account_mappings (id, envelope_id, account_id)
                            VALUES (:id, :envelopeId, :accountId)
                            """)
                    .param("id", id)
                    .param("envelopeId", envelopeId)
                    .param("accountId", accountId)
                    .update();
        }
    }

    @Override
    public void deleteByEnvelopeId(String envelopeId) {
        jdbcClient.sql("""
                        DELETE FROM envelope_account_mappings WHERE envelope_id = :envelopeId
                        """)
                .param("envelopeId", envelopeId)
                .update();
    }

    @Override
    public Map<String, String> findAllMappingsForLedger(String ledgerId) {
        var mappings = jdbcClient.sql("""
                        SELECT m.id, m.envelope_id, m.account_id
                        FROM envelope_account_mappings m
                        JOIN envelopes e ON m.envelope_id = e.id
                        WHERE e.ledger_id = :ledgerId
                        """)
                .param("ledgerId", ledgerId)
                .query(DBMapping.class)
                .list();

        Map<String, String> result = new HashMap<>();
        for (var mapping : mappings) {
            result.put(mapping.accountId(), mapping.envelopeId());
        }
        return result;
    }
}
