// src/main/java/com/example/onboarding/policy/InternalPolicyController.java
package com.example.onboarding.controller.policy;

import com.example.onboarding.dto.policy.OpaRequest;
import com.example.onboarding.dto.policy.PolicyDecision;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.example.onboarding.integrations.OpaClient;

@RestController
@RequestMapping("/internal/policy")
public class InternalPolicyController {

    private final OpaClient opaClient;

    public InternalPolicyController(OpaClient opaClient) {
        this.opaClient = opaClient;
    }

    @PostMapping(value = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PolicyDecision evaluate(@RequestBody OpaRequest req) {
        return opaClient.evaluate(req); // pass-through of OPA result
    }
}
