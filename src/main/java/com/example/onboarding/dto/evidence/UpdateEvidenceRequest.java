package com.example.onboarding.dto.evidence;

import java.time.OffsetDateTime;

/**
 * Request DTO for updating existing evidence
 */
public record UpdateEvidenceRequest(
        String uri,                 // optional - update evidence location
        String type,                // optional - update evidence type
        String sourceSystem,        // optional - update source system
        String submittedBy,         // optional - update submitter
        OffsetDateTime validFrom,   // optional - update validity start
        OffsetDateTime validUntil,  // optional - update validity end
        String status,              // optional - update status ('active'|'superseded'|'revoked')
        String reviewedBy,          // optional - who reviewed the evidence
        String relatedEvidenceFields, // optional - update related evidence fields
        String documentId,          // optional - update document reference
        String docVersionId         // optional - update document version reference
) {}