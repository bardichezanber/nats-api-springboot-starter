package com.example.ingest.namespace;

/**
 * The ingest sources. Each source is a distinct NATS flavor with its own
 * event types and its own way of determining the namespace of a message.
 */
public enum SourceKey {
    SOURCE_A,
    SOURCE_B
}
