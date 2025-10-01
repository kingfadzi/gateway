// src/main/java/com/example/onboarding/dto/policy/PolicyInput.java
package com.example.gateway.policy.dto;

public record PolicyInput(
    App app,
    String criticality,
    String security,
    String integrity,
    String availability,
    String resilience,
    boolean has_dependencies,
    Release release
) {
    public record App(String id) {}
    public record Release(String id, String window_start) {}
}
