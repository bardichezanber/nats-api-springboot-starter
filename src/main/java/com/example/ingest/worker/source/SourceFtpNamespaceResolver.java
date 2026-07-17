package com.example.ingest.worker.source;

import org.springframework.stereotype.Component;

/**
 * Source FTP messages come from the gateway's file poller, which copies each
 * NDJSON line's declared namespace into the {@code X-Namespace} header.
 */
@Component
public class SourceFtpNamespaceResolver {

    public String resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new IllegalArgumentException("source FTP message is missing the X-Namespace header");
        }
        return headerValue.trim();
    }
}
