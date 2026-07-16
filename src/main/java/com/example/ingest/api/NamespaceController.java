package com.example.ingest.api;

import com.example.ingest.namespace.NamespaceRegistry;
import com.example.ingest.record.IngestedRecordRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Namespaces are a public API concept, not just an ingest detail. Reads are
 * always scoped to a single namespace — there is no cross-namespace query.
 */
@RestController
@RequestMapping("/api/namespaces")
@Profile("api")
public class NamespaceController {

    private final NamespaceRegistry registry;
    private final IngestedRecordRepository repository;

    public NamespaceController(NamespaceRegistry registry, IngestedRecordRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    @GetMapping
    public List<String> namespaces() {
        return registry.enabledKeys();
    }

    @GetMapping("/{namespaceKey}/records")
    public Page<RecordResponse> records(@PathVariable String namespaceKey,
                                        @PageableDefault(size = 20) Pageable pageable) {
        if (registry.find(namespaceKey).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "unknown or disabled namespace: " + namespaceKey);
        }
        return repository.findByNamespaceKeyOrderByOccurredAtDesc(namespaceKey, pageable)
                .map(RecordResponse::from);
    }
}
