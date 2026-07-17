package com.example.ingest.gateway;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides whether an inbound gateway request may publish events.
 * Swap the default static-token bean for the corporate implementation by
 * providing another {@code @Component} of this type (see the TODO on
 * {@link StaticTokenGatewayAuthenticator}).
 */
public interface GatewayAuthenticator {

    boolean authenticate(HttpServletRequest request);
}
