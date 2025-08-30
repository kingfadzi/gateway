package dev.controlplane.auditkit.context;

import java.util.List;

public final class ActorContext {
    public final String actorId;
    public final String actorType; // USER | SERVICE
    public final List<String> roles;
    public final String tenantId;

    public ActorContext(String actorId, String actorType, List<String> roles, String tenantId) {
        this.actorId = actorId;
        this.actorType = actorType;
        this.roles = roles;
        this.tenantId = tenantId;
    }
}
