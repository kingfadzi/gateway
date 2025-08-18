package com.example.onboarding.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentInstance {
    private String serviceCorrelationId;
    private String serviceName;
    private String appCorrelationId;
    private String appName;
    private String instanceCorrelationId;
    private String instanceName;
    private String environment;
    private String installType;
}
