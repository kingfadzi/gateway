package com.example.gateway.document.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Base64;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Service
public class GitLabMetadataService {
    
    private static final Logger log = LoggerFactory.getLogger(GitLabMetadataService.class);
    
    private final RestTemplate restTemplate;
    
    public GitLabMetadataService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    public Optional<GitLabMetadata> extractMetadata(PlatformDetectionService.GitLabUrlInfo gitlabInfo, String accessToken) {
        try {
            log.debug("Starting GitLab metadata extraction for project: {} on host: {}", 
                     gitlabInfo.getProjectPath(), gitlabInfo.getHost());
            
            if (accessToken == null || accessToken.trim().isEmpty()) {
                log.warn("GitLab API token is not configured - API calls will be limited or fail");
            }
            
            // Get project information first
            Optional<GitLabProject> project = getProject(gitlabInfo, accessToken);
            if (project.isEmpty()) {
                log.warn("Could not fetch GitLab project info for {} - check token and project access", 
                        gitlabInfo.getProjectPath());
                return Optional.empty();
            }
            
            GitLabMetadata.Builder metadataBuilder = GitLabMetadata.builder()
                    .projectId(project.get().getId())
                    .projectName(project.get().getName())
                    .projectPath(project.get().getPathWithNamespace())
                    .defaultBranch(project.get().getDefaultBranch())
                    .webUrl(project.get().getWebUrl());
            
            // Get the latest commit from the ref (branch or commit SHA)
            String ref = gitlabInfo.getCommitSha() != null ? gitlabInfo.getCommitSha() : project.get().getDefaultBranch();
            Optional<GitLabCommit> commit = getLatestCommit(gitlabInfo, accessToken, project.get().getId(), ref);
            if (commit.isPresent()) {
                metadataBuilder
                        .commitSha(commit.get().getId())
                        .commitShortSha(commit.get().getShortId())
                        .commitMessage(commit.get().getMessage())
                        .commitAuthor(commit.get().getAuthorName())
                        .commitDate(commit.get().getCommittedDate())
                        .commitWebUrl(commit.get().getWebUrl());
            }
            
            // Try to get CODEOWNERS information
            Optional<String> owners = getCodeowners(gitlabInfo, accessToken, project.get().getId(), ref);
            if (owners.isPresent()) {
                metadataBuilder.owners(owners.get());
            } else {
                // Fallback to project path
                metadataBuilder.owners(project.get().getPathWithNamespace());
            }
            
            // If we have file path, get file info
            if (gitlabInfo.getFilePath() != null) {
                Optional<GitLabFile> file = getFile(gitlabInfo, accessToken, project.get().getId(), ref);
                if (file.isPresent()) {
                    metadataBuilder
                            .filePath(file.get().getFilePath())
                            .fileName(file.get().getFileName())
                            .fileSize(file.get().getSize());
                }
            }
            
            return Optional.of(metadataBuilder.build());
            
        } catch (Exception e) {
            log.error("Failed to extract GitLab metadata for {}: {}", gitlabInfo.getProjectPath(), e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    private Optional<GitLabProject> getProject(PlatformDetectionService.GitLabUrlInfo gitlabInfo, String accessToken) {
        try {
            // Properly encode the project path for GitLab API
            String encodedProjectPath = URLEncoder.encode(gitlabInfo.getProjectPath(), StandardCharsets.UTF_8);
            String url = String.format("%s/projects/%s", gitlabInfo.getApiBaseUrl(), encodedProjectPath);
            log.debug("Fetching GitLab project from API: {}", url);
            
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<GitLabProject> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, entity, GitLabProject.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Successfully fetched GitLab project: {} (ID: {})", 
                         response.getBody().getName(), response.getBody().getId());
                return Optional.of(response.getBody());
            } else {
                log.warn("GitLab API returned non-success status: {} for project {}", 
                        response.getStatusCode(), gitlabInfo.getProjectPath());
            }
            
        } catch (RestClientException e) {
            log.error("Failed to fetch GitLab project {} from {}: {}", 
                     gitlabInfo.getProjectPath(), gitlabInfo.getProjectApiUrl(), e.getMessage());
        }
        
        return Optional.empty();
    }
    
    private Optional<GitLabCommit> getLatestCommit(PlatformDetectionService.GitLabUrlInfo gitlabInfo, 
                                                 String accessToken, Long projectId, String ref) {
        try {
            String url = String.format("%s/projects/%d/repository/commits/%s",
                    gitlabInfo.getApiBaseUrl(), projectId, ref);
            log.debug("Fetching GitLab commit from API: {} for ref: {}", url, ref);
            
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<GitLabCommit> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, entity, GitLabCommit.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Successfully fetched GitLab commit: {} by {} at {}", 
                         response.getBody().getShortId(), 
                         response.getBody().getAuthorName(),
                         response.getBody().getCommittedDate());
                return Optional.of(response.getBody());
            } else {
                log.warn("GitLab API returned non-success status: {} for commit ref {}", 
                        response.getStatusCode(), ref);
            }
            
        } catch (RestClientException e) {
            log.error("Failed to fetch GitLab commit for ref {} from {}: {}", ref, gitlabInfo.getApiBaseUrl(), e.getMessage());
        }
        
        return Optional.empty();
    }
    
    private Optional<String> getCodeowners(PlatformDetectionService.GitLabUrlInfo gitlabInfo,
                                         String accessToken, Long projectId, String ref) {
        // Try common CODEOWNERS file locations
        String[] codeownersLocations = {".gitlab/CODEOWNERS", "CODEOWNERS", ".github/CODEOWNERS"};
        
        for (String location : codeownersLocations) {
            try {
                String encodedFilePath = URLEncoder.encode(location, StandardCharsets.UTF_8);
                String url = String.format("%s/projects/%d/repository/files/%s?ref=%s",
                        gitlabInfo.getApiBaseUrl(), projectId, encodedFilePath, ref);
                HttpHeaders headers = createHeaders(accessToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<GitLabFileContent> response = restTemplate.exchange(
                        URI.create(url), HttpMethod.GET, entity, GitLabFileContent.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String content = response.getBody().getDecodedContent();
                    if (content != null && !content.trim().isEmpty()) {
                        // Parse CODEOWNERS content and extract owners
                        String owners = parseCodeowners(content, gitlabInfo.getFilePath());
                        if (owners != null && !owners.trim().isEmpty()) {
                            log.debug("Found CODEOWNERS in {} with owners: {}", location, owners);
                            return Optional.of(owners);
                        }
                    }
                }
                
            } catch (RestClientException e) {
                log.debug("CODEOWNERS file not found at {}: {}", location, e.getMessage());
            }
        }
        
        return Optional.empty();
    }
    
    private String parseCodeowners(String content, String filePath) {
        if (content == null || filePath == null) {
            return null;
        }
        
        String[] lines = content.split("\n");
        String bestMatch = null;
        int bestMatchLength = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            String[] parts = line.split("\\s+", 2);
            if (parts.length < 2) {
                continue;
            }
            
            String pattern = parts[0];
            String owners = parts[1];
            
            // Simple pattern matching - can be enhanced with glob patterns
            if (filePath.startsWith(pattern.replace("*", ""))) {
                if (pattern.length() > bestMatchLength) {
                    bestMatch = owners;
                    bestMatchLength = pattern.length();
                }
            } else if (pattern.equals("*") && bestMatch == null) {
                // Fallback to global pattern
                bestMatch = owners;
            }
        }
        
        return bestMatch;
    }
    
    private Optional<GitLabFile> getFile(PlatformDetectionService.GitLabUrlInfo gitlabInfo,
                                       String accessToken, Long projectId, String ref) {
        try {
            String encodedFilePath = URLEncoder.encode(gitlabInfo.getFilePath(), StandardCharsets.UTF_8);
            String url = String.format("%s/projects/%d/repository/files/%s?ref=%s",
                    gitlabInfo.getApiBaseUrl(), projectId, encodedFilePath, ref);
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<GitLabFile> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, entity, GitLabFile.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            
        } catch (RestClientException e) {
            log.debug("Failed to fetch GitLab file {}: {}", gitlabInfo.getFilePath(), e.getMessage());
        }
        
        return Optional.empty();
    }
    
    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            headers.set("Authorization", "Bearer " + accessToken);
        }
        headers.set("User-Agent", "DocumentManagementService/1.0");
        return headers;
    }
    
    // DTOs for GitLab API responses
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitLabProject {
        private Long id;
        private String name;
        @JsonProperty("path_with_namespace")
        private String pathWithNamespace;
        @JsonProperty("default_branch")
        private String defaultBranch;
        @JsonProperty("web_url")
        private String webUrl;
        @JsonProperty("description")
        private String description;
        
        // Getters
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getPathWithNamespace() { return pathWithNamespace; }
        public String getDefaultBranch() { return defaultBranch; }
        public String getWebUrl() { return webUrl; }
        public String getDescription() { return description; }
        
        // Setters
        public void setId(Long id) { this.id = id; }
        public void setName(String name) { this.name = name; }
        public void setPathWithNamespace(String pathWithNamespace) { this.pathWithNamespace = pathWithNamespace; }
        public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
        public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
        public void setDescription(String description) { this.description = description; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitLabCommit {
        private String id;
        @JsonProperty("short_id")
        private String shortId;
        private String message;
        @JsonProperty("author_name")
        private String authorName;
        @JsonProperty("author_email")
        private String authorEmail;
        @JsonProperty("committed_date")
        private String committedDate;
        @JsonProperty("web_url")
        private String webUrl;
        
        // Getters
        public String getId() { return id; }
        public String getShortId() { return shortId; }
        public String getMessage() { return message; }
        public String getAuthorName() { return authorName; }
        public String getAuthorEmail() { return authorEmail; }
        public String getCommittedDate() { return committedDate; }
        public String getWebUrl() { return webUrl; }
        
        // Setters
        public void setId(String id) { this.id = id; }
        public void setShortId(String shortId) { this.shortId = shortId; }
        public void setMessage(String message) { this.message = message; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
        public void setCommittedDate(String committedDate) { this.committedDate = committedDate; }
        public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitLabFile {
        @JsonProperty("file_name")
        private String fileName;
        @JsonProperty("file_path")
        private String filePath;
        private Integer size;
        private String encoding;
        @JsonProperty("content_sha256")
        private String contentSha256;
        
        // Getters
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public Integer getSize() { return size; }
        public String getEncoding() { return encoding; }
        public String getContentSha256() { return contentSha256; }
        
        // Setters
        public void setFileName(String fileName) { this.fileName = fileName; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public void setSize(Integer size) { this.size = size; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
        public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitLabFileContent {
        @JsonProperty("file_name")
        private String fileName;
        @JsonProperty("file_path")
        private String filePath;
        private String content;
        private String encoding;
        
        // Getters
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public String getContent() { return content; }
        public String getEncoding() { return encoding; }
        
        // Setters
        public void setFileName(String fileName) { this.fileName = fileName; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public void setContent(String content) { this.content = content; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
        
        // Helper method to decode base64 content
        public String getDecodedContent() {
            if (content == null) return null;
            if ("base64".equals(encoding)) {
                try {
                    return new String(Base64.getDecoder().decode(content));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            return content;
        }
    }
    
    // Metadata result class
    public static class GitLabMetadata {
        private final Long projectId;
        private final String projectName;
        private final String projectPath;
        private final String defaultBranch;
        private final String webUrl;
        private final String commitSha;
        private final String commitShortSha;
        private final String commitMessage;
        private final String commitAuthor;
        private final String commitDate;
        private final String commitWebUrl;
        private final String filePath;
        private final String fileName;
        private final Integer fileSize;
        private final String owners;
        
        private GitLabMetadata(Builder builder) {
            this.projectId = builder.projectId;
            this.projectName = builder.projectName;
            this.projectPath = builder.projectPath;
            this.defaultBranch = builder.defaultBranch;
            this.webUrl = builder.webUrl;
            this.commitSha = builder.commitSha;
            this.commitShortSha = builder.commitShortSha;
            this.commitMessage = builder.commitMessage;
            this.commitAuthor = builder.commitAuthor;
            this.commitDate = builder.commitDate;
            this.commitWebUrl = builder.commitWebUrl;
            this.filePath = builder.filePath;
            this.fileName = builder.fileName;
            this.fileSize = builder.fileSize;
            this.owners = builder.owners;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public Long getProjectId() { return projectId; }
        public String getProjectName() { return projectName; }
        public String getProjectPath() { return projectPath; }
        public String getDefaultBranch() { return defaultBranch; }
        public String getWebUrl() { return webUrl; }
        public String getCommitSha() { return commitSha; }
        public String getCommitShortSha() { return commitShortSha; }
        public String getCommitMessage() { return commitMessage; }
        public String getCommitAuthor() { return commitAuthor; }
        public String getCommitDate() { return commitDate; }
        public String getCommitWebUrl() { return commitWebUrl; }
        public String getFilePath() { return filePath; }
        public String getFileName() { return fileName; }
        public Integer getFileSize() { return fileSize; }
        public String getOwners() { return owners; }
        
        public String getVersionId() {
            return commitShortSha != null ? commitShortSha : commitSha;
        }
        
        public String getVersionUrl() {
            return commitWebUrl != null ? commitWebUrl : webUrl;
        }
        
        public OffsetDateTime getCommitDateAsOffsetDateTime() {
            if (commitDate == null || commitDate.isEmpty()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(commitDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException e) {
                log.debug("Failed to parse commit date '{}': {}", commitDate, e.getMessage());
                return null;
            }
        }
        
        public static class Builder {
            private Long projectId;
            private String projectName;
            private String projectPath;
            private String defaultBranch;
            private String webUrl;
            private String commitSha;
            private String commitShortSha;
            private String commitMessage;
            private String commitAuthor;
            private String commitDate;
            private String commitWebUrl;
            private String filePath;
            private String fileName;
            private Integer fileSize;
            private String owners;
            
            public Builder projectId(Long projectId) { this.projectId = projectId; return this; }
            public Builder projectName(String projectName) { this.projectName = projectName; return this; }
            public Builder projectPath(String projectPath) { this.projectPath = projectPath; return this; }
            public Builder defaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; return this; }
            public Builder webUrl(String webUrl) { this.webUrl = webUrl; return this; }
            public Builder commitSha(String commitSha) { this.commitSha = commitSha; return this; }
            public Builder commitShortSha(String commitShortSha) { this.commitShortSha = commitShortSha; return this; }
            public Builder commitMessage(String commitMessage) { this.commitMessage = commitMessage; return this; }
            public Builder commitAuthor(String commitAuthor) { this.commitAuthor = commitAuthor; return this; }
            public Builder commitDate(String commitDate) { this.commitDate = commitDate; return this; }
            public Builder commitWebUrl(String commitWebUrl) { this.commitWebUrl = commitWebUrl; return this; }
            public Builder filePath(String filePath) { this.filePath = filePath; return this; }
            public Builder fileName(String fileName) { this.fileName = fileName; return this; }
            public Builder fileSize(Integer fileSize) { this.fileSize = fileSize; return this; }
            public Builder owners(String owners) { this.owners = owners; return this; }
            
            public GitLabMetadata build() {
                return new GitLabMetadata(this);
            }
        }
    }
}