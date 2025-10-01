// src/main/java/com/example/onboarding/policy/OpaClient.java
package com.example.onboarding.integrations;

import com.example.onboarding.config.OpaProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.example.onboarding.policy.dto.OpaRequest;
import com.example.onboarding.policy.dto.OpaResponse;
import com.example.onboarding.policy.dto.PolicyDecision;


@Service
public class OpaClient {

    private final RestTemplate rest;
    private final OpaProperties props;

    public OpaClient(RestTemplate restTemplate, OpaProperties props) {
        this.rest = restTemplate;
        this.props = props;
    }

    public PolicyDecision evaluate(OpaRequest req) {
        String url = props.getBaseUrl() + props.getPath();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (props.getAuthHeader() != null && !props.getAuthHeader().isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, props.getAuthHeader());
        }

        HttpEntity<OpaRequest> entity = new HttpEntity<>(req, headers);
        ResponseEntity<OpaResponse<PolicyDecision>> resp =
            rest.exchange(url, HttpMethod.POST, entity,
                new org.springframework.core.ParameterizedTypeReference<OpaResponse<PolicyDecision>>() {});

        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null && resp.getBody().result() != null) {
            return resp.getBody().result();
        }
        throw new IllegalStateException("OPA evaluate failed: " + resp.getStatusCode());
    }
}
