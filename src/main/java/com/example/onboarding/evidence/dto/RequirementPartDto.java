// com.example.onboarding.dto.requirements.RequirementPartDto
package com.example.onboarding.evidence.dto;

public class RequirementPartDto {
    private String id;
    private String title;
    private String profileField;   // underscore key, e.g. "security_encryption_at_rest"
    private Integer maxAgeDays;    // nullable → infinite
    private String typeExpected;   // e.g. "link|document|…"
    private ReuseCandidate reuseCandidate; // <— NEW (nullable)

    // getters/setters …
    public ReuseCandidate getReuseCandidate() { return reuseCandidate; }
    public void setReuseCandidate(ReuseCandidate reuseCandidate) { this.reuseCandidate = reuseCandidate; }
}
