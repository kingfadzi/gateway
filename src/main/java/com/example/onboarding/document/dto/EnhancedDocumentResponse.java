package com.example.onboarding.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Enhanced document response that includes attachment information when relevant
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnhancedDocumentResponse(
    @JsonProperty("documentId")
    String documentId,
    
    @JsonProperty("appId") 
    String appId,
    
    @JsonProperty("title")
    String title,
    
    @JsonProperty("canonicalUrl")
    String canonicalUrl,
    
    @JsonProperty("sourceType")
    String sourceType,
    
    @JsonProperty("owners")
    String owners,
    
    @JsonProperty("linkHealth")
    Integer linkHealth,
    
    @JsonProperty("relatedEvidenceFields")
    List<String> relatedEvidenceFields,
    
    @JsonProperty("latestVersion")
    DocumentVersionInfo latestVersion,
    
    @JsonProperty("createdAt")
    OffsetDateTime createdAt,
    
    @JsonProperty("updatedAt")
    OffsetDateTime updatedAt,
    
    // Attachment-specific fields (only present when attached to a profile field)
    @JsonProperty("isAttachedToField")
    Boolean isAttachedToField,
    
    @JsonProperty("attachedAt")
    OffsetDateTime attachedAt,
    
    @JsonProperty("evidenceId")
    String evidenceId,
    
    @JsonProperty("sourceSystem")
    String sourceSystem,
    
    @JsonProperty("submittedBy")
    String submittedBy
) {
    
    /**
     * Create from DocumentResponse without attachment info
     */
    public static EnhancedDocumentResponse fromDocumentResponse(DocumentResponse doc) {
        return new EnhancedDocumentResponse(
            doc.documentId(),
            doc.appId(),
            doc.title(),
            doc.canonicalUrl(),
            doc.sourceType(),
            doc.owners(),
            doc.linkHealth(),
            doc.relatedEvidenceFields(),
            doc.latestVersion(),
            doc.createdAt(),
            doc.updatedAt(),
            false, // isAttachedToField
            null,  // attachedAt
            null,  // evidenceId
            null,  // sourceSystem
            null   // submittedBy
        );
    }
    
    /**
     * Create from DocumentResponse with attachment info
     */
    public static EnhancedDocumentResponse fromDocumentResponseWithAttachment(
            DocumentResponse doc, 
            String evidenceId, 
            OffsetDateTime attachedAt, 
            String sourceSystem, 
            String submittedBy) {
        return new EnhancedDocumentResponse(
            doc.documentId(),
            doc.appId(),
            doc.title(),
            doc.canonicalUrl(),
            doc.sourceType(),
            doc.owners(),
            doc.linkHealth(),
            doc.relatedEvidenceFields(),
            doc.latestVersion(),
            doc.createdAt(),
            doc.updatedAt(),
            true,      // isAttachedToField
            attachedAt,
            evidenceId,
            sourceSystem,
            submittedBy
        );
    }
}