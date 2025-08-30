package dev.controlplane.auditkit.context;

public final class CorrelationContextHolder {
    private static final ThreadLocal<CorrelationContext> TL = new ThreadLocal<>();
    public static void set(CorrelationContext ctx) { TL.set(ctx); }
    public static CorrelationContext get() { return TL.get(); }
    public static void clear() { TL.remove(); }
}
