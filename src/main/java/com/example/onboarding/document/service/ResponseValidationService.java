package com.example.onboarding.document.service;

import com.example.onboarding.config.DocumentValidationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Service
public class ResponseValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(ResponseValidationService.class);
    
    private final RestTemplate restTemplate;
    private final DocumentValidationProperties validationProperties;
    
    
    public ResponseValidationService(RestTemplate restTemplate, DocumentValidationProperties validationProperties) {
        this.restTemplate = restTemplate;
        this.validationProperties = validationProperties;
    }
    
    /**
     * Validates that a URL from an allowed domain returns valid, meaningful content
     */
    public ValidationResult validateResponse(String url) {
        try {
            // Perform GET request to fetch content
            ResponseEntity<String> response = fetchUrlContent(url);
            
            // Check HTTP status
            if (!isValidStatusCode(response.getStatusCodeValue())) {
                return ValidationResult.invalid(
                    String.format("Invalid HTTP status: %d. Expected one of: %s", 
                                 response.getStatusCodeValue(), 
                                 validationProperties.getResponseValidation().getValidStatusCodes())
                );
            }
            
            String content = response.getBody();
            if (content == null || content.trim().isEmpty()) {
                return ValidationResult.invalid("Response contains no content");
            }
            
            // Check minimum content size
            if (content.length() < validationProperties.getResponseValidation().getMinContentSize()) {
                return ValidationResult.invalid(
                    String.format("Content too small: %d bytes. Minimum required: %d bytes",
                                 content.length(),
                                 validationProperties.getResponseValidation().getMinContentSize())
                );
            }
            
            
            log.debug("Successfully validated response content for URL: {}", url);
            return ValidationResult.valid();
            
        } catch (Exception e) {
            log.warn("Failed to validate response for URL {}: {}", url, e.getMessage());
            return ValidationResult.invalid("Failed to validate response: " + e.getMessage());
        }
    }
    
    private ResponseEntity<String> fetchUrlContent(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "DocumentValidationService/1.0");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            return restTemplate.exchange(
                URI.create(url), 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
        } catch (RestClientException e) {
            throw new RuntimeException("Failed to fetch content: " + e.getMessage(), e);
        }
    }
    
    private boolean isValidStatusCode(int statusCode) {
        return validationProperties.getResponseValidation().getValidStatusCodes().contains(statusCode);
    }
    
    
}