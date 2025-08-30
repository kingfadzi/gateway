package dev.controlplane.auditkit.context;

public final class CorrelationContext {
    public final String correlationId;
    public final String traceId;
    public final String channel; // UI | API | WEBHOOK | JOB
    public final String ip;
    public final String userAgent;

    public CorrelationContext(String correlationId, String traceId, String channel, String ip, String userAgent) {
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.channel = channel;
        this.ip = ip;
        this.userAgent = userAgent;
    }
}
