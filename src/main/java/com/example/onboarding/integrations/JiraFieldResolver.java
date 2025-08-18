package com.example.onboarding.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class JiraFieldResolver {
    private final JiraClient jiraClient;
    private final ObjectMapper om = new ObjectMapper();
    private Map<String, String> cache;

    public JiraFieldResolver(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    public synchronized String idFor(String displayName) {
        if (cache == null) load();
        return cache.get(displayName);
    }

    private void load() {
        ResponseEntity<String> resp = jiraClient.getFields();
        cache = new HashMap<>();
        try {
            JsonNode arr = om.readTree(resp.getBody());
            for (Iterator<JsonNode> it = arr.elements(); it.hasNext();) {
                JsonNode f = it.next();
                cache.put(f.get("name").asText(), f.get("id").asText());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed loading Jira fields", e);
        }
    }
}
