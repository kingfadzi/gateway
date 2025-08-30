package dev.controlplane.auditkit.context;

public final class PolicyDecisionContextHolder {
    private static final ThreadLocal<PolicyDecisionContext> TL = new ThreadLocal<>();
    public static void set(PolicyDecisionContext ctx) { TL.set(ctx); }
    public static PolicyDecisionContext get() { return TL.get(); }
    public static void clear() { TL.remove(); }
}
