package com.example.onboarding.registry.dto;

import java.util.List;

public record ComplianceFramework(
    String framework,
    List<String> controls
) {
}