package com.example.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "document-validation")
public class DocumentValidationProperties {
    
    private Map<String, List<String>> platformDomains;
    private boolean strictMode = true;
    private ResponseValidation responseValidation;
    
    // Getters and setters
    public Map<String, List<String>> getPlatformDomains() {
        return platformDomains;
    }
    
    public void setPlatformDomains(Map<String, List<String>> platformDomains) {
        this.platformDomains = platformDomains;
    }
    
    public boolean isStrictMode() {
        return strictMode;
    }
    
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }
    
    public ResponseValidation getResponseValidation() {
        return responseValidation;
    }
    
    public void setResponseValidation(ResponseValidation responseValidation) {
        this.responseValidation = responseValidation;
    }
    
    // Helper methods for platform-specific domain access
    public List<String> getGitlabDomains() {
        return platformDomains != null ? platformDomains.getOrDefault("gitlab", List.of()) : List.of();
    }
    
    public List<String> getConfluenceDomains() {
        return platformDomains != null ? platformDomains.getOrDefault("confluence", List.of()) : List.of();
    }
    
    public List<String> getSharepointDomains() {
        return platformDomains != null ? platformDomains.getOrDefault("sharepoint", List.of()) : List.of();
    }
    
    /**
     * Find which platform a domain belongs to
     * @param hostname The hostname to check
     * @return The platform name (gitlab, confluence, etc.) or null if not found
     */
    public String findPlatformForHostname(String hostname) {
        if (platformDomains == null || hostname == null) {
            return null;
        }
        
        for (Map.Entry<String, List<String>> entry : platformDomains.entrySet()) {
            String platform = entry.getKey();
            List<String> domains = entry.getValue();
            
            for (String domain : domains) {
                if (hostname.equals(domain) || hostname.endsWith("." + domain)) {
                    return platform;
                }
            }
        }
        
        return null;
    }
    
    public static class ResponseValidation {
        private int minTitleLength = 3;
        private int minContentSize = 100;
        private List<Integer> validStatusCodes = List.of(200, 302);
        private int validationTimeout = 30000;
        
        // Getters and setters
        public int getMinTitleLength() {
            return minTitleLength;
        }
        
        public void setMinTitleLength(int minTitleLength) {
            this.minTitleLength = minTitleLength;
        }
        
        public int getMinContentSize() {
            return minContentSize;
        }
        
        public void setMinContentSize(int minContentSize) {
            this.minContentSize = minContentSize;
        }
        
        public List<Integer> getValidStatusCodes() {
            return validStatusCodes;
        }
        
        public void setValidStatusCodes(List<Integer> validStatusCodes) {
            this.validStatusCodes = validStatusCodes;
        }
        
        public int getValidationTimeout() {
            return validationTimeout;
        }
        
        public void setValidationTimeout(int validationTimeout) {
            this.validationTimeout = validationTimeout;
        }
    }
}