package com.example.gateway.risk.dto;

/**
 * Request DTO for SME review actions on risk items.
 * Supports multiple review actions with optional fields.
 *
 * Supported actions:
 * - approve: SME accepts the risk
 * - approve_with_mitigation: SME accepts with required mitigation plan
 * - reject: SME rejects and requires remediation
 * - request_info: SME needs more information
 * - assign_other: Reassign to another SME
 * - escalate: Escalate the risk
 */
public record SmeReviewRequest(
        String action,           // One of: approve, approve_with_mitigation, reject, request_info, assign_other, escalate
        String comments,         // Review comments from SME
        String smeId,            // SME identifier (email or ID)
        String mitigationPlan,   // Required for approve_with_mitigation action
        String assignToSme       // Required for assign_other action
) {
    /**
     * Validate the request has required fields.
     */
    public boolean isValid() {
        if (action == null || action.isBlank() || smeId == null || smeId.isBlank()) {
            return false;
        }

        // Validate action is one of the supported values
        String lowerAction = action.toLowerCase();
        boolean validAction = lowerAction.equals("approve")
                || lowerAction.equals("approve_with_mitigation")
                || lowerAction.equals("reject")
                || lowerAction.equals("request_info")
                || lowerAction.equals("assign_other")
                || lowerAction.equals("escalate");

        if (!validAction) {
            return false;
        }

        // Validate required fields for specific actions
        if (lowerAction.equals("approve_with_mitigation") && (mitigationPlan == null || mitigationPlan.isBlank())) {
            return false;
        }

        if (lowerAction.equals("assign_other") && (assignToSme == null || assignToSme.isBlank())) {
            return false;
        }

        return true;
    }

    /**
     * Check if this is an approve action.
     */
    public boolean isApprove() {
        return "approve".equalsIgnoreCase(action);
    }

    /**
     * Check if this is an approve with mitigation action.
     */
    public boolean isApproveWithMitigation() {
        return "approve_with_mitigation".equalsIgnoreCase(action);
    }

    /**
     * Check if this is a reject action.
     */
    public boolean isReject() {
        return "reject".equalsIgnoreCase(action);
    }

    /**
     * Check if this is a request info action.
     */
    public boolean isRequestInfo() {
        return "request_info".equalsIgnoreCase(action);
    }

    /**
     * Check if this is an assign other action.
     */
    public boolean isAssignOther() {
        return "assign_other".equalsIgnoreCase(action);
    }

    /**
     * Check if this is an escalate action.
     */
    public boolean isEscalate() {
        return "escalate".equalsIgnoreCase(action);
    }
}
