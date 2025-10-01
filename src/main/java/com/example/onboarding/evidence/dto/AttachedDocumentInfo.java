package com.example.onboarding.evidence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * Information about a document attached as evidence to a profile field
 */
public record AttachedDocumentInfo(
    @JsonProperty("documentId")
    String documentId,
    
    @JsonProperty("evidenceId")
    String evidenceId,
    
    @JsonProperty("title")
    String title,
    
    @JsonProperty("url")
    String url,
    
    @JsonProperty("attachedAt")
    OffsetDateTime attachedAt,
    
    @JsonProperty("sourceSystem")
    String sourceSystem,
    
    @JsonProperty("submittedBy")
    String submittedBy
) {
}