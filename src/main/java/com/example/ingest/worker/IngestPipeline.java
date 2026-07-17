package com.example.ingest.worker;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.NamespacePolicy;
import com.example.ingest.namespace.NamespaceRegistry;
import com.example.ingest.record.IngestedRecord;
import com.example.ingest.record.IngestedRecordRepository;
import com.example.ingest.worker.ledger.IngestLedgerEntry;
import com.example.ingest.worker.ledger.IngestLedgerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * The single funnel every source goes through:
 * resolve namespace → dedup via ledger → namespace-specific parse → save.
 *
 * <p>Ledger entry and record are written in the same transaction. The dedup
 * check is a plain read; under a concurrent redelivery the ledger's primary
 * key makes the losing transaction fail at commit, the message is NAKed and
 * redelivered, and the retry then sees the entry and reports DUPLICATE.
 */
@Service
public class IngestPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestPipeline.class);

    private final NamespaceRegistry registry;
    private final IngestLedgerRepository ledgerRepository;
    private final IngestedRecordRepository recordRepository;
    private final IngestMetrics metrics;

    public IngestPipeline(NamespaceRegistry registry,
                          IngestLedgerRepository ledgerRepository,
                          IngestedRecordRepository recordRepository,
                          IngestMetrics metrics) {
        this.registry = registry;
        this.ledgerRepository = ledgerRepository;
        this.recordRepository = recordRepository;
        this.metrics = metrics;
    }

    @Transactional
    public IngestResult ingest(String namespaceKey, CommonEnvelope envelope) {
        IngestResult result = doIngest(namespaceKey, envelope);
        metrics.result(envelope.source(), namespaceKey, result);
        return result;
    }

    private IngestResult doIngest(String namespaceKey, CommonEnvelope envelope) {
        Optional<NamespacePolicy> policy = registry.find(namespaceKey);
        if (policy.isEmpty()) {
            if (registry.isKnown(namespaceKey)) {
                log.debug("dropping message for disabled namespace {} ({} {})",
                        namespaceKey, envelope.source(), envelope.dedupKey());
                return IngestResult.NAMESPACE_DISABLED;
            }
            log.warn("dropping message for unknown namespace {} ({} {})",
                    namespaceKey, envelope.source(), envelope.dedupKey());
            return IngestResult.UNKNOWN_NAMESPACE;
        }

        String ledgerKey = envelope.source().name() + ":" + envelope.dedupKey();
        if (ledgerRepository.existsById(ledgerKey)) {
            return IngestResult.DUPLICATE;
        }
        ledgerRepository.save(new IngestLedgerEntry(ledgerKey, Instant.now()));

        JsonNode payload = policy.get().parse(envelope);
        recordRepository.save(IngestedRecord.of(
                namespaceKey,
                envelope.source(),
                envelope.eventType(),
                envelope.dedupKey(),
                payload.toString(),
                envelope.occurredAt(),
                Instant.now()));
        return IngestResult.SAVED;
    }
}
