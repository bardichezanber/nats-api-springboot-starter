package com.example.ingest.namespace;

/**
 * The ingest sources. Each source is a distinct NATS flavor with its own
 * event types and its own way of determining the namespace of a message.
 */
public enum SourceKey {
    SOURCE_A("source-a"),
    SOURCE_B("source-b"),
    SOURCE_HTTP("source-http"),
    SOURCE_FTP("source-ftp");

    private final String key;

    SourceKey(String key) {
        this.key = key;
    }

    /** Stable kebab-case key used in config (APP_SOURCES_ENABLED) and metric tags. */
    public String key() {
        return key;
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
