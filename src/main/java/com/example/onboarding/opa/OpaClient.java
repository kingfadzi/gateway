// src/main/java/com/example/onboarding/opa/OpaClient.java
package com.example.onboarding.opa;

import com.example.onboarding.config.OpaProperties;
import com.example.onboarding.opa.OpaModels.OpaRequest;
import com.example.onboarding.opa.OpaModels.OpaResponse;
import com.example.onboarding.opa.OpaModels.OpaResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class OpaClient {

    private static final Logger log = LoggerFactory.getLogger(OpaClient.class);
    private static final int MAX_LOG_CHARS = 100_000;

    private final RestTemplate restTemplate;
    private final OpaProperties props;
    private final ObjectMapper om;

    public OpaClient(RestTemplate restTemplate, OpaProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.om = new ObjectMapper()
                .findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public OpaResponse evaluate(OpaRequest request) {
        try {
            String reqJson = om.writerWithDefaultPrettyPrinter().writeValueAsString(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(reqJson, headers);

            long t0 = System.currentTimeMillis();
            ResponseEntity<String> httpResp =
                    restTemplate.exchange(props.getUrl(), HttpMethod.POST, entity, String.class);
            long ms = System.currentTimeMillis() - t0;

            String body = httpResp.getBody() == null ? "" : httpResp.getBody();

            // Log request/response
            log.info("OPA request -> {}\n{}", props.getUrl(), truncate(reqJson));
            log.info("OPA response (status={} in {}ms):\n{}",
                    httpResp.getStatusCode().value(), ms, truncate(pretty(body)));

            // ---- Normalize: pick result.result when present, else result ----
            JsonNode root = om.readTree(body);
            JsonNode resultNode = root.path("result");
            JsonNode decisionNode = resultNode.has("result") ? resultNode.path("result") : resultNode;

            OpaResult decision = om.treeToValue(decisionNode, OpaResult.class);
            OpaResponse resp = new OpaResponse();
            resp.result = decision;
            return resp;

        } catch (RestClientResponseException e) {
            log.error("OPA HTTP error (status={}):\n{}", e.getRawStatusCode(), truncate(e.getResponseBodyAsString()));
            return new OpaResponse(); // caller: result == null
        } catch (Exception e) {
            log.error("OPA evaluate failed: {}", e.toString(), e);
            return new OpaResponse();
        }
    }

    private String pretty(String json) {
        try {
            if (json == null || json.isBlank()) return json;
            Object tree = om.readValue(json, Object.class);
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        } catch (Exception ignore) {
            return json;
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_LOG_CHARS ? s : s.substring(0, MAX_LOG_CHARS) + "\n... [truncated]";
    }
}
