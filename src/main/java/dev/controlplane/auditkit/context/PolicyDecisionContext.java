package dev.controlplane.auditkit.context;

public final class PolicyDecisionContext {
    public final String decisionId;
    public final String rulePath;
    public PolicyDecisionContext(String decisionId, String rulePath) {
        this.decisionId = decisionId;
        this.rulePath = rulePath;
    }
}
