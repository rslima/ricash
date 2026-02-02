package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class EnvelopeJdbcRepository implements EnvelopeRepository {
    private final JdbcClient jdbcClient;

    record DBEnvelope(String id, String ledgerId, String parentEnvelopeId, String name, String description,
                      String currency, String type, String status, Instant createdAt) {
    }

    @Override
    public Page<Envelope> listLedgerEnvelopes(String ledgerId, PageRequest pageRequest) {
        final var dbEnvelopes = jdbcClient.sql("""
                        SELECT
                            e.id,
                            e.ledger_id,
                            e.parent_envelope_id,
                            e.name,
                            e.description,
                            e.currency,
                            e.type,
                            e.status,
                            e.created_at
                        FROM
                            envelopes e
                        WHERE
                            e.ledger_id = :ledgerId
                        ORDER BY e.type, e.name
                        OFFSET :offset
                        LIMIT :limit
                        """)
                .param("ledgerId", ledgerId)
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBEnvelope.class)
                .list();

        final var total = jdbcClient.sql("""
                        SELECT COUNT(*) FROM envelopes WHERE ledger_id = :ledgerId
                        """)
                .param("ledgerId", ledgerId)
                .query(Long.class)
                .single();

        List<Envelope> envelopes = dbEnvelopes.stream()
                .map(this::toEnvelope)
                .toList();

        return new PageImpl<>(envelopes, pageRequest, total);
    }

    @Override
    public Optional<Envelope> findById(String ledgerId, String envelopeId) {
        return jdbcClient.sql("""
                        SELECT
                            e.id,
                            e.ledger_id,
                            e.parent_envelope_id,
                            e.name,
                            e.description,
                            e.currency,
                            e.type,
                            e.status,
                            e.created_at
                        FROM
                            envelopes e
                        WHERE
                            e.ledger_id = :ledgerId AND
                            e.id = :envelopeId
                        """)
                .param("ledgerId", ledgerId)
                .param("envelopeId", envelopeId)
                .query(DBEnvelope.class)
                .optional()
                .map(this::toEnvelope);
    }

    @Override
    public Envelope create(String ledgerId, Envelope envelope) {
        jdbcClient.sql("""
                        INSERT INTO envelopes (id, ledger_id, parent_envelope_id, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, :parentEnvelopeId, :name, :description, :currency, :type, :status, :createdAt)
                        """)
                .param("id", envelope.id())
                .param("ledgerId", ledgerId)
                .param("parentEnvelopeId", envelope.parentEnvelopeId())
                .param("name", envelope.name())
                .param("description", envelope.description())
                .param("currency", envelope.currency())
                .param("type", envelope.type().name())
                .param("status", envelope.status().name())
                .param("createdAt", Timestamp.from(envelope.createdAt()))
                .update();

        return envelope;
    }

    @Override
    public Envelope update(String ledgerId, String envelopeId, String name, String description, EnvelopeType type, String currency, EnvelopeStatus status, String parentEnvelopeId) {
        jdbcClient.sql("""
                        UPDATE envelopes SET name = :name, description = :description, type = :type, currency = :currency, status = :status, parent_envelope_id = :parentEnvelopeId
                        WHERE ledger_id = :ledgerId AND id = :envelopeId
                        """)
                .param("ledgerId", ledgerId)
                .param("envelopeId", envelopeId)
                .param("name", name)
                .param("description", description)
                .param("type", type.name())
                .param("currency", currency)
                .param("status", status.name())
                .param("parentEnvelopeId", parentEnvelopeId)
                .update();

        return findById(ledgerId, envelopeId).orElseThrow();
    }

    @Override
    public List<String> findChildEnvelopeIds(String ledgerId, String envelopeId) {
        return jdbcClient.sql("""
                        SELECT id FROM envelopes WHERE ledger_id = :ledgerId AND parent_envelope_id = :envelopeId
                        """)
                .param("ledgerId", ledgerId)
                .param("envelopeId", envelopeId)
                .query(String.class)
                .list();
    }

    @Override
    public boolean hasAllocations(String envelopeId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM envelope_allocations WHERE envelope_id = :envelopeId
                        """)
                .param("envelopeId", envelopeId)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public boolean hasTransactionEntries(String envelopeId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM transaction_entries WHERE envelope_id = :envelopeId
                        """)
                .param("envelopeId", envelopeId)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public void delete(String ledgerId, String envelopeId) {
        jdbcClient.sql("""
                        DELETE FROM envelopes WHERE ledger_id = :ledgerId AND id = :envelopeId
                        """)
                .param("ledgerId", ledgerId)
                .param("envelopeId", envelopeId)
                .update();
    }

    private Envelope toEnvelope(DBEnvelope dbEnvelope) {
        return new Envelope(
                dbEnvelope.id(),
                dbEnvelope.name(),
                dbEnvelope.description(),
                dbEnvelope.currency(),
                EnvelopeType.valueOf(dbEnvelope.type()),
                EnvelopeStatus.valueOf(dbEnvelope.status()),
                dbEnvelope.createdAt(),
                dbEnvelope.parentEnvelopeId(),
                new ArrayList<>()
        );
    }
}
