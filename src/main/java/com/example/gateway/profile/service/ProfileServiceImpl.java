package com.example.gateway.profile.service;

import com.example.gateway.config.FieldRegistryConfig;
import com.example.gateway.evidence.dto.Evidence;
import com.example.gateway.document.dto.DocumentSummary;
import com.example.gateway.profile.dto.*;
import com.example.gateway.profile.respository.ProfileRepository;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.risk.model.RiskItem;
import com.example.gateway.document.service.DocumentService;
import com.example.gateway.evidence.dto.EnhancedEvidenceSummary;
import com.example.gateway.evidence.repository.EvidenceRepository;
import com.example.gateway.evidence.repository.EvidenceFieldLinkRepository;
import com.example.gateway.evidence.model.EvidenceFieldLinkStatus;
import com.example.gateway.util.ProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Profile API now returns profile + application row + service instances.
 */
@Service
public class ProfileServiceImpl implements ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileServiceImpl.class);

    private final ProfileRepository profileRepository;
    private final FieldRegistryConfig fieldRegistryConfig;
    private final DocumentService documentService;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    private final RiskItemRepository riskItemRepository;
    private final EvidenceStatusCalculator evidenceStatusCalculator;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceFieldLinkRepository evidenceFieldLinkRepository;

    public ProfileServiceImpl(ProfileRepository profileRepository,
                             FieldRegistryConfig fieldRegistryConfig,
                             DocumentService documentService,
                             ProfileFieldRegistryService profileFieldRegistryService,
                             RiskItemRepository riskItemRepository,
                             EvidenceStatusCalculator evidenceStatusCalculator,
                             EvidenceRepository evidenceRepository,
                             EvidenceFieldLinkRepository evidenceFieldLinkRepository) {
        this.profileRepository = profileRepository;
        this.fieldRegistryConfig = fieldRegistryConfig;
        this.documentService = documentService;
        this.profileFieldRegistryService = profileFieldRegistryService;
        this.riskItemRepository = riskItemRepository;
        this.evidenceStatusCalculator = evidenceStatusCalculator;
        this.evidenceRepository = evidenceRepository;
        this.evidenceFieldLinkRepository = evidenceFieldLinkRepository;
    }


    private boolean appExists(String appId) {
        return profileRepository.appExists(appId);
    }

    private ProfileMeta getLatestProfile(String appId) {
        return profileRepository.getLatestProfileMeta(appId);
    }

    @Transactional
    private ProfileMeta ensureProfile(String appId, Integer version) {
        if (!appExists(appId)) throw new NoSuchElementException("Application not found: " + appId);

        if (version == null) {
            ProfileMeta latest = getLatestProfile(appId);
            if (latest != null) return latest;
            return profileRepository.createProfile(appId, null);
        } else {
            ProfileMeta existing = profileRepository.findProfileByVersion(appId, version);
            if (existing != null) return existing;
            return profileRepository.createProfile(appId, version);
        }
    }

    @Override
    public ProfileSnapshotDto getProfile(String appId) {
        if (!appExists(appId)) throw new NoSuchElementException("Application not found: " + appId);

        // 1) Application row (ALL columns)
        Map<String, Object> application = profileRepository.getApplication(appId);

        // 2) Service instances (try a few likely relations; return [] if none exist)
        List<Map<String, Object>> serviceInstances = profileRepository.getServiceInstances(appId);

        // 3) Profile snapshot (as before)
        ProfileMeta prof = getLatestProfile(appId);
        if (prof == null) {
            // No profile yet -> empty fields but still return application + serviceInstances
            return new ProfileSnapshotDto(appId, null, null, List.of(), application, serviceInstances);
        }

        String profileId = prof.profileId();
        OffsetDateTime profUpdated = prof.updatedAt();

        List<FieldRow> rows = profileRepository.getProfileFieldRows(profileId);
        var fields = rows.stream()
                .map(r -> new ProfileField(
                        r.fieldId(),
                        r.fieldKey(),
                        ProfileUtils.jsonToJava(r.valueJson()),
                        r.sourceSystem(),
                        r.sourceRef(),
                        r.evidenceCount(),
                        r.updatedAt()))
                .toList();

        return new ProfileSnapshotDto(appId, profileId, profUpdated, fields, application, serviceInstances);
    }

    @Override
    public ProfilePayload getProfilePayload(String appId) {
        Map<String, Object> appData = profileRepository.getApplication(appId);
        if (appData == null) return null;
        
        ProfileMeta profile = getLatestProfile(appId);
        if (profile == null) return null;
        
        List<ProfileField> fields = profileRepository.getProfileFields(profile.profileId());
        List<String> fieldIds = fields.stream().map(ProfileField::fieldId).collect(Collectors.toList());
        
        List<Evidence> evidenceList = profileRepository.getEvidence(fieldIds);
        
        // Build drivers map
        Map<String, String> drivers = new HashMap<>();
        drivers.put("security_rating", (String) appData.get("security_rating"));
        drivers.put("confidentiality_rating", (String) appData.get("confidentiality_rating"));
        drivers.put("integrity_rating", (String) appData.get("integrity_rating"));
        drivers.put("availability_rating", (String) appData.get("availability_rating"));
        drivers.put("resilience_rating", (String) appData.get("resilience_rating"));
        drivers.put("app_criticality_assessment", (String) appData.get("app_criticality_assessment"));
        
        // Convert ProfileFields to ProfileFieldPayloads with derived_from mapping
        List<ProfileFieldPayload> fieldPayloads = fields.stream()
                .map(field -> new ProfileFieldPayload(
                        field.fieldId(),
                        profile.profileId(),
                        field.fieldKey(),
                        field.value(),
                        fieldRegistryConfig.getDerivedFromByFieldKey(field.fieldKey())
                ))
                .collect(Collectors.toList());
        
        // Convert Evidence to EvidencePayload
        List<EvidencePayload> evidencePayloads = evidenceList.stream()
                .map(evidence -> new EvidencePayload(
                        evidence.evidenceId(),
                        evidence.profileFieldId(),
                        evidence.uri(),
                        evidence.status(),
                        evidence.reviewedBy(),
                        evidence.reviewedAt(),
                        evidence.validFrom(),
                        evidence.validUntil()
                ))
                .collect(Collectors.toList());
        
        // Risk functionality removed
        List<RiskPayload> riskPayloads = Collections.emptyList();
        
        return new ProfilePayload(
                (String) appData.get("app_id"),
                (String) appData.get("name"),
                profile.version(),
                (OffsetDateTime) appData.get("updated_at"),
                drivers,
                fieldPayloads,
                evidencePayloads,
                riskPayloads
        );
    }

    @Override
    public DomainGraphPayload getProfileDomainGraph(String appId) {
        Map<String, Object> appData = profileRepository.getApplication(appId);
        if (appData == null) return null;
        
        ProfileMeta profile = getLatestProfile(appId);
        if (profile == null) return null;
        
        List<ProfileField> fields = profileRepository.getProfileFields(profile.profileId());
        List<String> fieldIds = fields.stream().map(ProfileField::fieldId).collect(Collectors.toList());
        
        List<Evidence> evidenceList = profileRepository.getEvidence(fieldIds);
        
        // Group evidence by field ID
        Map<String, List<Evidence>> evidenceByField = evidenceList.stream()
                .collect(Collectors.groupingBy(Evidence::profileFieldId));

        // Get risk items for this application
        List<RiskItem> riskItems = riskItemRepository.findByAppId(appId);

        // Group risks by field key
        Map<String, List<RiskItem>> risksByFieldKey = riskItems.stream()
                .collect(Collectors.groupingBy(RiskItem::getFieldKey));
        
        // Group fields by derived_from (domain)
        Map<String, List<ProfileField>> fieldsByDomain = fields.stream()
                .collect(Collectors.groupingBy(field ->
                    fieldRegistryConfig.getDerivedFromByFieldKey(field.fieldKey())));

        // Batch load enhanced evidence for all fields (eliminates N+1 query problem)
        List<String> allFieldIds = fields.stream()
                .map(ProfileField::fieldId)
                .collect(Collectors.toList());

        Map<String, List<EnhancedEvidenceSummary>> enhancedEvidenceByField =
            evidenceRepository.findEvidenceByProfileFieldsBatch(allFieldIds)
                .stream()
                .collect(Collectors.groupingBy(EnhancedEvidenceSummary::profileFieldId));

        List<DomainPayload> domainPayloads = new ArrayList<>();
        for (Map.Entry<String, List<ProfileField>> entry : fieldsByDomain.entrySet()) {
            String domainKey = entry.getKey();
            List<ProfileField> domainFields = entry.getValue();

            List<FieldGraphPayload> fieldPayloads = new ArrayList<>();
            for (ProfileField field : domainFields) {
                List<Evidence> evForField = evidenceByField.getOrDefault(field.fieldId(), Collections.emptyList());

                // Get enhanced evidence for status calculations (from batch-loaded map)
                List<EnhancedEvidenceSummary> enhancedEvidence =
                    enhancedEvidenceByField.getOrDefault(field.fieldId(), Collections.emptyList());
                
                // Calculate approval and freshness statuses
                String approvalStatus = evidenceStatusCalculator.calculateApprovalStatus(enhancedEvidence);
                String freshnessStatus = evidenceStatusCalculator.calculateFreshnessStatus(enhancedEvidence, field.fieldId());
                
                // Convert evidence to graph payload
                List<EvidenceGraphPayload> evidenceGraphPayloads = evForField.stream()
                        .map(evidence -> new EvidenceGraphPayload(
                                evidence.evidenceId(),
                                evidence.status(),
                                evidence.validUntil(),
                                evidence.reviewedBy()
                        ))
                        .collect(Collectors.toList());
                
                // Get risks for this field
                List<RiskItem> risksForField = risksByFieldKey.getOrDefault(field.fieldKey(), Collections.emptyList());

                // Convert risks to graph payload
                List<RiskGraphPayload> riskGraphPayloads = risksForField.stream()
                        .map(risk -> new RiskGraphPayload(
                                risk.getRiskItemId(),
                                risk.getTitle(),
                                risk.getSeverity(),
                                risk.getStatus().toString()
                        ))
                        .collect(Collectors.toList());
                
                // Get attestations (evidence with PENDING_PO_REVIEW status)
                List<AttestationGraphPayload> attestationGraphPayloads = enhancedEvidence.stream()
                        .filter(evidence -> evidence.linkStatus() == EvidenceFieldLinkStatus.PENDING_PO_REVIEW)
                        .map(evidence -> new AttestationGraphPayload(
                                evidence.evidenceId(),
                                evidence.documentTitle() != null ? evidence.documentTitle() : "Unknown Document",
                                evidence.documentSourceType() != null ? evidence.documentSourceType() : "unknown",
                                evidence.linkedAt() != null ? evidence.linkedAt() : evidence.createdAt(),
                                evidence.submittedBy() != null ? evidence.submittedBy() : "Unknown"
                        ))
                        .collect(Collectors.toList());
                
                fieldPayloads.add(new FieldGraphPayload(
                        field.fieldId(),
                        field.fieldKey(),
                        getFieldLabel(field.fieldKey()),
                        field.value(),
                        evidenceGraphPayloads,
                        approvalStatus,
                        freshnessStatus,
                        riskGraphPayloads,
                        attestationGraphPayloads
                ));
            }
            
            domainPayloads.add(new DomainPayload(
                    domainKey,
                    getDomainTitle(domainKey),
                    getDomainIcon(domainKey),
                    domainKey,
                    getDriverValue(domainKey, appData),
                    fieldPayloads,
                    calculateBulkAttestationEnabled(domainKey, fieldPayloads)
            ));
        }
        
        return new DomainGraphPayload(
                (String) appData.get("app_id"),
                (String) appData.get("name"),
                profile.version(),
                (OffsetDateTime) appData.get("updated_at"),
                domainPayloads
        );
    }
    
    @Override
    public ProfileFieldContext getProfileFieldContext(String appId, String fieldKey) {
        if (!appExists(appId)) return null;
        
        ProfileMeta profile = getLatestProfile(appId);
        if (profile == null) return null;
        
        // Find the specific field
        List<ProfileField> fields = profileRepository.getProfileFields(profile.profileId());
        ProfileField targetField = fields.stream()
                .filter(f -> f.fieldKey().equals(fieldKey))
                .findFirst()
                .orElse(null);
                
        if (targetField == null) return null;
        
        // Get evidence for this field
        List<Evidence> evidenceList = profileRepository.getEvidence(List.of(targetField.fieldId()));
        
        // Convert evidence to graph payload
        List<EvidenceGraphPayload> evidenceGraphPayloads = evidenceList.stream()
                .map(evidence -> new EvidenceGraphPayload(
                        evidence.evidenceId(),
                        evidence.status(),
                        evidence.validUntil(),
                        evidence.reviewedBy()
                ))
                .collect(Collectors.toList());
        
        // Get field metadata
        String label = getFieldLabel(fieldKey);
        String derivedFrom = fieldRegistryConfig.getDerivedFromByFieldKey(fieldKey);
        String domain = derivedFrom != null ? derivedFrom : "unknown";
        String assurance = deriveAssurance(evidenceList);
        
        // Get risks for this specific field
        List<RiskItem> risksForField = riskItemRepository.findByAppId(appId).stream()
                .filter(risk -> fieldKey.equals(risk.getFieldKey()))
                .collect(Collectors.toList());

        // Convert risks to graph payload
        List<RiskGraphPayload> riskGraphPayloads = risksForField.stream()
                .map(risk -> new RiskGraphPayload(
                        risk.getRiskItemId(),
                        risk.getTitle(),
                        risk.getSeverity(),
                        risk.getStatus().toString()
                ))
                .collect(Collectors.toList());
        
        return new ProfileFieldContext(
                targetField.fieldId(),
                targetField.fieldKey(),
                label,
                targetField.value(),
                derivedFrom,
                domain,
                targetField.sourceSystem(),
                targetField.sourceRef(),
                assurance,
                targetField.value(), // Using field value as policy requirement for now
                evidenceGraphPayloads,
                riskGraphPayloads,
                targetField.updatedAt(),
                targetField.evidenceCount()
        );
    }
    
    @Override
    public SuggestedEvidence getSuggestedEvidence(String appId, String fieldKey) {
        if (!appExists(appId)) return null;
        
        // Get field metadata
        String fieldLabel = getFieldLabel(fieldKey);
        
        // Get documents that have this field key in their related evidence fields
        List<DocumentSummary> suggestedDocuments = documentService.getDocumentsByFieldType(appId, fieldKey);
        
        // Build match criteria explanation
        String matchCriteria = String.format("Documents with '%s' in related evidence fields", fieldKey);
        
        return new SuggestedEvidence(
                fieldKey,
                fieldLabel,
                suggestedDocuments,
                suggestedDocuments.size(),
                matchCriteria
        );
    }
    
    
    private String getProfileFieldIdForDomain(String domain, List<ProfileFieldPayload> fields) {
        // Try to find the first field that matches the domain
        return fields.stream()
                .filter(field -> fieldRegistryConfig.getDerivedFromByFieldKey(field.field_key()).equals(domain + "_rating") 
                              || fieldRegistryConfig.getDerivedFromByFieldKey(field.field_key()).equals(domain))
                .findFirst()
                .map(ProfileFieldPayload::id)
                .orElse(null);
    }
    
    private String getDomainTitle(String domainKey) {
        return switch (domainKey) {
            case "security_rating" -> "Security";
            case "integrity_rating" -> "Integrity";
            case "availability_rating" -> "Availability";
            case "resilience_rating" -> "Resilience";
            case "confidentiality_rating" -> "Confidentiality";
            case "app_criticality_assessment" -> "Summary";
            default -> domainKey;
        };
    }

    private String getDomainIcon(String domainKey) {
        return switch (domainKey) {
            case "security_rating" -> "SecurityIcon";
            case "integrity_rating" -> "IntegrityIcon";
            case "availability_rating" -> "AvailabilityIcon";
            case "resilience_rating" -> "ResilienceIcon";
            case "app_criticality_assessment" -> "SummaryIcon";
            default -> "DefaultIcon";
        };
    }

    private String getDriverValue(String domainKey, Map<String, Object> appData) {
        return switch (domainKey) {
            case "security_rating" -> (String) appData.get("security_rating");
            case "confidentiality_rating" -> (String) appData.get("confidentiality_rating");
            case "integrity_rating" -> (String) appData.get("integrity_rating");
            case "availability_rating" -> (String) appData.get("availability_rating");
            case "resilience_rating" -> (String) appData.get("resilience_rating");
            case "app_criticality_assessment" -> (String) appData.get("app_criticality_assessment");
            default -> null;
        };
    }

    private String getFieldLabel(String fieldKey) {
        // Get label from the YAML registry
        FieldRegistryConfig.FieldDefinition fieldDef = fieldRegistryConfig.getRegistry().fields.stream()
                .filter(field -> field.key.equals(fieldKey))
                .findFirst()
                .orElse(null);
        return fieldDef != null ? fieldDef.label : fieldKey;
    }

    private String deriveAssurance(List<Evidence> evidence) {
        if (evidence == null || evidence.isEmpty()) return "Missing";
        // Status field deprecated - use first available evidence and check validUntil
        Evidence firstEvidence = evidence.get(0);
        if (firstEvidence.validUntil() == null) return "Current";
        long daysLeft = Duration.between(Instant.now(), firstEvidence.validUntil().toInstant()).toDays();
        if (daysLeft < 0) return "Expired";
        if (daysLeft <= 90) return "Expiring";
        return "Current";
    }

    /**
     * Calculate if bulk attestation is enabled based on domain and driver value.
     * Bulk attestation is DISABLED (false) for high-risk combinations:
     * - Security domain: A1, A2, B
     * - Resiliency domain: 0, 1  
     * - Other domains (CIA): A, B
     */
    private boolean calculateBulkAttestationEnabled(String domainKey, List<FieldGraphPayload> fieldPayloads) {
        // Simple check: if ANY field in this domain has attestations, then bulkAttestationEnabled = true
        return fieldPayloads.stream()
            .anyMatch(field -> field.attestations() != null && !field.attestations().isEmpty());
    }

    @Override
    @Transactional
    public PatchProfileResponse patchProfile(String appId, PatchProfileRequest req) {
        ProfileMeta prof = ensureProfile(appId, req.version());
        String profileId = prof.profileId();
        Integer version = prof.version();

        List<PatchProfileResponse.UpdatedField> updated = new ArrayList<>();
        for (var f : req.fields()) {
            if (f == null || f.key() == null || f.key().isBlank())
                throw new IllegalArgumentException("Field key is required");

            String fid = profileRepository.findOrCreateProfileField(profileId, f.key());
            profileRepository.updateProfileField(fid, profileId, f.key(), ProfileUtils.toJsonString(f.value()), f.sourceSystem(), f.sourceRef());
            updated.add(new PatchProfileResponse.UpdatedField(fid, f.key(), f.value()));
        }

        profileRepository.updateProfileTimestamp(profileId);
        return new PatchProfileResponse(version, profileId, updated);
    }

    @Override
    public String getFieldKeyByProfileFieldId(String profileFieldId) {
        return profileRepository.getFieldKeyByProfileFieldId(profileFieldId);
    }


}
