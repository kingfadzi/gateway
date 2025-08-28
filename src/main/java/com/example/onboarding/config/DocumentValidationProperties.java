package com.example.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "document-validation")
public class DocumentValidationProperties {
    
    private Map<String, List<String>> allowedDomains;
    private List<String> blockedDomains;
    private List<String> blockedPatterns;
    private ResponseValidation responseValidation;
    
    // Getters and setters
    public Map<String, List<String>> getAllowedDomains() {
        return allowedDomains;
    }
    
    public void setAllowedDomains(Map<String, List<String>> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }
    
    public List<String> getBlockedDomains() {
        return blockedDomains;
    }
    
    public void setBlockedDomains(List<String> blockedDomains) {
        this.blockedDomains = blockedDomains;
    }
    
    public List<String> getBlockedPatterns() {
        return blockedPatterns;
    }
    
    public void setBlockedPatterns(List<String> blockedPatterns) {
        this.blockedPatterns = blockedPatterns;
    }
    
    public ResponseValidation getResponseValidation() {
        return responseValidation;
    }
    
    public void setResponseValidation(ResponseValidation responseValidation) {
        this.responseValidation = responseValidation;
    }
    
    // Helper methods
    public List<String> getGitlabDomains() {
        return allowedDomains != null ? allowedDomains.getOrDefault("gitlab", List.of()) : List.of();
    }
    
    public List<String> getConfluenceDomains() {
        return allowedDomains != null ? allowedDomains.getOrDefault("confluence", List.of()) : List.of();
    }
    
    public List<String> getCorporateDomains() {
        return allowedDomains != null ? allowedDomains.getOrDefault("corporate", List.of()) : List.of();
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