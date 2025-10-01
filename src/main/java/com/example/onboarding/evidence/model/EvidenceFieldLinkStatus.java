package com.example.onboarding.evidence.model;

public enum EvidenceFieldLinkStatus {
    ATTACHED,
    PENDING_REVIEW,       // Legacy status - maps to PENDING_SME_REVIEW
    PENDING_PO_REVIEW,    // Product Owner attestation needed (low criticality)
    PENDING_SME_REVIEW,   // SME review needed (high criticality)
    APPROVED,             // Formally approved by reviewer
    USER_ATTESTED,        // User self-attested (completed state)
    REJECTED
}