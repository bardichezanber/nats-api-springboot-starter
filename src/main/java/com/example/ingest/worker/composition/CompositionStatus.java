package com.example.ingest.worker.composition;

/**
 * The whole composition state machine: {@code PENDING -> COMPOSED} when the
 * last required part arrives, {@code PENDING -> EXPIRED} when the deadline
 * passes first. Terminal states never change.
 */
public enum CompositionStatus {
    PENDING,
    COMPOSED,
    EXPIRED
}
