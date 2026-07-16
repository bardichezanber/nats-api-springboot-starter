package com.example.ingest.namespace;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * All hand-written {@link NamespacePolicy} beans, filtered down to the set
 * enabled for this deployment. Fails fast at startup if config enables a
 * namespace that has no policy implementation.
 */
@Component
public class NamespaceRegistry {

    private final Map<String, NamespacePolicy> all;
    private final Map<String, NamespacePolicy> enabled;

    public NamespaceRegistry(List<NamespacePolicy> policies, NamespaceProperties properties) {
        this.all = policies.stream()
                .collect(Collectors.toUnmodifiableMap(NamespacePolicy::key, Function.identity()));

        List<String> missing = properties.enabled().stream()
                .filter(key -> !all.containsKey(key))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Enabled namespaces without a NamespacePolicy implementation: " + missing);
        }

        Map<String, NamespacePolicy> byConfigOrder = new LinkedHashMap<>();
        properties.enabled().forEach(key -> byConfigOrder.put(key, all.get(key)));
        this.enabled = Collections.unmodifiableMap(byConfigOrder);
    }

    /** The policy for {@code key}, only if that namespace is enabled. */
    public Optional<NamespacePolicy> find(String key) {
        return Optional.ofNullable(enabled.get(key));
    }

    /** True if a policy implementation exists, enabled or not. */
    public boolean isKnown(String key) {
        return all.containsKey(key);
    }

    /** Enabled namespace keys, in configuration order. */
    public List<String> enabledKeys() {
        return List.copyOf(enabled.keySet());
    }
}
