package com.example.gateway.evidence.dto;

/**
 * Request to approve or reject evidence during SME review workflow.
 *
 * Used by POST /api/evidence/{evidenceId}/review endpoint.
 */
public record ReviewEvidenceRequest(
    String action,         // "approve" or "reject"
    String reviewerId,     // SME identifier (e.g., sme@example.com)
    String reviewComment   // Optional review notes
) {
    /**
     * Validate that required fields are present and action is valid.
     */
    public boolean isValid() {
        if (action == null || action.trim().isEmpty()) {
            return false;
        }
        if (reviewerId == null || reviewerId.trim().isEmpty()) {
            return false;
        }
        String normalizedAction = action.trim().toLowerCase();
        return "approve".equals(normalizedAction) || "reject".equals(normalizedAction);
    }

    /**
     * Check if this is an approval (vs rejection).
     */
    public boolean isApproval() {
        return action != null && "approve".equalsIgnoreCase(action.trim());
    }
}
