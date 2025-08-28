package com.example.onboarding.service.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Optional;

@Service
public class PlatformMetadataService {
    
    private static final Logger log = LoggerFactory.getLogger(PlatformMetadataService.class);
    
    private final RestTemplate restTemplate;
    private final PlatformDetectionService platformDetectionService;
    private final GitLabMetadataService gitLabMetadataService;
    private final ConfluenceMetadataService confluenceMetadataService;
    
    @Value("${app.external-apis.gitlab.token:}")
    private String gitlabToken;
    
    @Value("${app.external-apis.confluence.username:}")
    private String confluenceUsername;
    
    @Value("${app.external-apis.confluence.token:}")
    private String confluenceToken;
    
    public PlatformMetadataService(RestTemplate restTemplate, 
                                 PlatformDetectionService platformDetectionService,
                                 GitLabMetadataService gitLabMetadataService,
                                 ConfluenceMetadataService confluenceMetadataService) {
        this.restTemplate = restTemplate;
        this.platformDetectionService = platformDetectionService;
        this.gitLabMetadataService = gitLabMetadataService;
        this.confluenceMetadataService = confluenceMetadataService;
    }
    
    /**
     * Extract metadata from external platform with comprehensive error handling
     */
    public PlatformMetadata extractMetadata(String url, String sourceType) {
        PlatformMetadata metadata = new PlatformMetadata();
        
        try {
            // First detect the platform
            PlatformDetectionService.PlatformInfo platformInfo = platformDetectionService.detectPlatform(url);
            
            if (!platformInfo.isValid()) {
                log.warn("Invalid URL detected: {} - {}", url, platformInfo.getErrorMessage());
                metadata.setHealthStatus(0);
                return metadata;
            }
            
            // Check basic URL health first
            HealthCheckResult healthResult = checkUrlHealth(url);
            metadata.setHealthStatus(healthResult.getStatusCode());
            
            if (healthResult.getStatusCode() != 200) {
                log.warn("URL health check failed for {}: {}", url, healthResult.getErrorMessage());
                return metadata;
            }
            
            // Extract platform-specific metadata
            switch (platformInfo.getPlatformType()) {
                case "gitlab" -> extractGitLabMetadata(platformInfo, metadata);
                case "confluence" -> extractConfluenceMetadata(platformInfo, metadata);
                case "generic" -> extractGenericMetadata(url, metadata);
                default -> {
                    log.debug("Unknown platform type: {}", platformInfo.getPlatformType());
                    metadata.setHealthStatus(0);
                }
            }
            
        } catch (RuntimeException e) {
            // Re-throw RuntimeExceptions (API failures) to propagate to caller
            log.error("Platform metadata extraction failed for {}: {}", url, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error extracting metadata from {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Metadata extraction failed: " + e.getMessage(), e);
        }
        
        return metadata;
    }
    
    private void extractGitLabMetadata(PlatformDetectionService.PlatformInfo platformInfo, PlatformMetadata metadata) {
        Optional<PlatformDetectionService.GitLabUrlInfo> gitlabInfoOpt = platformInfo.getGitlabInfo();
        if (gitlabInfoOpt.isEmpty()) {
            log.debug("No GitLab URL info available for metadata extraction");
            return;
        }
        
        PlatformDetectionService.GitLabUrlInfo gitlabInfo = gitlabInfoOpt.get();
        
        try {
            Optional<GitLabMetadataService.GitLabMetadata> gitlabMetadata = 
                    gitLabMetadataService.extractMetadata(gitlabInfo, gitlabToken);
            
            if (gitlabMetadata.isPresent()) {
                GitLabMetadataService.GitLabMetadata meta = gitlabMetadata.get();
                metadata.setVersionId(meta.getVersionId());
                metadata.setVersionUrl(meta.getVersionUrl());
                metadata.setAuthor(meta.getCommitAuthor());
                metadata.setOwners(meta.getOwners());
                metadata.setExtractedTitle(meta.getProjectName());
                metadata.setSourceDate(meta.getCommitDateAsOffsetDateTime());
                
                log.debug("Successfully extracted GitLab metadata for project: {}", meta.getProjectName());
            } else {
                // GitLab API failed - this should be an error, not a fallback
                String errorMsg = String.format("GitLab project not found or not accessible: %s", gitlabInfo.getProjectPath());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
        } catch (Exception e) {
            log.error("Failed to extract GitLab metadata for {}: {}", gitlabInfo.getProjectPath(), e.getMessage());
            throw new RuntimeException("GitLab metadata extraction failed: " + e.getMessage(), e);
        }
    }
    
    private void setGitLabFallbackMetadata(PlatformDetectionService.GitLabUrlInfo gitlabInfo, PlatformMetadata metadata) {
        if (gitlabInfo.getCommitSha() != null) {
            String shortSha = gitlabInfo.getCommitSha().length() > 8 ? 
                    gitlabInfo.getCommitSha().substring(0, 8) : gitlabInfo.getCommitSha();
            metadata.setVersionId(shortSha);
        }
        metadata.setOwners(gitlabInfo.getProjectPath());
    }
    
    private void extractConfluenceMetadata(PlatformDetectionService.PlatformInfo platformInfo, PlatformMetadata metadata) {
        Optional<PlatformDetectionService.ConfluenceUrlInfo> confluenceInfoOpt = platformInfo.getConfluenceInfo();
        if (confluenceInfoOpt.isEmpty()) {
            log.debug("No Confluence URL info available for metadata extraction");
            return;
        }
        
        PlatformDetectionService.ConfluenceUrlInfo confluenceInfo = confluenceInfoOpt.get();
        
        try {
            Optional<ConfluenceMetadataService.ConfluenceMetadata> confluenceMetadata = 
                    confluenceMetadataService.extractMetadata(confluenceInfo, confluenceUsername, confluenceToken);
            
            if (confluenceMetadata.isPresent()) {
                ConfluenceMetadataService.ConfluenceMetadata meta = confluenceMetadata.get();
                metadata.setVersionId(meta.getVersionId());
                metadata.setVersionUrl(meta.getVersionUrl());
                metadata.setAuthor(meta.getAuthor());
                metadata.setOwners(meta.getOwners());
                metadata.setExtractedTitle(meta.getTitle());
                
                log.debug("Successfully extracted Confluence metadata for page: {}", meta.getTitle());
            } else {
                log.debug("Could not extract Confluence metadata, using URL-based fallback");
                setConfluenceFallbackMetadata(confluenceInfo, metadata);
            }
            
        } catch (Exception e) {
            log.warn("Failed to extract Confluence metadata, using fallback: {}", e.getMessage());
            setConfluenceFallbackMetadata(confluenceInfo, metadata);
        }
    }
    
    private void setConfluenceFallbackMetadata(PlatformDetectionService.ConfluenceUrlInfo confluenceInfo, PlatformMetadata metadata) {
        if (confluenceInfo.getPageId() != null) {
            metadata.setVersionId(confluenceInfo.getPageId());
        }
        if (confluenceInfo.getSpaceKey() != null) {
            metadata.setOwners(confluenceInfo.getSpaceKey());
        }
    }
    
    private void extractGenericMetadata(String url, PlatformMetadata metadata) {
        // For generic URLs, we can only do basic health checking
        // In the future, this could be enhanced with HTML title extraction, etc.
        log.debug("Extracting generic metadata for URL: {}", url);
    }
    
    private HealthCheckResult checkUrlHealth(String url) {
        try {
            URI uri = new URI(url);
            
            // Validate URI scheme
            if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                return new HealthCheckResult(400, "Invalid URL scheme. Only HTTP and HTTPS are supported.");
            }
            
            // Validate host
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                return new HealthCheckResult(400, "Invalid URL: missing or empty host");
            }
            
            // Perform HEAD request to check accessibility
            restTemplate.headForHeaders(uri);
            return new HealthCheckResult(200, null);
            
        } catch (URISyntaxException e) {
            return new HealthCheckResult(400, "Malformed URL: " + e.getMessage());
        } catch (HttpClientErrorException e) {
            String message = String.format("Client error: %d %s", e.getStatusCode().value(), e.getStatusText());
            return new HealthCheckResult(e.getStatusCode().value(), message);
        } catch (HttpServerErrorException e) {
            String message = String.format("Server error: %d %s", e.getStatusCode().value(), e.getStatusText());
            return new HealthCheckResult(e.getStatusCode().value(), message);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                return new HealthCheckResult(408, "Request timeout: URL took too long to respond");
            } else if (e.getCause() instanceof UnknownHostException) {
                return new HealthCheckResult(404, "Unknown host: " + e.getCause().getMessage());
            } else {
                return new HealthCheckResult(503, "Connection failed: " + e.getMessage());
            }
        } catch (RestClientException e) {
            return new HealthCheckResult(500, "Network error: " + e.getMessage());
        } catch (Exception e) {
            return new HealthCheckResult(500, "Unexpected error: " + e.getMessage());
        }
    }
    
    private static class HealthCheckResult {
        private final int statusCode;
        private final String errorMessage;
        
        public HealthCheckResult(int statusCode, String errorMessage) {
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }
        
        public int getStatusCode() { return statusCode; }
        public String getErrorMessage() { return errorMessage; }
    }
}