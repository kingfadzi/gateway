package com.example.gateway.document.service;

import com.example.gateway.config.DocumentValidationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlatformDetectionService {
    
    private static final Logger log = LoggerFactory.getLogger(PlatformDetectionService.class);
    
    // URL parsing patterns for extracting project information
    private static final Pattern GITLAB_BLOB_PATTERN = Pattern.compile(
            "https?://([^/]+)/([^/]+/[^/]+)/-/blob/([^/]+)/(.+)");
    private static final Pattern GITLAB_RAW_PATTERN = Pattern.compile(
            "https?://([^/]+)/([^/]+/[^/]+)/-/raw/([^/]+)/(.+)");
    private static final Pattern GITLAB_PROJECT_PATTERN = Pattern.compile(
            "https?://([^/]+)/([^/]+/[^/]+)/?$");
    
    private static final Pattern CONFLUENCE_PAGES_PATTERN = Pattern.compile(
            "https?://([^/]+)/.*?pages/([0-9]+)");
    private static final Pattern CONFLUENCE_DISPLAY_PATTERN = Pattern.compile(
            "https?://([^/]+)/.*?display/([^/]+)/(.+)");
    
    private final DocumentValidationProperties validationProperties;
    
    public PlatformDetectionService(DocumentValidationProperties validationProperties) {
        this.validationProperties = validationProperties;
    }
    
    public PlatformInfo detectPlatform(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new PlatformInfo("unknown", false, "Empty or null URL");
        }
        
        try {
            URL parsedUrl = new URL(url);
            String hostname = parsedUrl.getHost();
            
            if (hostname == null) {
                return new PlatformInfo("unknown", false, "Invalid URL - no hostname");
            }
            
            // Find platform based on domain configuration
            String platform = validationProperties.findPlatformForHostname(hostname);
            
            if (platform != null) {
                log.debug("Detected platform '{}' for hostname '{}'", platform, hostname);
                return createPlatformInfo(platform, url, hostname);
            }
            
            // In strict mode, reject unknown domains
            if (validationProperties.isStrictMode()) {
                log.warn("Domain '{}' not in allowed platform domains (strict mode enabled)", hostname);
                return new PlatformInfo("unknown", false, 
                    "Domain '" + hostname + "' is not in the list of allowed platform domains");
            }
            
            // Fallback to generic (if strict mode disabled)
            log.debug("Unknown domain '{}' - allowing as generic (strict mode disabled)", hostname);
            return new PlatformInfo("generic", true, null);
            
        } catch (MalformedURLException e) {
            log.warn("Malformed URL: {}", url);
            return new PlatformInfo("unknown", false, "Malformed URL: " + e.getMessage());
        }
    }
    
    private PlatformInfo createPlatformInfo(String platform, String url, String hostname) {
        switch (platform.toLowerCase()) {
            case "gitlab":
                return createGitlabPlatformInfo(url, hostname);
            case "confluence":
                return createConfluencePlatformInfo(url, hostname);
            default:
                // Future platforms (sharepoint, etc.) can be added here
                return new PlatformInfo(platform, true, null);
        }
    }
    
    private PlatformInfo createGitlabPlatformInfo(String url, String hostname) {
        // Parse GitLab URL to extract project information
        
        // Check for GitLab blob URLs (/-/blob/)
        Matcher blobMatcher = GITLAB_BLOB_PATTERN.matcher(url);
        if (blobMatcher.matches()) {
            String gitlabHost = blobMatcher.group(1);
            String projectPath = blobMatcher.group(2);
            String commitSha = blobMatcher.group(3);
            String filePath = blobMatcher.group(4);
            
            GitLabUrlInfo gitlabInfo = new GitLabUrlInfo(gitlabHost, projectPath, commitSha, filePath, true);
            return new PlatformInfo("gitlab", true, null, gitlabInfo);
        }
        
        // Check for GitLab raw URLs (/-/raw/)
        Matcher rawMatcher = GITLAB_RAW_PATTERN.matcher(url);
        if (rawMatcher.matches()) {
            String gitlabHost = rawMatcher.group(1);
            String projectPath = rawMatcher.group(2);
            String commitSha = rawMatcher.group(3);
            String filePath = rawMatcher.group(4);
            
            GitLabUrlInfo gitlabInfo = new GitLabUrlInfo(gitlabHost, projectPath, commitSha, filePath, false);
            return new PlatformInfo("gitlab", true, null, gitlabInfo);
        }
        
        // Check for GitLab project URLs
        Matcher projectMatcher = GITLAB_PROJECT_PATTERN.matcher(url);
        if (projectMatcher.matches()) {
            String gitlabHost = projectMatcher.group(1);
            String projectPath = projectMatcher.group(2);
            
            GitLabUrlInfo gitlabInfo = new GitLabUrlInfo(gitlabHost, projectPath, null, null, false);
            return new PlatformInfo("gitlab", true, null, gitlabInfo);
        }
        
        // Fallback: domain is GitLab but URL structure not recognized
        log.warn("GitLab domain detected but URL structure not recognized: {}", url);
        GitLabUrlInfo gitlabInfo = new GitLabUrlInfo(hostname, null, null, null, false);
        return new PlatformInfo("gitlab", true, "URL structure not recognized, metadata extraction may fail", gitlabInfo);
    }
    
    private PlatformInfo createConfluencePlatformInfo(String url, String hostname) {
        // Parse Confluence URL to extract page information
        
        // Check for Confluence pages with ID (pages/123456)
        Matcher pagesMatcher = CONFLUENCE_PAGES_PATTERN.matcher(url);
        if (pagesMatcher.matches()) {
            String confluenceHost = pagesMatcher.group(1);
            String pageId = pagesMatcher.group(2);
            
            ConfluenceUrlInfo confluenceInfo = new ConfluenceUrlInfo(confluenceHost, null, pageId, null);
            return new PlatformInfo("confluence", true, null, confluenceInfo);
        }
        
        // Check for Confluence display URLs (display/SPACE/Page+Title)
        Matcher displayMatcher = CONFLUENCE_DISPLAY_PATTERN.matcher(url);
        if (displayMatcher.matches()) {
            String confluenceHost = displayMatcher.group(1);
            String spaceKey = displayMatcher.group(2);
            String pageTitle = displayMatcher.group(3);
            
            ConfluenceUrlInfo confluenceInfo = new ConfluenceUrlInfo(confluenceHost, spaceKey, null, pageTitle);
            return new PlatformInfo("confluence", true, null, confluenceInfo);
        }
        
        // Fallback: domain is Confluence but URL structure not recognized
        log.warn("Confluence domain detected but URL structure not recognized: {}", url);
        ConfluenceUrlInfo confluenceInfo = new ConfluenceUrlInfo(hostname, null, null, null);
        return new PlatformInfo("confluence", true, "URL structure not recognized, metadata extraction may fail", confluenceInfo);
    }
    
    public static class PlatformInfo {
        private final String platformType;
        private final boolean isValid;
        private final String errorMessage;
        private final GitLabUrlInfo gitlabInfo;
        private final ConfluenceUrlInfo confluenceInfo;
        
        public PlatformInfo(String platformType, boolean isValid, String errorMessage) {
            this(platformType, isValid, errorMessage, null, null);
        }
        
        public PlatformInfo(String platformType, boolean isValid, String errorMessage, GitLabUrlInfo gitlabInfo) {
            this(platformType, isValid, errorMessage, gitlabInfo, null);
        }
        
        public PlatformInfo(String platformType, boolean isValid, String errorMessage, ConfluenceUrlInfo confluenceInfo) {
            this(platformType, isValid, errorMessage, null, confluenceInfo);
        }
        
        private PlatformInfo(String platformType, boolean isValid, String errorMessage, 
                           GitLabUrlInfo gitlabInfo, ConfluenceUrlInfo confluenceInfo) {
            this.platformType = platformType;
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.gitlabInfo = gitlabInfo;
            this.confluenceInfo = confluenceInfo;
        }
        
        public String getPlatformType() { return platformType; }
        public boolean isValid() { return isValid; }
        public String getErrorMessage() { return errorMessage; }
        public java.util.Optional<GitLabUrlInfo> getGitlabInfo() { return java.util.Optional.ofNullable(gitlabInfo); }
        public java.util.Optional<ConfluenceUrlInfo> getConfluenceInfo() { return java.util.Optional.ofNullable(confluenceInfo); }
        
        public boolean isGitLab() { return "gitlab".equals(platformType); }
        public boolean isConfluence() { return "confluence".equals(platformType); }
        public boolean isGeneric() { return "generic".equals(platformType); }
        public boolean isUnknown() { return "unknown".equals(platformType); }
    }
    
    public static class GitLabUrlInfo {
        private final String host;
        private final String projectPath;
        private final String commitSha;
        private final String filePath;
        private final boolean isBlob;
        
        public GitLabUrlInfo(String host, String projectPath, String commitSha, String filePath, boolean isBlob) {
            this.host = host;
            this.projectPath = projectPath;
            this.commitSha = commitSha;
            this.filePath = filePath;
            this.isBlob = isBlob;
        }
        
        public String getHost() { return host; }
        public String getProjectPath() { return projectPath; }
        public String getCommitSha() { return commitSha; }
        public String getFilePath() { return filePath; }
        public boolean isBlob() { return isBlob; }
        
        public String getApiBaseUrl() {
            return String.format("https://%s/api/v4", host);
        }
        
        public String getProjectApiUrl() {
            return String.format("%s/projects/%s", getApiBaseUrl(), projectPath != null ? projectPath : "");
        }
    }
    
    public static class ConfluenceUrlInfo {
        private final String host;
        private final String spaceKey;
        private final String pageId;
        private final String pageTitle;
        
        public ConfluenceUrlInfo(String host, String spaceKey, String pageId, String pageTitle) {
            this.host = host;
            this.spaceKey = spaceKey;
            this.pageId = pageId;
            this.pageTitle = pageTitle;
        }
        
        public String getHost() { return host; }
        public String getSpaceKey() { return spaceKey; }
        public String getPageId() { return pageId; }
        public String getPageTitle() { return pageTitle; }
        
        public String getApiBaseUrl() {
            return String.format("https://%s/rest/api", host);
        }
        
        public String getPageApiUrl() {
            if (pageId != null) {
                return String.format("%s/content/%s", getApiBaseUrl(), pageId);
            }
            return null;
        }
    }
}