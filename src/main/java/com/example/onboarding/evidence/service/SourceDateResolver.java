package com.example.onboarding.evidence.service;

import com.example.onboarding.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Service for resolving the source date of evidence with robust fallback logic.
 * Priority order:
 * 1. Document source date (GitLab commit, Confluence modified date)
 * 2. Evidence validFrom (if explicitly set)
 * 3. Evidence created_at (fallback)
 */
@Service
public class SourceDateResolver {

    private static final Logger log = LoggerFactory.getLogger(SourceDateResolver.class);
    
    private final DocumentRepository documentRepository;

    public SourceDateResolver(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Resolve the source date for evidence freshness calculation.
     * Uses fallback hierarchy to ensure we always have a date to work with.
     */
    public OffsetDateTime resolveSourceDate(String docVersionId, OffsetDateTime validFrom, OffsetDateTime createdAt) {
        log.debug("Resolving source date - docVersionId: {}, validFrom: {}, createdAt: {}", 
                  docVersionId, validFrom, createdAt);
        
        // Priority 1: Document source date (from GitLab, Confluence, etc.)
        if (docVersionId != null) {
            OffsetDateTime sourceDate = getDocumentSourceDate(docVersionId);
            if (sourceDate != null) {
                log.debug("Using document source date: {}", sourceDate);
                return sourceDate;
            }
        }
        
        // Priority 2: Explicit validFrom if provided
        if (validFrom != null) {
            log.debug("Using explicit validFrom date: {}", validFrom);
            return validFrom;
        }
        
        // Priority 3: Evidence created_at as final fallback
        log.debug("Using created_at as fallback: {}", createdAt);
        return createdAt;
    }

    /**
     * Get source date from document version (GitLab commit date, Confluence modified date, etc.)
     */
    private OffsetDateTime getDocumentSourceDate(String docVersionId) {
        try {
            return documentRepository.getSourceDateByVersionId(docVersionId);
        } catch (Exception e) {
            log.warn("Failed to retrieve source date for document version {}: {}", docVersionId, e.getMessage());
            return null;
        }
    }
}