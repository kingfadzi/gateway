package com.example.onboarding.dto.evidence;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request DTO for creating evidence with a new document in a single operation
 */
public record CreateEvidenceWithDocumentRequest(
        String profileFieldId,              // required - links to profile_field
        CreateDocumentRequest document,     // required - document to create
        CreateEvidenceMetadataRequest evidence  // optional - evidence metadata
) {
    
    /**
     * Document creation details
     */
    public record CreateDocumentRequest(
            String title,                   // required - document title
            String url,                     // required - document URL
            List<String> fieldTypes         // optional - field types this document supports
    ) {}
    
    /**
     * Evidence metadata (optional fields will use defaults)
     */
    public record CreateEvidenceMetadataRequest(
            String type,                    // optional - defaults to "document"
            String sourceSystem,            // optional - defaults to "manual"
            String submittedBy,             // optional - should be current user
            OffsetDateTime validFrom,       // optional - defaults to now
            OffsetDateTime validUntil,      // optional - no expiry
            String relatedEvidenceFields,   // optional - additional related evidence fields
            String trackId                  // optional - link to track
    ) {}
}