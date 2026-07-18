package com.example.ingest.gateway;

import com.example.ingest.namespace.SourceKey;
import com.example.ingest.namespace.CommonPayload;
import com.example.ingest.namespace.CommonPayloadReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * HTTP trigger source: validates the common payload and bridges it into
 * JetStream. The gateway never resolves namespaces or touches the DB — the
 * caller declares the namespace in {@code X-Namespace} and the worker-side
 * resolver enforces it, exactly like source B.
 */
@RestController
@Profile("gateway")
public class GatewayController {

    public static final String NAMESPACE_HEADER = "X-Namespace";
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Za-z0-9._-]+");

    private final GatewayAuthenticator authenticator;
    private final CommonPayloadReader payloadReader;
    private final EventPublisher publisher;

    public GatewayController(GatewayAuthenticator authenticator,
                             CommonPayloadReader payloadReader,
                             EventPublisher publisher) {
        this.authenticator = authenticator;
        this.payloadReader = payloadReader;
        this.publisher = publisher;
    }

    @PostMapping("/gateway/events/{eventType}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> publish(@PathVariable String eventType,
                                       @RequestHeader(value = NAMESPACE_HEADER, required = false) String namespace,
                                       @RequestBody byte[] body,
                                       HttpServletRequest request) {
        if (!authenticator.authenticate(request)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!EVENT_TYPE.matcher(eventType).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid event type");
        }
        if (namespace == null || namespace.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing " + NAMESPACE_HEADER + " header");
        }
        CommonPayload payload;
        try {
            payload = payloadReader.read(body);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        publisher.publish(new GatewayEvent(
                SourceKey.SOURCE_HTTP, eventType, namespace.trim(), payload.dedupKey(), payload.body()));
        return Map.of("dedupKey", payload.dedupKey());
    }
}
