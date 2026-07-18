package com.example.ingest.namespace;

/**
 * NATS/HTTP header names shared by the gateway (which writes them) and the
 * worker consumers (which read them). One definition keeps both sides of the
 * contract in sync.
 */
public final class MessageHeaders {

    /** The caller-declared namespace of an event. */
    public static final String NAMESPACE = "X-Namespace";

    private MessageHeaders() {
    }
}
