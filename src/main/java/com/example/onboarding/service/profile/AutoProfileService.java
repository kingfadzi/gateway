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

@Service
public class AutoProfileService {

    private final ServiceNowRepository serviceNowRepository;
    private final ApplicationManagementRepository applicationManagementRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ProfileRepository profileRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final RegistryDeriver registryDeriver;
    private final AutoProfileProperties props;

    private static final Logger log = LoggerFactory.getLogger(AutoProfileService.class);

    public AutoProfileService(ServiceNowRepository serviceNowRepository,
                              ApplicationManagementRepository applicationManagementRepository,
                              ServiceInstanceRepository serviceInstanceRepository,
                              ProfileRepository profileRepository,
                              ProfileFieldRepository profileFieldRepository,
                              RegistryDeriver registryDeriver,
                              AutoProfileProperties props) {
        this.serviceNowRepository = serviceNowRepository;
        this.applicationManagementRepository = applicationManagementRepository;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.profileRepository = profileRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.registryDeriver = registryDeriver;
        this.props = props;
    }

    @Transactional
    public ProfileSnapshot autoSetup(String appId) {
        // 1) Load authoritative source snapshot
        SourceRow src = loadSource(appId);

        // 2) Upsert target application row (authoritative facts only)
        upsertApplication(src);

        // 3) Load and upsert service instances
        List<ServiceInstanceRow> instances = loadServiceInstances(appId);
        upsertServiceInstances(appId, instances);

        // 4) Build base context combining all signal sources for derivation input
        Map<String, Object> serviceNowContext = buildServiceNowAppRatingContext(appId, src);
        Map<String, Object> artifactContext = buildArtifactContext(appId);
        
        Map<String, Object> base = new LinkedHashMap<>();
        base.putAll(serviceNowContext);
        base.putAll(artifactContext);

        // 5) Derive risk profile controls
        Map<String, Object> derived = derive(base);

        // 6) Upsert profile header and write **derived only** into profile_field
        String profileId = upsertProfileHeader(appId);
        writeDerivedOnly(profileId, appId, derived);

        // Return snapshot with derived fields (the persisted content)
        return new ProfileSnapshot(appId, profileId, props.getScopeType(), props.getProfileVersion(), derived);
    }

    /* ======================= helpers / logic units ======================= */

    private SourceRow loadSource(String appId) {
        SourceRow src = serviceNowRepository.fetchApplicationData(appId)
                .orElseThrow(() -> new IllegalArgumentException("appId not found in ServiceNow: " + appId));
        if (log.isDebugEnabled()) log.debug("autoProfile: ServiceNow data for {} -> {}", appId, src);
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
        Map<String, Object> context = RatingsNormalizer.normalizeCtx(
                src.appCriticality(), src.securityRating(), src.integrityRating(),
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

    private String upsertProfileHeader(String appId) {
        String profileId = com.example.onboarding.util.HashIds.profileId(
                props.getScopeType(), appId, props.getProfileVersion()
        );
        profileRepository.upsertProfile(profileId, props.getScopeType(), appId, props.getProfileVersion());
        return profileId;
    }

    /** Persist **derived only** into profile_field. */
    private void writeDerivedOnly(String profileId, String appId, Map<String, Object> derived) {
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: writing {} derived fields for appId={}, profileId={}",
                    derived.size(), appId, profileId);
        }
        profileFieldRepository.upsertAll(profileId, "SERVICE_NOW", appId, derived);
    }
}
