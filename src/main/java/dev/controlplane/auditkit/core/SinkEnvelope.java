package dev.controlplane.auditkit.core;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record SinkEnvelope(
    Integer schemaVersion,
    String producerId,
    OffsetDateTime occurredAtUtc,
    String action,
    String outcome,
    Subject subject,
    Actor actor,
    Context context,
    String channel,
    String correlationId,
    String traceId,
    Policy policy,
    Payload payload,
    ErrorInfo error,
    String idempotencyKey
) {
    public record Subject(String type, String id) {}
    public record Actor(String id, String type, List<String> roles, String tenantId) {}
    public record Context(String appId, String trackId, String releaseId, String jiraKey, String snowSysId) {}
    public record Policy(String decisionId, String rulePath) {}
    public record Payload(Map<String,Object> argsRedacted, Map<String,Object> resultRedacted, String payloadHash) {}
    public record ErrorInfo(String errorType, String errorMessageHash) {}
}
