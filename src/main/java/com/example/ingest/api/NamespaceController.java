package com.example.ingest.api;

import com.example.ingest.namespace.NamespaceRegistry;
import com.example.ingest.record.IngestedRecord;
import com.example.ingest.record.IngestedRecordRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Namespaces are a public API concept, not just an ingest detail. Reads are
 * always scoped to a single namespace — there is no cross-namespace query.
 */
@RestController
@RequestMapping("/api/namespaces")
@Profile("api")
public class NamespaceController {

    private static final int MAX_WINDOW_SIZE = 100;

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
        requireEnabled(namespaceKey);
        return repository.findByNamespaceKeyOrderByOccurredAtDesc(namespaceKey, pageable)
                .map(RecordResponse::from);
    }

    /**
     * Keyset variant of {@code records}: stays cheap at any depth and stable
     * under concurrent inserts. Page by passing the response's
     * {@code nextOccurredBefore}/{@code nextIdBefore} back until
     * {@code records} is empty.
     */
    @GetMapping("/{namespaceKey}/records/window")
    public RecordWindowResponse recordsWindow(@PathVariable String namespaceKey,
                                              @RequestParam(required = false) Instant occurredBefore,
                                              @RequestParam(required = false) Long idBefore,
                                              @RequestParam(defaultValue = "20") int size) {
        requireEnabled(namespaceKey);
        if (size < 1 || size > MAX_WINDOW_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size must be between 1 and " + MAX_WINDOW_SIZE);
        }
        if ((occurredBefore == null) != (idBefore == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "occurredBefore and idBefore must be passed together");
        }
        List<IngestedRecord> window = occurredBefore == null
                ? repository.findByNamespaceKeyOrderByOccurredAtDescIdDesc(
                        namespaceKey, PageRequest.of(0, size))
                : repository.findWindowBefore(
                        namespaceKey, occurredBefore, idBefore, PageRequest.of(0, size));
        return RecordWindowResponse.from(window);
    }

    private void requireEnabled(String namespaceKey) {
        if (registry.find(namespaceKey).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "unknown or disabled namespace: " + namespaceKey);
        }
    }
}
