package com.example.ingest.namespace;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One implementation per namespace, hand-written in code. A namespace owns
 * the logic for turning the common payload into the normalized record
 * payload — namespaces may differ structurally, not just by parameters.
 *
 * <p>To add a namespace: implement this interface as a {@code @Component}
 * and list its key in {@code APP_NAMESPACES_ENABLED} where it should be live.
 */
public interface NamespacePolicy {

    /** Stable key, used in config, the ledger-facing pipeline, and API paths. */
    String key();

    /** Parse the common payload into the normalized record payload. */
    JsonNode parse(CommonEnvelope envelope);
}
