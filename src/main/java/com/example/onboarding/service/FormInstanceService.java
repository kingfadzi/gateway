package com.example.onboarding.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FormInstanceService {

    private final Map<String, FormInstance> memory = new ConcurrentHashMap<>();

    @Value("${cps.forms.base-url:http://localhost:5173/forms}")
    private String formBaseUrl;

    public FormInstance findOrCreate(String issueKey, String packId, String version) {
        String key = issueKey + ":" + packId + ":" + version;
        return memory.computeIfAbsent(key, k -> {
            FormInstance fi = new FormInstance();
            fi.setId(UUID.randomUUID());
            fi.setJiraIssueKey(issueKey);
            fi.setPackId(packId);
            fi.setVersion(version);
            fi.setToken(UUID.randomUUID().toString()); // simple token for MVP
            fi.setStatus("DRAFT");
            fi.setDataJson("{}");
            return fi;
        });
    }

    public String publicUrl(FormInstance fi) {
        // e.g., http://localhost:5173/forms/<id>?t=<token>
        String base = formBaseUrl.endsWith("/") ? formBaseUrl.substring(0, formBaseUrl.length() - 1) : formBaseUrl;
        return base + "/" + fi.getId() + "?t=" + fi.getToken();
    }
}
