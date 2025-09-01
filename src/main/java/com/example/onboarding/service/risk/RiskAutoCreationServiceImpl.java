package com.example.onboarding.service.risk;

import com.example.onboarding.dto.risk.AutoRiskCreationResponse;
import com.example.onboarding.dto.registry.RegistryRuleEvaluation;
import com.example.onboarding.model.RiskCreationType;
import com.example.onboarding.model.RiskStatus;
import com.example.onboarding.model.risk.RiskStory;
import com.example.onboarding.repository.risk.RiskStoryRepository;
import com.example.onboarding.service.RegistryRiskConfigService;
import com.example.onboarding.service.track.TrackService;
import com.example.onboarding.service.profile.ProfileService;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;
import com.example.onboarding.repository.application.ApplicationManagementRepository;
import com.example.onboarding.service.ComplianceContextService;
import com.example.onboarding.dto.profile.ProfileFieldTypeInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Optional;

@Service
public class RiskAutoCreationServiceImpl implements RiskAutoCreationService {
    
    private static final Logger log = LoggerFactory.getLogger(RiskAutoCreationServiceImpl.class);

    private final RegistryRiskConfigService registryRiskConfigService;
    private final RiskStoryRepository riskStoryRepository;
    private final TrackService trackService;
    private final ProfileService profileService;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    private final ApplicationManagementRepository applicationRepository;
    private final ComplianceContextService complianceContextService;

    public RiskAutoCreationServiceImpl(RegistryRiskConfigService registryRiskConfigService,
                                      RiskStoryRepository riskStoryRepository,
                                      TrackService trackService,
                                      ProfileService profileService,
                                      ProfileFieldRegistryService profileFieldRegistryService,
                                      ApplicationManagementRepository applicationRepository,
                                      ComplianceContextService complianceContextService) {
        this.registryRiskConfigService = registryRiskConfigService;
        this.riskStoryRepository = riskStoryRepository;
        this.trackService = trackService;
        this.profileService = profileService;
        this.profileFieldRegistryService = profileFieldRegistryService;
        this.applicationRepository = applicationRepository;
        this.complianceContextService = complianceContextService;
    }

    @Override
    @Transactional
    public AutoRiskCreationResponse evaluateAndCreateRisk(String evidenceId, String profileFieldId, String appId) {
        log.info("=== AUTO-RISK EVALUATION START ===");
        log.info("Evidence ID: {}, Profile Field ID: {}, App ID: {}", evidenceId, profileFieldId, appId);
        
        String fieldKey = getFieldKeyFromProfileFieldId(profileFieldId);
        log.info("Resolved field key: {}", fieldKey);
        
        String appRating = getAppRatingForField(appId, fieldKey);
        log.info("App rating for field {}: {}", fieldKey, appRating);
        
        // Evaluate if risk should be created based on registry config
        RegistryRuleEvaluation evaluation = registryRiskConfigService.evaluateRiskCreation(fieldKey, appId, appRating);
        boolean shouldCreateRisk = evaluation.shouldCreateRisk();
        
        log.info("Risk evaluation result: field={}, criticality={}, requiresReview={}, reason={}", 
            fieldKey, appRating, evaluation.shouldCreateRisk(), evaluation.evaluationReason());
        
        if (!shouldCreateRisk) {
            log.info("Risk creation SKIPPED: {}", evaluation.evaluationReason());
            return AutoRiskCreationResponse.notCreated(fieldKey, appId, appRating, 
                                                      "Field does not require review for this rating level");
        }

        // Check if risk already exists for this field and evidence
        boolean riskExists = riskStoryRepository.existsByAppIdAndFieldKeyAndTriggeringEvidenceId(
                appId, fieldKey, evidenceId);
        
        log.info("Risk already exists check: exists={}", riskExists);
        
        if (riskExists) {
            log.info("Risk creation SKIPPED: Risk already exists for evidence {} and field {}", evidenceId, fieldKey);
            return AutoRiskCreationResponse.notCreated(fieldKey, appId, appRating, 
                                                      "Risk already exists for this evidence and field");
        }

        // Assign SME
        String assignedSme = assignSmeForRisk(appId, fieldKey);
        log.info("Assigned SME: {}", assignedSme);
        
        // Create risk
        log.info("Creating risk story for app={}, field={}, evidence={}", appId, fieldKey, evidenceId);
        RiskStory riskStory = new RiskStory();
        riskStory.setRiskId("risk_" + UUID.randomUUID());
        riskStory.setAppId(appId);
        riskStory.setFieldKey(fieldKey);
        riskStory.setTitle("Auto-created risk for " + fieldKey + " field");
        riskStory.setHypothesis("Evidence may indicate risk in " + fieldKey + " implementation");
        riskStory.setCondition("IF the attached evidence reveals security gaps");
        riskStory.setConsequence("THEN security posture may be compromised");
        riskStory.setSeverity("medium");
        riskStory.setStatus(RiskStatus.PENDING_SME_REVIEW);
        riskStory.setCreationType(RiskCreationType.SYSTEM_AUTO_CREATION);
        riskStory.setTriggeringEvidenceId(evidenceId);
        riskStory.setAssignedSme(assignedSme);
        riskStory.setRaisedBy("SYSTEM_AUTO_CREATION"); // Required field for auto-created risks
        riskStory.setOpenedAt(OffsetDateTime.now());
        riskStory.setAssignedAt(OffsetDateTime.now());
        riskStory.setPolicyRequirementSnapshot(complianceContextService.getComplianceSnapshot(fieldKey, appRating));

        RiskStory savedRisk = riskStoryRepository.save(riskStory);
        log.info("Risk story CREATED successfully: ID={}, Field={}, App={}, Evidence={}, SME={}", 
            savedRisk.getRiskId(), fieldKey, appId, evidenceId, assignedSme);
        log.info("=== AUTO-RISK EVALUATION END ===");
        
        return AutoRiskCreationResponse.created(savedRisk.getRiskId(), fieldKey, appId, 
                                               appRating, assignedSme, evidenceId, 
                                               "Auto-created based on field configuration");
    }

    @Override
    public String getFieldKeyFromProfileFieldId(String profileFieldId) {
        // Query the profile service to get the field key for this profile field ID
        try {
            return profileService.getFieldKeyByProfileFieldId(profileFieldId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not find field key for profile field ID: " + profileFieldId, e);
        }
    }

    @Override
    public String getAppRatingForField(String appId, String fieldKey) {
        try {
            // 1. Get field configuration to find what rating column to check
            Optional<ProfileFieldTypeInfo> fieldInfo = profileFieldRegistryService.getFieldTypeInfo(fieldKey);
            if (fieldInfo.isEmpty()) {
                throw new IllegalArgumentException("Unknown field key: " + fieldKey);
            }
            
            String derivedFrom = fieldInfo.get().derivedFrom();
            if (derivedFrom == null || derivedFrom.trim().isEmpty()) {
                throw new IllegalArgumentException("Field " + fieldKey + " has no derived_from configuration");
            }
            
            // 2. Query the application table for the rating column value
            String rating = applicationRepository.getApplicationRatingByColumn(appId, derivedFrom);
            if (rating == null || rating.trim().isEmpty()) {
                // Default rating if not set
                return "C1";
            }
            
            return rating;
            
        } catch (Exception e) {
            // Log the error and return default rating
            throw new IllegalArgumentException("Could not determine rating for app " + appId + 
                                             " and field " + fieldKey + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String assignSmeForRisk(String appId, String fieldKey) {
        // Simple SME assignment logic - this could be enhanced with:
        // - Load balancing
        // - Expertise matching
        // - Availability checking
        
        // For security-related fields, assign to security SME
        if (fieldKey.contains("security") || fieldKey.contains("encryption") || 
            fieldKey.contains("secrets") || fieldKey.contains("auth")) {
            return "security_sme_001";
        }
        
        // For infrastructure fields, assign to infra SME
        if (fieldKey.contains("infra") || fieldKey.contains("network") || 
            fieldKey.contains("monitoring")) {
            return "infra_sme_001";
        }
        
        // Default to general SME
        return "general_sme_001";
    }
}