// src/main/java/com/example/onboarding/dto/policy/OpaResponse.java
package com.example.onboarding.policy.dto;

// OPA returns: {"result": <T>}
public record OpaResponse<T>(T result) {}
