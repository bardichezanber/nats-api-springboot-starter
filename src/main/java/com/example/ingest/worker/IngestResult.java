package com.example.ingest.worker;

/**
 * Outcome of running one message through the pipeline. Every value maps to
 * an ACK on the NATS side — including DISABLED/UNKNOWN, because namespace
 * enablement is a switch (not enabled = business-wise not collected), so
 * redelivery would never help.
 */
public enum IngestResult {
    SAVED,
    DUPLICATE,
    NAMESPACE_DISABLED,
    UNKNOWN_NAMESPACE,
    /** Composition part stored, waiting for the rest of its composition. */
    BUFFERED
}
