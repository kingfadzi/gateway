package dev.controlplane.auditkit.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class AuditSinkClient {
    private static final Logger log = LoggerFactory.getLogger(AuditSinkClient.class);
    
    private final RestTemplate rest;
    private final AuditProperties props;

    public AuditSinkClient(RestTemplate rest, AuditProperties props) {
        this.rest = rest;
        this.props = props;
    }

    public void send(SinkEnvelope env) {
        if (!props.isEnabled()) {
            log.debug("AUDIT SINK: Audit disabled, skipping event");
            return;
        }
        
        log.info("AUDIT SINK REQUEST: Sending to {} - action={}, subject={}, subjectType={}, outcome={}, producer={}", 
                props.getSink().getUrl(), env.action(), env.subject().id(), env.subject().type(), env.outcome(), env.producerId());
        
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
                h.set("X-Api-Key", props.getApiKey());
                log.debug("AUDIT SINK: Using API key: {}", props.getApiKey());
            }
            HttpEntity<SinkEnvelope> entity = new HttpEntity<>(env, h);
            
            log.debug("AUDIT SINK: Request headers={}", h);
            log.debug("AUDIT SINK: Request body={}", env);
            
            ResponseEntity<String> response = rest.exchange(props.getSink().getUrl(), HttpMethod.POST, entity, String.class);
            log.info("AUDIT SINK RESPONSE: SUCCESS - status={}, body={}", response.getStatusCode(), response.getBody());
            
        } catch (Exception e) {
            log.error("AUDIT SINK RESPONSE: FAILED to send audit event to {}: {} - {}", 
                    props.getSink().getUrl(), e.getClass().getSimpleName(), e.getMessage(), e);
            
            if (props.isLogOnFailure()) {
                log.info("AUDIT_EVENT: action={}, subject={}, subjectType={}, outcome={}, producer={}", 
                        env.action(), env.subject().id(), env.subject().type(), env.outcome(), env.producerId());
                if (env.context() != null) {
                    log.info("AUDIT_CONTEXT: appId={}, trackId={}, releaseId={}, jiraKey={}, snowSysId={}", 
                            env.context().appId(), env.context().trackId(), env.context().releaseId(), 
                            env.context().jiraKey(), env.context().snowSysId());
                }
            }
        }
    }
}
