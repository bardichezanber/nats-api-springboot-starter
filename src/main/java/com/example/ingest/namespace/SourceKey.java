package com.example.ingest.namespace;

/**
 * The ingest sources. Each source is a distinct NATS flavor with its own
 * event types and its own way of determining the namespace of a message.
 */
public enum SourceKey {
    SOURCE_A("source-a", "src-a.events."),
    SOURCE_B("source-b", "src-b.events."),
    SOURCE_HTTP("source-http", "src-http.events."),
    SOURCE_FTP("source-ftp", "src-ftp.events.");

    private final String key;
    private final String subjectPrefix;

    SourceKey(String key, String subjectPrefix) {
        this.key = key;
        this.subjectPrefix = subjectPrefix;
    }

    /** Stable kebab-case key used in config (APP_SOURCES_ENABLED) and metric tags. */
    public String key() {
        return key;
    }

    /**
     * Canonical NATS subject prefix: messages arrive on
     * {@code <subjectPrefix><eventType>}. The single source of truth —
     * {@code SourceRegistry} rejects config whose subject disagrees, and the
     * gateway publishes with it.
     */
    public String subjectPrefix() {
        return subjectPrefix;
    }

    public static SourceKey fromKey(String key) {
        for (SourceKey value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown source key: " + key);
    }
}
