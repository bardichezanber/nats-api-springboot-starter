package com.example.ingest.worker.source;

import org.springframework.stereotype.Component;

/**
 * Source B has an upstream contract: the message names its namespace
 * explicitly in the {@code X-Namespace} header.
 */
@Component
public class SourceBNamespaceResolver {

    public String resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new IllegalArgumentException("source B message is missing the X-Namespace header");
        }
        return headerValue.trim();
    }
}
