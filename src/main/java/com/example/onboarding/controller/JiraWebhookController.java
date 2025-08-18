package com.example.onboarding.controller;

import com.example.onboarding.service.JiraWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/jira")
public class JiraWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(JiraWebhookController.class);

    private final JiraWebhookService service;

    public JiraWebhookController(JiraWebhookService service) {
        this.service = service;
    }

    /**
     * Primary endpoint for Jira webhooks.
     * Configure Jira to POST issue events here.
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String payload) {

        logger.info("Received POST /webhooks/jira (payloadSize={} bytes)", payload == null ? 0 : payload.length());

        try {
            service.process(headers, payload);
            logger.info("Webhook processed successfully");
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Webhook rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            logger.warn("Webhook unauthorized: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            logger.error("Error while processing Jira webhook", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Simple health probe to verify routing and auth outside of Jira.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        logger.info("Received GET /webhooks/jira/health");
        return ResponseEntity.ok("jira-webhook: OK");
    }
}
