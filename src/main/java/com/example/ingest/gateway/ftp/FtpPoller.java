package com.example.ingest.gateway.ftp;

import com.example.ingest.gateway.EventPublisher;
import com.example.ingest.gateway.GatewayEvent;
import com.example.ingest.gateway.GatewayMetrics;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.namespace.CommonPayload;
import com.example.ingest.namespace.CommonPayloadReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Polls the file source and bridges NDJSON lines into JetStream. Line
 * contract: a JSON object with textual {@code eventType} and
 * {@code namespace} plus the common payload fields (eventId, occurredAt).
 * A publish failure leaves the file in processing/ for manual redrive.
 */
@Component
@Profile("gateway")
@ConditionalOnProperty(prefix = "app.gateway.ftp", name = "enabled", havingValue = "true")
public class FtpPoller {

    private static final Logger log = LoggerFactory.getLogger(FtpPoller.class);

    private final FileSourceClient files;
    private final EventPublisher publisher;
    private final CommonPayloadReader payloadReader;
    private final ObjectMapper objectMapper;
    private final GatewayMetrics metrics;

    public FtpPoller(FileSourceClient files, EventPublisher publisher,
                     CommonPayloadReader payloadReader, ObjectMapper objectMapper,
                     GatewayMetrics metrics) {
        this.files = files;
        this.publisher = publisher;
        this.payloadReader = payloadReader;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.gateway.ftp.poll-interval:30s}")
    public void poll() throws IOException {
        List<FileSourceClient.RemoteFile> inbox = files.listInbox();
        metrics.scanLag(scanLagSeconds(inbox, Instant.now()));
        for (FileSourceClient.RemoteFile file : inbox) {
            if (!files.claim(file)) {
                continue; // another gateway instance won the rename race
            }
            try {
                processClaimed(file);
            } catch (Exception e) {
                metrics.file("failed");
                log.error("file {} failed mid-processing, left in processing/ for manual redrive",
                        file.name(), e);
            }
        }
    }

    static long scanLagSeconds(List<FileSourceClient.RemoteFile> inbox, Instant now) {
        return inbox.stream()
                .map(FileSourceClient.RemoteFile::modifiedAt)
                .min(Instant::compareTo)
                .map(oldest -> Math.max(0, now.getEpochSecond() - oldest.getEpochSecond()))
                .orElse(0L);
    }

    private void processClaimed(FileSourceClient.RemoteFile file) throws IOException {
        int published = 0;
        int invalid = 0;
        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(files.read(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                GatewayEvent event;
                try {
                    event = parseLine(line);
                } catch (IllegalArgumentException e) {
                    invalid++;
                    log.warn("file {} line {}: {}", file.name(), lineNumber, e.getMessage());
                    continue;
                }
                publisher.publish(event);
                published++;
            }
        }
        if (invalid > 0) {
            files.error(file);
            metrics.file("error");
        } else {
            files.archive(file);
            metrics.file("archived");
        }
        log.info("file {} -> {} events published, {} invalid lines", file.name(), published, invalid);
    }

    private GatewayEvent parseLine(String line) {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        JsonNode node;
        try {
            node = objectMapper.readTree(bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("line is not valid JSON", e);
        }
        JsonNode eventType = node.path("eventType");
        if (!eventType.isTextual() || eventType.asText().isBlank()) {
            throw new IllegalArgumentException("missing 'eventType'");
        }
        JsonNode namespace = node.path("namespace");
        if (!namespace.isTextual() || namespace.asText().isBlank()) {
            throw new IllegalArgumentException("missing 'namespace'");
        }
        CommonPayload payload = payloadReader.read(bytes);
        return new GatewayEvent(SourceKey.SOURCE_FTP, eventType.asText(), namespace.asText(),
                payload.dedupKey(), payload.body());
    }
}
