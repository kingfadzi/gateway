package com.example.gateway.risk.service;

import com.example.gateway.risk.dto.AutoRiskCreationResponse;
import com.example.gateway.registry.dto.RegistryRuleEvaluation;
import com.example.gateway.registry.dto.RiskCreationRule;
import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.registry.service.RegistryRiskConfigService;
import com.example.gateway.track.service.TrackService;
import com.example.gateway.profile.service.ProfileService;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import com.example.gateway.application.repository.ApplicationManagementRepository;
import com.example.gateway.registry.service.ComplianceContextService;
import com.example.gateway.profile.dto.ProfileFieldTypeInfo;
import com.example.gateway.evidence.repository.EvidenceFieldLinkRepository;
import com.example.gateway.evidence.model.EvidenceFieldLink;
import com.example.gateway.evidence.model.EvidenceFieldLinkId;
import com.example.gateway.evidence.service.EvidenceStatusMapper;
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
    private final RiskItemRepository riskItemRepository;
    private final TrackService trackService;
    private final ProfileService profileService;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    private final ApplicationManagementRepository applicationRepository;
    private final ComplianceContextService complianceContextService;
    private final DomainRiskAggregationService aggregationService;
    private final RiskPriorityCalculator priorityCalculator;
    private final ArbRoutingService arbRoutingService;
    private final EvidenceFieldLinkRepository evidenceFieldLinkRepository;
    private final EvidenceStatusMapper evidenceStatusMapper;

    public RiskAutoCreationServiceImpl(RegistryRiskConfigService registryRiskConfigService,
                                      RiskItemRepository riskItemRepository,
                                      TrackService trackService,
                                      ProfileService profileService,
                                      ProfileFieldRegistryService profileFieldRegistryService,
                                      ApplicationManagementRepository applicationRepository,
                                      ComplianceContextService complianceContextService,
                                      DomainRiskAggregationService aggregationService,
                                      RiskPriorityCalculator priorityCalculator,
                                      ArbRoutingService arbRoutingService,
                                      EvidenceFieldLinkRepository evidenceFieldLinkRepository,
                                      EvidenceStatusMapper evidenceStatusMapper) {
        this.registryRiskConfigService = registryRiskConfigService;
        this.riskItemRepository = riskItemRepository;
        this.trackService = trackService;
        this.profileService = profileService;
        this.profileFieldRegistryService = profileFieldRegistryService;
        this.applicationRepository = applicationRepository;
        this.complianceContextService = complianceContextService;
        this.aggregationService = aggregationService;
        this.priorityCalculator = priorityCalculator;
        this.arbRoutingService = arbRoutingService;
        this.evidenceFieldLinkRepository = evidenceFieldLinkRepository;
        this.evidenceStatusMapper = evidenceStatusMapper;
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

        // Check if risk item already exists for this field and evidence (deduplication)
        boolean riskExists = riskItemRepository.existsByAppIdAndFieldKeyAndTriggeringEvidenceId(
                appId, fieldKey, evidenceId);

        log.info("Risk item already exists check: exists={}", riskExists);

        if (riskExists) {
            log.info("Risk creation SKIPPED: Risk item already exists for evidence {} and field {}", evidenceId, fieldKey);
            return AutoRiskCreationResponse.notCreated(fieldKey, appId, appRating,
                                                      "Risk item already exists for this evidence and field");
        }

        // Get field info for domain routing
        Optional<ProfileFieldTypeInfo> fieldInfo = profileFieldRegistryService.getFieldTypeInfo(fieldKey);
        if (fieldInfo.isEmpty()) {
            throw new IllegalStateException("Field type info not found for: " + fieldKey);
        }
        String derivedFrom = fieldInfo.get().derivedFrom();

        // Get or create domain risk
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(appId, derivedFrom);
        log.info("Using domain risk: {} for domain: {}", domainRisk.getDomainRiskId(), domainRisk.getRiskDimension());

        // Get priority from matched rule
        RiskCreationRule matchedRule = evaluation.matchedRule();
        if (matchedRule == null) {
            throw new IllegalStateException("Matched rule is null but shouldCreateRisk was true");
        }
        RiskPriority priority = matchedRule.priority();

        // Query actual evidence status from evidence field link
        String evidenceStatus = evidenceFieldLinkRepository
                .findById(new EvidenceFieldLinkId(evidenceId, profileFieldId))
                .map(link -> evidenceStatusMapper.mapLinkStatusToEvidenceStatus(link.getLinkStatus()))
                .orElse("missing");  // Truly missing if no link exists

        int priorityScore = priorityCalculator.calculatePriorityScore(priority, evidenceStatus);
        String severity = priorityCalculator.getSeverityLabel(priorityScore);

        log.info("Priority calculation: priority={}, evidenceStatus={}, score={}, severity={}",
                priority, evidenceStatus, priorityScore, severity);

        // Create risk item
        log.info("Creating risk item for app={}, field={}, evidence={}, domain={}", appId, fieldKey, evidenceId, domainRisk.getRiskDimension());
        RiskItem riskItem = new RiskItem();
        riskItem.setRiskItemId("item_" + UUID.randomUUID());
        riskItem.setAppId(appId);
        riskItem.setFieldKey(fieldKey);
        riskItem.setProfileFieldId(profileFieldId);
        riskItem.setTriggeringEvidenceId(evidenceId);

        // Content
        riskItem.setTitle("Compliance risk: " + fieldKey);
        riskItem.setDescription(String.format("Evidence for %s requires review due to %s rating configuration",
                fieldKey, appRating));

        // Priority & severity
        riskItem.setPriority(priority);
        riskItem.setSeverity(severity);
        riskItem.setPriorityScore(priorityScore);
        riskItem.setEvidenceStatus(evidenceStatus);

        // Status
        riskItem.setStatus(RiskItemStatus.OPEN);

        // Lifecycle
        riskItem.setCreationType(RiskCreationType.SYSTEM_AUTO_CREATION);
        riskItem.setRaisedBy("SYSTEM_AUTO_CREATION");
        riskItem.setOpenedAt(OffsetDateTime.now());

        // Snapshot
        riskItem.setPolicyRequirementSnapshot(complianceContextService.getComplianceSnapshot(fieldKey, appRating));

        // Add risk item to domain risk (this saves the risk item and recalculates aggregations)
        aggregationService.addRiskItemToDomain(domainRisk, riskItem);

        log.info("Risk item CREATED successfully: ID={}, Field={}, App={}, Evidence={}, Domain={}, Priority={}, Score={}",
            riskItem.getRiskItemId(), fieldKey, appId, evidenceId, domainRisk.getRiskDimension(), priority, priorityScore);
        log.info("Domain risk {} updated with new item, total items: {}, open items: {}",
            domainRisk.getDomainRiskId(), domainRisk.getTotalItems(), domainRisk.getOpenItems());
        log.info("=== AUTO-RISK EVALUATION END ===");

        return AutoRiskCreationResponse.created(riskItem.getRiskItemId(), fieldKey, appId,
                                               appRating, domainRisk.getAssignedArb(), evidenceId,
                                               "Auto-created risk item and added to domain risk " + domainRisk.getRiskDimension());
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
            
            
            // 3. Query the application table for the rating column value
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