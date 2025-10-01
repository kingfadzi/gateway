package com.example.gateway.registry.dto;

import java.util.List;

public record ComplianceFramework(
    String framework,
    List<String> controls
) {
}