package com.example.onboarding.evidence.dto;

import com.example.onboarding.document.dto.EnhancedDocumentResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Enhanced response containing all documents currently attached to a profile field
 * with full document metadata
 */
public record EnhancedAttachedDocumentsResponse(
    @JsonProperty("profileFieldId")
    String profileFieldId,
    
    @JsonProperty("appId")
    String appId,
    
    @JsonProperty("documents")
    List<EnhancedDocumentResponse> documents
) {
}