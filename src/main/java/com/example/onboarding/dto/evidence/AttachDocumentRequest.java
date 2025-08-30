package com.example.onboarding.dto.evidence;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request to attach an existing document as evidence to a profile field
 */
public record AttachDocumentRequest(
    @JsonProperty("documentId")
    String documentId
) {
}