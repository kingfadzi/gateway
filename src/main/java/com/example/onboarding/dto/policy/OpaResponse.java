// src/main/java/com/example/onboarding/dto/policy/OpaResponse.java
package com.example.onboarding.dto.policy;

// OPA returns: {"result": <T>}
public record OpaResponse<T>(T result) {}
