package com.example.ingest.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class StaticTokenGatewayAuthenticatorTest {

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private static StaticTokenGatewayAuthenticator authenticator(String token) {
        return new StaticTokenGatewayAuthenticator(new GatewayProperties(token, null));
    }

    @Test
    void acceptsTheConfiguredBearerToken() {
        assertThat(authenticator("s3cret").authenticate(request("Bearer s3cret"))).isTrue();
    }

    @Test
    void rejectsWrongOrMissingToken() {
        assertThat(authenticator("s3cret").authenticate(request("Bearer nope"))).isFalse();
        assertThat(authenticator("s3cret").authenticate(request(null))).isFalse();
    }

    @Test
    void failsClosedWhenNoTokenConfigured() {
        assertThat(authenticator(null).authenticate(request("Bearer anything"))).isFalse();
        assertThat(authenticator("").authenticate(request("Bearer "))).isFalse();
    }
}
