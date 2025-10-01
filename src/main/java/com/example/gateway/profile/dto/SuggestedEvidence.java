package com.example.gateway.profile.dto;

import com.example.gateway.document.dto.DocumentSummary;
import java.util.List;

/**
 * Suggested evidence for a profile field based on document related evidence fields
 */
public record SuggestedEvidence(
        String fieldKey,
        String fieldLabel,
        List<DocumentSummary> suggestedDocuments,
        int totalSuggestions,
        String matchCriteria
) {}