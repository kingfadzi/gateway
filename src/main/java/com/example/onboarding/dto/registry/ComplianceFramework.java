package com.example.onboarding.dto.registry;

import java.util.List;

public record ComplianceFramework(
    String framework,
    List<String> controls
) {
}