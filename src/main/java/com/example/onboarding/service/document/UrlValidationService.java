package com.example.onboarding.service.document;

import com.example.onboarding.config.DocumentValidationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class UrlValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(UrlValidationService.class);
    
    private final DocumentValidationProperties validationProperties;
    private final ResponseValidationService responseValidationService;
    
    // Pattern for detecting fake subdomains (will be built from configuration)
    private final Pattern fakeSubdomainPattern;
    
    public UrlValidationService(DocumentValidationProperties validationProperties, 
                               ResponseValidationService responseValidationService) {
        this.validationProperties = validationProperties;
        this.responseValidationService = responseValidationService;
        
        // Build fake subdomain pattern from configuration
        String patterns = String.join("|", validationProperties.getBlockedPatterns());
        this.fakeSubdomainPattern = Pattern.compile("^(" + patterns + ")\\.");
    }
    
    public ValidationResult validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return ValidationResult.invalid("URL cannot be empty");
        }
        
        try {
            URI uri = new URI(url.trim());
            
            // Check scheme
            if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                return ValidationResult.invalid("URL must use http or https protocol");
            }
            
            // Check host
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return ValidationResult.invalid("URL must have a valid host");
            }
            
            host = host.toLowerCase();
            
            // Check for fake domains
            ValidationResult fakeCheck = checkForFakeDomains(host, url);
            if (!fakeCheck.isValid()) {
                return fakeCheck;
            }
            
            // Check for suspicious patterns
            ValidationResult suspiciousCheck = checkForSuspiciousPatterns(host, url);
            if (!suspiciousCheck.isValid()) {
                return suspiciousCheck;
            }
            
            // For allowed domains, validate the response content
            if (isAllowedDomain(host)) {
                log.debug("Performing response validation for allowed domain: {}", host);
                ValidationResult responseValidation = responseValidationService.validateResponse(url);
                if (!responseValidation.isValid()) {
                    log.warn("Response validation failed for allowed domain {}: {}", host, responseValidation.getErrorMessage());
                    return ValidationResult.invalid("Content validation failed: " + responseValidation.getErrorMessage());
                }
            }
            
            return ValidationResult.valid();
            
        } catch (URISyntaxException e) {
            return ValidationResult.invalid("Malformed URL: " + e.getMessage());
        }
    }
    
    private ValidationResult checkForFakeDomains(String host, String url) {
        List<String> blockedDomains = validationProperties.getBlockedDomains();
        
        // Check exact matches for blocked domains
        if (blockedDomains.contains(host)) {
            log.warn("Rejected blocked domain: {}", host);
            return ValidationResult.invalid("URL uses a blocked domain: " + host);
        }
        
        // Check if host ends with blocked domains
        for (String blockedDomain : blockedDomains) {
            if (host.endsWith("." + blockedDomain)) {
                log.warn("Rejected subdomain of blocked domain: {}", host);
                return ValidationResult.invalid("URL uses a subdomain of blocked domain: " + blockedDomain);
            }
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult checkForSuspiciousPatterns(String host, String url) {
        // Check for blocked domain patterns within the host
        for (String pattern : validationProperties.getBlockedPatterns()) {
            if (host.contains(pattern + ".") || host.contains("." + pattern)) {
                // Allow legitimate services that might contain these words
                if (isAllowedDomain(host)) {
                    continue;
                }
                log.warn("Rejected suspicious domain pattern '{}' in host: {}", pattern, host);
                return ValidationResult.invalid("URL appears to use a fake/test domain: " + host);
            }
        }
        
        // Check for fake subdomain patterns
        if (fakeSubdomainPattern.matcher(host).find()) {
            if (!isAllowedDomain(host)) {
                log.warn("Rejected fake subdomain pattern: {}", host);
                return ValidationResult.invalid("URL uses a fake/test subdomain: " + host);
            }
        }
        
        // Additional validation for platform-specific URLs
        ValidationResult platformCheck = validatePlatformSpecificUrls(host, url);
        if (!platformCheck.isValid()) {
            return platformCheck;
        }
        
        return ValidationResult.valid();
    }
    
    private boolean isAllowedDomain(String host) {
        // Check all allowed domain categories
        List<String> gitlabDomains = validationProperties.getGitlabDomains();
        List<String> confluenceDomains = validationProperties.getConfluenceDomains();
        List<String> corporateDomains = validationProperties.getCorporateDomains();
        
        // Check if host matches any allowed GitLab domains
        for (String domain : gitlabDomains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        
        // Check if host matches any allowed Confluence domains
        for (String domain : confluenceDomains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        
        // Check if host matches any allowed corporate domains
        for (String domain : corporateDomains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        
        return false;
    }
    
    private ValidationResult validatePlatformSpecificUrls(String host, String url) {
        // For now, platform-specific validation is mainly done by checking allowed domains
        // and response content validation. Additional platform-specific URL structure
        // validation can be added here if needed.
        
        // All platform validation is now handled by isAllowedDomain() check
        // and responseValidationService for content validation
        return ValidationResult.valid();
    }
    
}