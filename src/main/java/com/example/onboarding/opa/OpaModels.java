// src/main/java/com/example/onboarding/opa/OpaModels.java
package com.example.onboarding.opa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class OpaModels {

    // ---- Request ----
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpaInput {
        public String criticality;
        public String security;
        public String integrity;
        public String availability;
        public String resilience;
        public String confidentiality;
        @JsonProperty("has_dependencies")
        public Boolean hasDependencies;
    }

    public static class OpaRequest {
        public OpaInput input;
        public OpaRequest() {}
        public OpaRequest(OpaInput input) { this.input = input; }
    }

    // ---- Response ----
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpaResult {
        @JsonProperty("arb_domains")
        public List<String> arbDomains;

        @JsonProperty("assessment_mandatory")
        public Boolean assessmentMandatory;

        @JsonProperty("assessment_required")
        public Boolean assessmentRequired;

        @JsonProperty("attestation_required")
        public Boolean attestationRequired;

        @JsonProperty("fired_rules")
        public List<String> firedRules;

        @JsonProperty("questionnaire_required")
        public Boolean questionnaireRequired;

        @JsonProperty("review_mode")
        public String reviewMode;
    }

    public static class OpaResponse {
        public OpaResult result;

        // If OPA returns {"errors":[...]} or other keys, capture without failing
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Map<String, Object> errors;
    }
}
