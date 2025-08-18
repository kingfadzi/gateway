package com.example.onboarding.service;

import java.util.UUID;

public class FormInstance {
    private UUID id;
    private String jiraIssueKey;
    private String packId;
    private String version;
    private String token;
    private String status;   // DRAFT | SUBMITTED
    private String dataJson; // "{}" initially

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getJiraIssueKey() { return jiraIssueKey; }
    public void setJiraIssueKey(String jiraIssueKey) { this.jiraIssueKey = jiraIssueKey; }
    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
}
