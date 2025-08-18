package com.example.onboarding.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class AppMetadataResponse {
    private String parentName;
    private String businessAppName;
    private String appId;
    private String transactionCycle;
    private String applicationOwner;
    private String systemArchitect;
    private String operationalStatus;
    private String applicationType;
    private String architectureType;
    private String installType;
    private List<AppComponent> applicationComponents;

    public AppMetadataResponse(ApplicationMetadata app, List<AppComponent> children) {
        this.parentName = app.getApplicationParent();
        this.businessAppName = app.getAppName();
        this.appId = app.getAppId();
        this.transactionCycle = app.getOwningTransactionCycle();
        this.applicationOwner = app.getApplicationProductOwner();
        this.systemArchitect = app.getSystemArchitect();
        this.operationalStatus = app.getOperationalStatus();
        this.applicationType = app.getApplicationType();
        this.architectureType = app.getArchitectureType();
        this.installType = app.getInstallType();
        this.applicationComponents = children;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class AppComponent {
        private String name;
        private String appId;
    }
}
