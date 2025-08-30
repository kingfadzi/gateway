package dev.controlplane.auditkit.context;

public final class ActorContextHolder {
    private static final ThreadLocal<ActorContext> TL = new ThreadLocal<>();
    public static void set(ActorContext ctx) { TL.set(ctx); }
    public static ActorContext get() { return TL.get(); }
    public static void clear() { TL.remove(); }
}
