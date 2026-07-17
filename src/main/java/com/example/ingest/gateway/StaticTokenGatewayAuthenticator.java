package com.example.ingest.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// TODO(auth): 以企業內部認證實作替換此 bean（實作同一個 GatewayAuthenticator 介面、
// 換成那個 @Component 即可，controller 不用動）。
@Component
@Profile("gateway")
public class StaticTokenGatewayAuthenticator implements GatewayAuthenticator {

    private final String token;

    public StaticTokenGatewayAuthenticator(GatewayProperties properties) {
        this.token = properties.token();
    }

    @Override
    public boolean authenticate(HttpServletRequest request) {
        // Fail closed: an unset token means nobody may publish.
        if (token == null || token.isBlank()) {
            return false;
        }
        String header = request.getHeader("Authorization");
        return ("Bearer " + token).equals(header);
    }
}
