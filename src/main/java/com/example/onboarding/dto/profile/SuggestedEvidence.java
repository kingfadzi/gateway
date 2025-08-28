package com.example.onboarding.dto.profile;

import com.example.onboarding.dto.document.DocumentSummary;
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