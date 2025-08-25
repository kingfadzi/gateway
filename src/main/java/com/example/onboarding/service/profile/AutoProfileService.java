package com.example.onboarding.service.profile;

import com.example.onboarding.dto.profile.ProfileSnapshot;
import com.example.onboarding.dto.application.SourceRow;
import com.example.onboarding.dto.application.ServiceInstanceRow;
import com.example.onboarding.config.AutoProfileProperties;
import com.example.onboarding.repository.application.ApplicationManagementRepository;
import com.example.onboarding.repository.application.ServiceInstanceRepository;
import com.example.onboarding.repository.profile.ServiceNowRepository;
import com.example.onboarding.repository.profile.ProfileFieldRepository;
import com.example.onboarding.repository.profile.ProfileRepository;
import com.example.onboarding.service.application.RatingsNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AutoProfileService {

    private final ServiceNowRepository serviceNowRepository;
    private final ApplicationManagementRepository applicationManagementRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final RegistryDeriver registryDeriver;
    private final ProfileVersionService profileVersionService;
    private final AutoProfileProperties props;

    private static final Logger log = LoggerFactory.getLogger(AutoProfileService.class);

    public AutoProfileService(ServiceNowRepository serviceNowRepository,
                              ApplicationManagementRepository applicationManagementRepository,
                              ServiceInstanceRepository serviceInstanceRepository,
                              RegistryDeriver registryDeriver,
                              ProfileVersionService profileVersionService,
                              AutoProfileProperties props) {
        this.serviceNowRepository = serviceNowRepository;
        this.applicationManagementRepository = applicationManagementRepository;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.registryDeriver = registryDeriver;
        this.profileVersionService = profileVersionService;
        this.props = props;
    }

    @Transactional
    public ProfileSnapshot autoSetup(String appId) {
        // 1-3) Load source data and update application/service instances
        SourceRow src = loadSource(appId);
        upsertApplication(src);
        List<ServiceInstanceRow> instances = loadServiceInstances(appId);
        upsertServiceInstances(appId, instances);

        // 4) Build base context combining all signal sources for derivation input
        Map<String, Object> serviceNowContext = buildServiceNowAppRatingContext(appId, src);
        Map<String, Object> artifactContext = buildArtifactContext(appId);
        
        Map<String, Object> base = new LinkedHashMap<>();
        base.putAll(serviceNowContext);
        base.putAll(artifactContext);

        // 5) Derive risk profile controls
        Map<String, Object> newlyDerived = derive(base);

        // 6) Load current complete profile state (if exists)  
        Optional<com.example.onboarding.service.profile.ProfileVersionService.ProfileVersion> currentVersion = 
                profileVersionService.getCurrentVersion(appId);

        if (currentVersion.isEmpty()) {
            // No existing profile - create initial version
            return profileVersionService.createInitialProfile(appId, props.getScopeType(), newlyDerived);
        }

        // 7) Analyze changes with significance assessment
        com.example.onboarding.service.profile.ProfileChangeAnalysis analysis = 
                com.example.onboarding.service.profile.ProfileChangeAnalysis.analyze(
                        currentVersion.get().fields(), 
                        newlyDerived
                );

        if (analysis.requiresNewVersion()) {
            // Create new versioned profile with merged fields
            return profileVersionService.createNewProfileVersion(
                    appId, 
                    props.getScopeType(), 
                    currentVersion.get(), 
                    analysis, 
                    newlyDerived
            );
        }

        // No significant changes - return current version snapshot
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: no changes detected for appId={}, returning existing version {}", 
                    appId, currentVersion.get().version());
        }
        return currentVersion.get().toSnapshot(props.getScopeType());
    }

    /* ======================= helpers / logic units ======================= */

    private SourceRow loadSource(String appId) {
        SourceRow src = serviceNowRepository.fetchApplicationData(appId)
                .orElseThrow(() -> new IllegalArgumentException("appId not found in ServiceNow: " + appId));
        
        // Debug raw ServiceNow data to investigate data quality issues
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: ServiceNow raw data for appId={}", appId);
            log.debug("  appCriticality: '{}' (type: {})", src.appCriticality(), 
                    src.appCriticality() != null ? src.appCriticality().getClass().getSimpleName() : "null");
            log.debug("  securityRating: '{}' (type: {})", src.securityRating(), 
                    src.securityRating() != null ? src.securityRating().getClass().getSimpleName() : "null");
            log.debug("  integrityRating: '{}' (type: {})", src.integrityRating(), 
                    src.integrityRating() != null ? src.integrityRating().getClass().getSimpleName() : "null");
            log.debug("  availabilityRating: '{}' (type: {})", src.availabilityRating(), 
                    src.availabilityRating() != null ? src.availabilityRating().getClass().getSimpleName() : "null");
            log.debug("  resilienceRating: '{}' (type: {})", src.resilienceRating(), 
                    src.resilienceRating() != null ? src.resilienceRating().getClass().getSimpleName() : "null");
        }
        
        return src;
    }

    private void upsertApplication(SourceRow src) {
        applicationManagementRepository.upsertFromSource(src);
        if (log.isDebugEnabled()) log.debug("autoProfile: upserted application for {}", src.appId());
    }

    private List<ServiceInstanceRow> loadServiceInstances(String appId) {
        List<ServiceInstanceRow> rows = serviceNowRepository.fetchServiceInstances(appId);
        if (log.isDebugEnabled()) log.debug("autoProfile: fetched {} ServiceNow service instances for {}", rows.size(), appId);
        return rows;
    }

    private void upsertServiceInstances(String appId, List<ServiceInstanceRow> rows) {
        serviceInstanceRepository.upsertAll(appId, rows);
        if (log.isDebugEnabled()) log.debug("autoProfile: upserted {} service instances for {}", rows.size(), appId);
    }

    /** Build ServiceNow rating signals for policy derivation */
    private Map<String, Object> buildServiceNowAppRatingContext(String appId, SourceRow src) {
        // Only provide rating signals for policy derivation
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: About to normalize ratings for appId={}", appId);
            log.debug("  Input: appCrit='{}', secRating='{}', integRating='{}', availRating='{}', resilRating='{}'", 
                    src.appCriticality(), src.securityRating(), src.integrityRating(), 
                    src.availabilityRating(), src.resilienceRating());
        }
        
        Map<String, Object> context = RatingsNormalizer.normalizeCtx(
                appId, src.appCriticality(), src.securityRating(), src.integrityRating(),
                src.availabilityRating(), src.resilienceRating()
        );

        if (log.isDebugEnabled()) log.debug("autoProfile: ServiceNow context for {} -> {}", appId, context);
        return context;
    }
    
    /** Build artifact signals for policy derivation */
    private Map<String, Object> buildArtifactContext(String appId) {
        Map<String, Object> context = new LinkedHashMap<>();
        
        // For now, all artifacts are required (static policy)
        context.put("artifact", "required");
        
        if (log.isDebugEnabled()) log.debug("autoProfile: artifact context for {} -> {}", appId, context);
        return context;
    }

    private Map<String, Object> derive(Map<String, Object> base) {
        Map<String,Object> derived = registryDeriver.derive(base);
        if (log.isDebugEnabled()) log.debug("autoProfile: derived -> {}", derived);
        return derived;
    }

}
