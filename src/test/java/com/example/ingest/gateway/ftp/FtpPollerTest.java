package com.example.ingest.gateway.ftp;

import com.example.ingest.gateway.EventPublisher;
import com.example.ingest.gateway.GatewayEvent;
import com.example.ingest.gateway.GatewayMetrics;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.source.CommonPayloadReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FtpPollerTest {

    static class FakeFiles implements FileSourceClient {
        final Map<String, String> inbox = new LinkedHashMap<>();
        final List<String> archived = new ArrayList<>();
        final List<String> errored = new ArrayList<>();
        boolean claimable = true;

        @Override
        public List<RemoteFile> listInbox() {
            return inbox.keySet().stream()
                    .map(name -> new RemoteFile(name, Instant.parse("2026-01-01T00:00:00Z")))
                    .toList();
        }

        @Override
        public boolean claim(RemoteFile file) {
            return claimable;
        }

        @Override
        public InputStream read(RemoteFile file) {
            return new ByteArrayInputStream(inbox.get(file.name()).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void archive(RemoteFile file) {
            archived.add(file.name());
        }

        @Override
        public void error(RemoteFile file) {
            errored.add(file.name());
        }
    }

    private final FakeFiles files = new FakeFiles();
    private final List<GatewayEvent> published = new ArrayList<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private FtpPoller poller(EventPublisher publisher) {
        ObjectMapper mapper = new ObjectMapper();
        return new FtpPoller(files, publisher, new CommonPayloadReader(mapper), mapper,
                new GatewayMetrics(registry));
    }

    private static final String VALID_LINE =
            "{\"eventType\":\"orders.created\",\"namespace\":\"alpha\","
                    + "\"eventId\":\"f-1\",\"occurredAt\":\"2026-01-01T00:00:00Z\",\"data\":{\"amount\":1}}";
    private static final String VALID_LINE_2 =
            "{\"eventType\":\"orders.created\",\"namespace\":\"beta\","
                    + "\"eventId\":\"f-2\",\"occurredAt\":\"2026-01-01T00:01:00Z\",\"data\":{\"amount\":2}}";

    @Test
    void publishesEveryLineAndArchivesTheFile() throws IOException {
        files.inbox.put("batch-1.ndjson", VALID_LINE + "\n" + VALID_LINE_2 + "\n");

        poller(published::add).poll();

        assertThat(published).hasSize(2);
        assertThat(published.get(0).source()).isEqualTo(SourceKey.SOURCE_FTP);
        assertThat(published.get(0).eventType()).isEqualTo("orders.created");
        assertThat(published.get(0).namespaceKey()).isEqualTo("alpha");
        assertThat(published.get(0).dedupKey()).isEqualTo("f-1");
        assertThat(published.get(1).dedupKey()).isEqualTo("f-2");
        assertThat(files.archived).containsExactly("batch-1.ndjson");
        assertThat(files.errored).isEmpty();
    }

    @Test
    void fileWithAnInvalidLineGoesToErrorButValidLinesStillPublish() throws IOException {
        files.inbox.put("batch-2.ndjson", VALID_LINE + "\nnot json\n");

        poller(published::add).poll();

        assertThat(published).hasSize(1);
        assertThat(files.errored).containsExactly("batch-2.ndjson");
        assertThat(files.archived).isEmpty();
        assertThat(registry.get("gateway.ftp.files").tag("outcome", "error").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void lostClaimRaceSkipsTheFile() throws IOException {
        files.inbox.put("batch-3.ndjson", VALID_LINE);
        files.claimable = false;

        poller(published::add).poll();

        assertThat(published).isEmpty();
        assertThat(files.archived).isEmpty();
        assertThat(files.errored).isEmpty();
    }

    @Test
    void publishFailureLeavesTheFileInProcessing() throws IOException {
        files.inbox.put("batch-4.ndjson", VALID_LINE);

        poller(event -> {
            throw new IllegalStateException("nats down");
        }).poll();

        assertThat(files.archived).isEmpty();
        assertThat(files.errored).isEmpty();
        assertThat(registry.get("gateway.ftp.files").tag("outcome", "failed").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void scanLagIsAgeOfOldestInboxFile() {
        Instant now = Instant.parse("2026-01-01T00:05:00Z");
        List<FileSourceClient.RemoteFile> inbox = List.of(
                new FileSourceClient.RemoteFile("new", Instant.parse("2026-01-01T00:04:00Z")),
                new FileSourceClient.RemoteFile("old", Instant.parse("2026-01-01T00:00:00Z")));

        assertThat(FtpPoller.scanLagSeconds(inbox, now)).isEqualTo(300);
        assertThat(FtpPoller.scanLagSeconds(List.of(), now)).isZero();
    }
}
