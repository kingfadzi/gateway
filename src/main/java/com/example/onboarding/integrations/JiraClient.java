package com.example.onboarding.integrations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);

    private final RestTemplate http;
    private final String baseUrl;
    private final String token; // read from env JIRA_TOKEN

    public JiraClient(@Value("${cps.jira.base-url:http://mars:8080}") String baseUrl) {
        this.baseUrl = baseUrl;

        // Read JIRA token from shell env; fail fast if missing
        String envToken = System.getenv("JIRA_TOKEN");
        if (envToken == null || envToken.isBlank()) {
            throw new IllegalStateException("JIRA_TOKEN environment variable is not set");
        }
        this.token = envToken;

        this.http = new RestTemplate();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(3));
        rf.setReadTimeout(Duration.ofSeconds(15));
        this.http.setRequestFactory(rf);

        log.info("JiraClient initialized (baseUrl={}) using Bearer token from JIRA_TOKEN", this.baseUrl);
    }

    private HttpHeaders authJson() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token); // NOTE: switch to Basic (email:apiToken) if using Jira Cloud
        return h;
    }

    /* -------------------- existing helpers -------------------- */

    public void updateIssue(String issueKey, Map<String, Object> body) {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey;
        log.debug("Jira PUT {}", url);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authJson());
        http.exchange(url, HttpMethod.PUT, req, String.class);
    }

    public void addComment(String issueKey, String comment) {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/comment";
        log.debug("Jira POST {}", url);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("body", comment), authJson());
        http.postForEntity(url, req, String.class);
    }

    public ResponseEntity<String> getFields() {
        String url = baseUrl + "/rest/api/2/field";
        log.debug("Jira GET {}", url);
        HttpEntity<Void> req = new HttpEntity<>(authJson());
        return http.exchange(url, HttpMethod.GET, req, String.class);
    }

    /** Add a Remote Link to an issue (simple MVP). */
    public void addRemoteLink(String issueKey, String title, String linkUrl) {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/remotelink";
        log.debug("Jira POST {}", url);

        Map<String, Object> object = new HashMap<>();
        object.put("title", title);
        object.put("url", linkUrl);

        Map<String, Object> body = new HashMap<>();
        body.put("object", object);
        body.put("relationship", "is required by");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authJson());
        http.postForEntity(url, req, String.class);
    }

    /* -------------------- create issues -------------------- */

    /**
     * Create a top-level Jira issue and return its key (e.g., STRAT-123).
     * @param projectKey   Project key (e.g., "STRAT")
     * @param issueType    Issue type name (e.g., "Story", "Risk")
     * @param summary      Summary text
     * @param description  Optional description (null allowed)
     * @param labels       Optional labels (null/empty allowed)
     */
    @SuppressWarnings("unchecked")
    public String createIssue(String projectKey,
                              String issueType,
                              String summary,
                              String description,
                              List<String> labels) {
        String url = baseUrl + "/rest/api/2/issue";
        log.debug("Jira POST {}", url);

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("issuetype", Map.of("name", issueType));
        fields.put("summary", summary);
        if (description != null) fields.put("description", description);
        if (labels != null && !labels.isEmpty()) fields.put("labels", labels);

        Map<String, Object> body = new HashMap<>();
        body.put("fields", fields);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authJson());
        ResponseEntity<Map> resp = http.postForEntity(url, req, Map.class);

        if (resp.getBody() == null || resp.getBody().get("key") == null) {
            throw new IllegalStateException("Jira createIssue: response missing 'key'");
        }
        String key = resp.getBody().get("key").toString();
        log.info("Created Jira issue {} (type={}, project={})", key, issueType, projectKey);
        return key;
    }

    /**
     * Create a Sub-task under a parent issue and return its key.
     * @param projectKey     Project key (e.g., "STRAT")
     * @param parentIssueKey Parent issue key (e.g., "STRAT-221")
     * @param issueType      Sub-task issue type name (usually "Sub-task")
     * @param summary        Summary text
     * @param description    Optional description
     * @param labels         Optional labels
     */
    @SuppressWarnings("unchecked")
    public String createSubtask(String projectKey,
                                String parentIssueKey,
                                String issueType,
                                String summary,
                                String description,
                                List<String> labels) {
        String url = baseUrl + "/rest/api/2/issue";
        log.debug("Jira POST {}", url);

        Map<String, Object> fields = new HashMap<>();
        // For subtasks, parent is required; project is typically implied by parent but we include it for clarity
        fields.put("parent", Map.of("key", parentIssueKey));
        fields.put("project", Map.of("key", projectKey));
        fields.put("issuetype", Map.of("name", issueType)); // ensure this is a sub-task type
        fields.put("summary", summary);
        if (description != null) fields.put("description", description);
        if (labels != null && !labels.isEmpty()) fields.put("labels", labels);

        Map<String, Object> body = new HashMap<>();
        body.put("fields", fields);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authJson());
        ResponseEntity<Map> resp = http.postForEntity(url, req, Map.class);

        if (resp.getBody() == null || resp.getBody().get("key") == null) {
            throw new IllegalStateException("Jira createSubtask: response missing 'key'");
        }
        String key = resp.getBody().get("key").toString();
        log.info("Created Jira sub-task {} (parent={}, type={}, project={})", key, parentIssueKey, issueType, projectKey);
        return key;
    }

    /* -------------------- links -------------------- */

    /**
     * Link two issues with a given link type name.
     * Direction: inwardIssue <-[type]- outwardIssue
     */
    public void linkIssues(String inwardIssueKey, String outwardIssueKey, String linkTypeName) {
        String url = baseUrl + "/rest/api/2/issueLink";
        log.debug("Jira POST {} (type={}, inward={}, outward={})", url, linkTypeName, inwardIssueKey, outwardIssueKey);

        Map<String, Object> body = new HashMap<>();
        body.put("type", Map.of("name", linkTypeName));
        body.put("inwardIssue", Map.of("key", inwardIssueKey));
        body.put("outwardIssue", Map.of("key", outwardIssueKey));

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authJson());
        http.postForEntity(url, req, String.class);

        if ("Blocks".equalsIgnoreCase(linkTypeName)) {
            // Outward blocks inward
            log.info("Linked: {} (blocks) {}", outwardIssueKey, inwardIssueKey);
        } else {
            log.info("Linked: {} <{}> {}", inwardIssueKey, linkTypeName, outwardIssueKey);
        }
    }

    /**
     * Create a "Blocks" link where `blockerIssueKey` **blocks** `blockedIssueKey`.
     * Jira semantics: outward = blocker (shows "blocks"), inward = blocked (shows "is blocked by").
     */
    public void linkIssuesBlocks(String blockerIssueKey, String blockedIssueKey) {

        linkIssues(blockerIssueKey, blockedIssueKey, "Blocks");
    }

    /** Convenience: create a symmetric "Relates" link between A and B. */
    public void linkIssuesRelates(String issueAKey, String issueBKey) {
        // "Relates" is symmetric; direction doesn't materially matter
        linkIssues(issueAKey, issueBKey, "Relates");
    }
}
