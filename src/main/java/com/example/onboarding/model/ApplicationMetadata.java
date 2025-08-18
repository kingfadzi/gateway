package com.example.onboarding.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ApplicationMetadata {
    private String appId;
    private String appName;
    private boolean active;
    private String owningTransactionCycle;
    private String owningTransactionCycleId;
    private String resilienceCategory;
    private String operationalStatus;
    private String applicationType;
    private String architectureType;
    private String installType;
    private String applicationParent;
    private String applicationParentCorrelationId;
    private String housePosition;
    private LocalDate ceaseDate;
    private String businessApplicationSysId;
    private String applicationTier;
    private String componentId;
    private String applicationProductOwner;
    private String systemArchitect;
    private List<ApplicationMetadata> children = new ArrayList<>();
}
