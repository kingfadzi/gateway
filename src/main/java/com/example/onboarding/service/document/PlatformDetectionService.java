package com.example.onboarding.service.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlatformDetectionService {
    
    private static final Logger log = LoggerFactory.getLogger(PlatformDetectionService.class);
    
    // GitLab URL patterns
    private static final Pattern GITLAB_BLOB_PATTERN = Pattern.compile(
            "https?://([^/]+)/([^/]+/[^/]+)/-/blob/([^/]+)/(.+)");
    private static final Pattern GITLAB_PROJECT_PATTERN = Pattern.compile(
            "https?://([^/]+)/([^/]+/[^/]+)/?$");
    
    // Confluence URL patterns
    private static final Pattern CONFLUENCE_PAGES_PATTERN = Pattern.compile(
            "https?://([^/]+)/.*?pages/([0-9]+)");
    private static final Pattern CONFLUENCE_DISPLAY_PATTERN = Pattern.compile(
            "https?://([^/]+)/.*?display/([^/]+)/(.+)");
    
    public PlatformInfo detectPlatform(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new PlatformInfo("unknown", false, "Empty or null URL");
        }
        
        try {
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost();
            
            // Try GitLab detection first
            Optional<PlatformInfo> gitlabInfo = detectGitLab(url, host);
            if (gitlabInfo.isPresent()) {
                return gitlabInfo.get();
            }
            
            // Try Confluence detection
            Optional<PlatformInfo> confluenceInfo = detectConfluence(url, host);
            if (confluenceInfo.isPresent()) {
                return confluenceInfo.get();
            }
            
            // Generic URL
            return new PlatformInfo("generic", true, null);
            
        } catch (MalformedURLException e) {
            log.warn("Malformed URL: {}", url);
            return new PlatformInfo("unknown", false, "Malformed URL: " + e.getMessage());
        }
    }
    
    private Optional<PlatformInfo> detectGitLab(String url, String host) {
        // Check for GitLab blob URLs
        Matcher blobMatcher = GITLAB_BLOB_PATTERN.matcher(url);
        if (blobMatcher.matches()) {
            String gitlabHost = blobMatcher.group(1);
            String projectPath = blobMatcher.group(2);
            String commitSha = blobMatcher.group(3);
            String filePath = blobMatcher.group(4);
            
            GitLabUrlInfo gitlabInfo = new GitLabUrlInfo(gitlabHost, projectPath, commitSha, filePath, true);
            return Optional.of(new PlatformInfo("gitlab", true, null, gitlabInfo));
        }
        
        // Check for GitLab project URLs
        Matcher projectMatcher = GITLAB_PROJECT_PATTERN.matcher(url);
        if (projectMatcher.matches()) {
            String gitlabHost = projectMatcher.group(1);
            String projectPath = projectMatcher.group(2);
            
            GitLabUrlInfo gitlabInfo = new GitLabUrlInfo(gitlabHost, projectPath, null, null, false);
            return Optional.of(new PlatformInfo("gitlab", true, null, gitlabInfo));
        }
        
        // Check for common GitLab hostnames
        if (host != null && (host.contains("gitlab") || host.equals("gitlab.com"))) {
            return Optional.of(new PlatformInfo("gitlab", true, null));
        }
        
        return Optional.empty();
    }
    
    private Optional<PlatformInfo> detectConfluence(String url, String host) {
        // Check for Confluence pages with ID
        Matcher pagesMatcher = CONFLUENCE_PAGES_PATTERN.matcher(url);
        if (pagesMatcher.matches()) {
            String confluenceHost = pagesMatcher.group(1);
            String pageId = pagesMatcher.group(2);
            
            ConfluenceUrlInfo confluenceInfo = new ConfluenceUrlInfo(confluenceHost, null, pageId, null);
            return Optional.of(new PlatformInfo("confluence", true, null, confluenceInfo));
        }
        
        // Check for Confluence display URLs
        Matcher displayMatcher = CONFLUENCE_DISPLAY_PATTERN.matcher(url);
        if (displayMatcher.matches()) {
            String confluenceHost = displayMatcher.group(1);
            String spaceKey = displayMatcher.group(2);
            String pageTitle = displayMatcher.group(3);
            
            ConfluenceUrlInfo confluenceInfo = new ConfluenceUrlInfo(confluenceHost, spaceKey, null, pageTitle);
            return Optional.of(new PlatformInfo("confluence", true, null, confluenceInfo));
        }
        
        // Check for common Confluence hostnames or path patterns
        if (host != null && (host.contains("confluence") || url.contains("/wiki/") || url.contains("/display/"))) {
            return Optional.of(new PlatformInfo("confluence", true, null));
        }
        
        return Optional.empty();
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
        public Optional<GitLabUrlInfo> getGitlabInfo() { return Optional.ofNullable(gitlabInfo); }
        public Optional<ConfluenceUrlInfo> getConfluenceInfo() { return Optional.ofNullable(confluenceInfo); }
        
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
            // Don't manually encode - let RestTemplate handle URL encoding
            return String.format("%s/projects/%s", getApiBaseUrl(), projectPath);
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