package com.example.ingest.namespace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Deployment config decides which namespaces are live
 * ({@code APP_NAMESPACES_ENABLED}, comma-separated). Switch semantics:
 * a namespace not listed here is business-wise not collected.
 */
@ConfigurationProperties(prefix = "app.namespaces")
public record NamespaceProperties(List<String> enabled) {

    public NamespaceProperties {
        enabled = enabled == null ? List.of() : List.copyOf(enabled);
    }
}
