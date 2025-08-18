// src/main/java/com/example/onboarding/config/OpaProperties.java
package com.example.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opa")
public class OpaProperties {
    private String url;
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 4000;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
