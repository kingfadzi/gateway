package com.example.gateway.evidence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response containing all documents currently attached to a profile field
 */
public record AttachedDocumentsResponse(
    @JsonProperty("profileFieldId")
    String profileFieldId,
    
    @JsonProperty("appId")
    String appId,
    
    @JsonProperty("documents")
    List<AttachedDocumentInfo> documents
) {
}