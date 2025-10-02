package com.example.gateway.document.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

/**
 * Centralized HTTP client for platform API calls (GitLab, Confluence, etc.).
 * Eliminates duplicate HTTP call patterns across metadata services.
 */
@Component
public class PlatformApiClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformApiClient.class);
    private static final String USER_AGENT = "DocumentManagementService/1.0";

    private final RestTemplate restTemplate;

    public PlatformApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Perform a GET request to a platform API and return the response body if successful.
     *
     * @param url          The full URL to call
     * @param accessToken  Access token for authentication (can be null)
     * @param responseType The expected response class type
     * @param context      Context string for logging (e.g., "GitLab project", "Confluence page")
     * @param <T>          The response type
     * @return Optional containing the response body if successful, empty otherwise
     */
    public <T> Optional<T> get(String url, String accessToken, Class<T> responseType, String context) {
        try {
            log.debug("Fetching {} from API: {}", context, url);

            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<T> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, entity, responseType);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Successfully fetched {} from API", context);
                return Optional.of(response.getBody());
            } else {
                log.warn("API returned non-success status: {} for {}", response.getStatusCode(), context);
                return Optional.empty();
            }

        } catch (RestClientException e) {
            log.error("Failed to fetch {} from {}: {}", context, url, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Perform a GET request and return the response as a String (for raw content like CODEOWNERS).
     *
     * @param url          The full URL to call
     * @param accessToken  Access token for authentication (can be null)
     * @param context      Context string for logging
     * @return Optional containing the response string if successful, empty otherwise
     */
    public Optional<String> getRaw(String url, String accessToken, String context) {
        return get(url, accessToken, String.class, context);
    }

    /**
     * Create HTTP headers with authentication and user agent.
     *
     * @param accessToken Access token for Bearer authentication (can be null)
     * @return HttpHeaders with appropriate headers set
     */
    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            headers.set("Authorization", "Bearer " + accessToken);
        }
        headers.set("User-Agent", USER_AGENT);
        return headers;
    }

    /**
     * Create HTTP headers with Basic authentication (for Confluence).
     *
     * @param username Username for Basic auth
     * @param password Password for Basic auth
     * @return HttpHeaders with Basic authentication
     */
    public <T> Optional<T> getWithBasicAuth(String url, String username, String password,
                                             Class<T> responseType, String context) {
        try {
            log.debug("Fetching {} from API: {}", context, url);

            HttpHeaders headers = new HttpHeaders();
            String auth = username + ":" + password;
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
            headers.set("User-Agent", USER_AGENT);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<T> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, entity, responseType);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Successfully fetched {} from API", context);
                return Optional.of(response.getBody());
            } else {
                log.warn("API returned non-success status: {} for {}", response.getStatusCode(), context);
                return Optional.empty();
            }

        } catch (RestClientException e) {
            log.error("Failed to fetch {} from {}: {}", context, url, e.getMessage());
            return Optional.empty();
        }
    }
}
