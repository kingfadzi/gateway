package com.example.onboarding.document.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class ConfluenceMetadataService {
    
    private static final Logger log = LoggerFactory.getLogger(ConfluenceMetadataService.class);
    
    private final RestTemplate restTemplate;
    
    public ConfluenceMetadataService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    public Optional<ConfluenceMetadata> extractMetadata(PlatformDetectionService.ConfluenceUrlInfo confluenceInfo, 
                                                       String username, String apiToken) {
        try {
            // Get page information
            Optional<ConfluencePage> page = getPage(confluenceInfo, username, apiToken);
            if (page.isEmpty()) {
                log.warn("Could not fetch Confluence page info for pageId: {}", confluenceInfo.getPageId());
                return Optional.empty();
            }
            
            ConfluenceMetadata.Builder metadataBuilder = ConfluenceMetadata.builder()
                    .pageId(page.get().getId())
                    .title(page.get().getTitle())
                    .type(page.get().getType())
                    .status(page.get().getStatus())
                    .createdDate(page.get().getCreatedDate())
                    .webUrl(page.get().getWebUrl());
            
            // Add space information
            if (page.get().getSpace() != null) {
                metadataBuilder
                        .spaceKey(page.get().getSpace().getKey())
                        .spaceName(page.get().getSpace().getName());
            }
            
            // Add version information
            if (page.get().getVersion() != null) {
                metadataBuilder
                        .versionNumber(page.get().getVersion().getNumber())
                        .versionMessage(page.get().getVersion().getMessage())
                        .versionDate(page.get().getVersion().getWhen());
                
                if (page.get().getVersion().getBy() != null) {
                    metadataBuilder.lastModifiedBy(page.get().getVersion().getBy().getDisplayName());
                }
            }
            
            // Add creator information
            if (page.get().getHistory() != null && page.get().getHistory().getCreatedBy() != null) {
                metadataBuilder.createdBy(page.get().getHistory().getCreatedBy().getDisplayName());
            }
            
            return Optional.of(metadataBuilder.build());
            
        } catch (Exception e) {
            log.error("Failed to extract Confluence metadata for pageId {}: {}", 
                     confluenceInfo.getPageId(), e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    private Optional<ConfluencePage> getPage(PlatformDetectionService.ConfluenceUrlInfo confluenceInfo,
                                           String username, String apiToken) {
        if (confluenceInfo.getPageId() == null) {
            log.debug("No page ID available for Confluence metadata extraction");
            return Optional.empty();
        }
        
        try {
            String url = String.format("%s/content/%s?expand=space,version,history.createdBy,version.by",
                    confluenceInfo.getApiBaseUrl(), confluenceInfo.getPageId());
            
            HttpHeaders headers = createHeaders(username, apiToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<ConfluencePage> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, ConfluencePage.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            
        } catch (RestClientException e) {
            log.debug("Failed to fetch Confluence page {}: {}", confluenceInfo.getPageId(), e.getMessage());
        }
        
        return Optional.empty();
    }
    
    private HttpHeaders createHeaders(String username, String apiToken) {
        HttpHeaders headers = new HttpHeaders();
        if (username != null && apiToken != null && !username.trim().isEmpty() && !apiToken.trim().isEmpty()) {
            String auth = java.util.Base64.getEncoder()
                    .encodeToString((username + ":" + apiToken).getBytes());
            headers.set("Authorization", "Basic " + auth);
        }
        headers.set("User-Agent", "DocumentManagementService/1.0");
        headers.set("Accept", "application/json");
        return headers;
    }
    
    // DTOs for Confluence API responses
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfluencePage {
        private String id;
        private String title;
        private String type;
        private String status;
        private ConfluenceSpace space;
        private ConfluenceVersion version;
        private ConfluenceHistory history;
        @JsonProperty("_links")
        private ConfluenceLinks links;
        
        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public String getStatus() { return status; }
        public ConfluenceSpace getSpace() { return space; }
        public ConfluenceVersion getVersion() { return version; }
        public ConfluenceHistory getHistory() { return history; }
        public ConfluenceLinks getLinks() { return links; }
        
        public String getCreatedDate() {
            return history != null ? history.getCreatedDate() : null;
        }
        
        public String getWebUrl() {
            return links != null ? links.getWebui() : null;
        }
        
        // Setters
        public void setId(String id) { this.id = id; }
        public void setTitle(String title) { this.title = title; }
        public void setType(String type) { this.type = type; }
        public void setStatus(String status) { this.status = status; }
        public void setSpace(ConfluenceSpace space) { this.space = space; }
        public void setVersion(ConfluenceVersion version) { this.version = version; }
        public void setHistory(ConfluenceHistory history) { this.history = history; }
        public void setLinks(ConfluenceLinks links) { this.links = links; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfluenceSpace {
        private String key;
        private String name;
        private String type;
        
        // Getters
        public String getKey() { return key; }
        public String getName() { return name; }
        public String getType() { return type; }
        
        // Setters
        public void setKey(String key) { this.key = key; }
        public void setName(String name) { this.name = name; }
        public void setType(String type) { this.type = type; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfluenceVersion {
        private Integer number;
        private String message;
        private String when;
        private ConfluenceUser by;
        
        // Getters
        public Integer getNumber() { return number; }
        public String getMessage() { return message; }
        public String getWhen() { return when; }
        public ConfluenceUser getBy() { return by; }
        
        // Setters
        public void setNumber(Integer number) { this.number = number; }
        public void setMessage(String message) { this.message = message; }
        public void setWhen(String when) { this.when = when; }
        public void setBy(ConfluenceUser by) { this.by = by; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfluenceHistory {
        @JsonProperty("createdDate")
        private String createdDate;
        @JsonProperty("createdBy")
        private ConfluenceUser createdBy;
        
        // Getters
        public String getCreatedDate() { return createdDate; }
        public ConfluenceUser getCreatedBy() { return createdBy; }
        
        // Setters
        public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
        public void setCreatedBy(ConfluenceUser createdBy) { this.createdBy = createdBy; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfluenceUser {
        @JsonProperty("displayName")
        private String displayName;
        private String username;
        private String email;
        
        // Getters
        public String getDisplayName() { return displayName; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        
        // Setters
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setUsername(String username) { this.username = username; }
        public void setEmail(String email) { this.email = email; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfluenceLinks {
        private String webui;
        private String base;
        
        // Getters
        public String getWebui() { return webui; }
        public String getBase() { return base; }
        
        // Setters
        public void setWebui(String webui) { this.webui = webui; }
        public void setBase(String base) { this.base = base; }
    }
    
    // Metadata result class
    public static class ConfluenceMetadata {
        private final String pageId;
        private final String title;
        private final String type;
        private final String status;
        private final String spaceKey;
        private final String spaceName;
        private final Integer versionNumber;
        private final String versionMessage;
        private final String versionDate;
        private final String lastModifiedBy;
        private final String createdBy;
        private final String createdDate;
        private final String webUrl;
        
        private ConfluenceMetadata(Builder builder) {
            this.pageId = builder.pageId;
            this.title = builder.title;
            this.type = builder.type;
            this.status = builder.status;
            this.spaceKey = builder.spaceKey;
            this.spaceName = builder.spaceName;
            this.versionNumber = builder.versionNumber;
            this.versionMessage = builder.versionMessage;
            this.versionDate = builder.versionDate;
            this.lastModifiedBy = builder.lastModifiedBy;
            this.createdBy = builder.createdBy;
            this.createdDate = builder.createdDate;
            this.webUrl = builder.webUrl;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public String getPageId() { return pageId; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public String getStatus() { return status; }
        public String getSpaceKey() { return spaceKey; }
        public String getSpaceName() { return spaceName; }
        public Integer getVersionNumber() { return versionNumber; }
        public String getVersionMessage() { return versionMessage; }
        public String getVersionDate() { return versionDate; }
        public String getLastModifiedBy() { return lastModifiedBy; }
        public String getCreatedBy() { return createdBy; }
        public String getCreatedDate() { return createdDate; }
        public String getWebUrl() { return webUrl; }
        
        public String getVersionId() {
            return versionNumber != null ? "v" + versionNumber : pageId;
        }
        
        public String getVersionUrl() {
            return webUrl;
        }
        
        public String getOwners() {
            return spaceKey != null ? spaceKey : createdBy;
        }
        
        public String getAuthor() {
            return lastModifiedBy != null ? lastModifiedBy : createdBy;
        }
        
        public static class Builder {
            private String pageId;
            private String title;
            private String type;
            private String status;
            private String spaceKey;
            private String spaceName;
            private Integer versionNumber;
            private String versionMessage;
            private String versionDate;
            private String lastModifiedBy;
            private String createdBy;
            private String createdDate;
            private String webUrl;
            
            public Builder pageId(String pageId) { this.pageId = pageId; return this; }
            public Builder title(String title) { this.title = title; return this; }
            public Builder type(String type) { this.type = type; return this; }
            public Builder status(String status) { this.status = status; return this; }
            public Builder spaceKey(String spaceKey) { this.spaceKey = spaceKey; return this; }
            public Builder spaceName(String spaceName) { this.spaceName = spaceName; return this; }
            public Builder versionNumber(Integer versionNumber) { this.versionNumber = versionNumber; return this; }
            public Builder versionMessage(String versionMessage) { this.versionMessage = versionMessage; return this; }
            public Builder versionDate(String versionDate) { this.versionDate = versionDate; return this; }
            public Builder lastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; return this; }
            public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }
            public Builder createdDate(String createdDate) { this.createdDate = createdDate; return this; }
            public Builder webUrl(String webUrl) { this.webUrl = webUrl; return this; }
            
            public ConfluenceMetadata build() {
                return new ConfluenceMetadata(this);
            }
        }
    }
}