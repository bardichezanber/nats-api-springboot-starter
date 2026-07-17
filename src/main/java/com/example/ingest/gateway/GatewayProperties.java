package com.example.ingest.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Gateway role config: HTTP auth token and the FTP file source. */
@ConfigurationProperties(prefix = "app.gateway")
public record GatewayProperties(String token, Ftp ftp) {

    public record Ftp(boolean enabled, String host, int port, String username, String password,
                      String inboxDir, String processingDir, String archiveDir, String errorDir,
                      Duration pollInterval) {
    }
}
