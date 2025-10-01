// src/main/java/com/example/onboarding/config/OpaProperties.java
package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opa")
public class OpaProperties {
    private String baseUrl;
    private String path;
    private String authHeader;       // optional
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 5000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getAuthHeader() { return authHeader; }
    public void setAuthHeader(String authHeader) { this.authHeader = authHeader; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
