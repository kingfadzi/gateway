package dev.controlplane.auditkit.core;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class AuditSinkClient {
    private final RestTemplate rest;
    private final AuditProperties props;

    public AuditSinkClient(RestTemplate rest, AuditProperties props) {
        this.rest = rest;
        this.props = props;
    }

    public void send(SinkEnvelope env) {
        if (!props.isEnabled()) return;
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            h.set("X-Api-Key", props.getApiKey());
        }
        HttpEntity<SinkEnvelope> entity = new HttpEntity<>(env, h);
        rest.exchange(props.getSink().getUrl(), HttpMethod.POST, entity, String.class);
    }
}
