package com.example.ingest.worker.source;

import org.springframework.stereotype.Component;

/**
 * Source HTTP messages come from the gateway, which copies the caller's
 * declared namespace into the {@code X-Namespace} header.
 */
@Component
public class SourceHttpNamespaceResolver {

    public String resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new IllegalArgumentException("source HTTP message is missing the X-Namespace header");
        }
        return headerValue.trim();
    }
}
