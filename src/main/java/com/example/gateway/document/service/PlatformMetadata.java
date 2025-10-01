package com.example.gateway.document.service;

import java.time.OffsetDateTime;

/**
 * Container for metadata extracted from external platforms
 */
public class PlatformMetadata {
    private String versionId;
    private String versionUrl;
    private String author;
    private String owners;
    private Integer healthStatus;
    private String extractedTitle;
    private OffsetDateTime sourceDate;
    
    public PlatformMetadata() {}
    
    public String getVersionId() {
        return versionId;
    }
    
    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }
    
    public String getVersionUrl() {
        return versionUrl;
    }
    
    public void setVersionUrl(String versionUrl) {
        this.versionUrl = versionUrl;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getOwners() {
        return owners;
    }
    
    public void setOwners(String owners) {
        this.owners = owners;
    }
    
    public Integer getHealthStatus() {
        return healthStatus;
    }
    
    public void setHealthStatus(Integer healthStatus) {
        this.healthStatus = healthStatus;
    }
    
    public String getExtractedTitle() {
        return extractedTitle;
    }
    
    public void setExtractedTitle(String extractedTitle) {
        this.extractedTitle = extractedTitle;
    }
    
    public OffsetDateTime getSourceDate() {
        return sourceDate;
    }
    
    public void setSourceDate(OffsetDateTime sourceDate) {
        this.sourceDate = sourceDate;
    }
}