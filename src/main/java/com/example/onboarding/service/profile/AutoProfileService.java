package com.example.onboarding.service.profile;

import com.example.onboarding.dto.profile.ProfileSnapshot;
import com.example.onboarding.dto.application.SourceRow;
import com.example.onboarding.dto.application.ServiceInstanceRow;
import com.example.onboarding.config.AutoProfileProperties;
import com.example.onboarding.repository.application.ApplicationManagementRepository;
import com.example.onboarding.repository.application.ServiceInstanceRepository;
import com.example.onboarding.repository.application.SourceDao;
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

    private final SourceDao sourceDao;
    private final ApplicationManagementRepository applicationManagementRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ProfileRepository profileRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final RegistryDeriver registryDeriver;
    private final AutoProfileProperties props;

    private static final Logger log = LoggerFactory.getLogger(AutoProfileService.class);

    public AutoProfileService(SourceDao sourceDao,
                              ApplicationManagementRepository applicationManagementRepository,
                              ServiceInstanceRepository serviceInstanceRepository,
                              ProfileRepository profileRepository,
                              ProfileFieldRepository profileFieldRepository,
                              RegistryDeriver registryDeriver,
                              AutoProfileProperties props) {
        this.sourceDao = sourceDao;
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

        // 4) Build base context (ratings + overview + instances) ONLY for derivation input
        Map<String, Object> base = buildBaseContext(appId, src, instances);

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
        SourceRow src = sourceDao.fetchByAppId(appId)
                .orElseThrow(() -> new IllegalArgumentException("appId not found in source: " + appId));
        if (log.isDebugEnabled()) log.debug("autoProfile: source for {} -> {}", appId, src);
        return src;
    }

    private void upsertApplication(SourceRow src) {
        applicationManagementRepository.upsertFromSource(src);
        if (log.isDebugEnabled()) log.debug("autoProfile: upserted application for {}", src.appId());
    }

    private List<ServiceInstanceRow> loadServiceInstances(String appId) {
        List<ServiceInstanceRow> rows = sourceDao.fetchServiceInstances(appId);
        if (log.isDebugEnabled()) log.debug("autoProfile: fetched {} service instances for {}", rows.size(), appId);
        return rows;
    }

    private void upsertServiceInstances(String appId, List<ServiceInstanceRow> rows) {
        serviceInstanceRepository.upsertAll(appId, rows);
        if (log.isDebugEnabled()) log.debug("autoProfile: upserted {} service instances for {}", rows.size(), appId);
    }

    /** Build input context for derivation; not persisted to profile. */
    private Map<String, Object> buildBaseContext(String appId, SourceRow src, List<ServiceInstanceRow> instances) {
        // ratings normalization
        Map<String,Object> ctx = RatingsNormalizer.normalizeCtx(
                src.appCriticality(), src.securityRating(), src.integrityRating(),
                src.availabilityRating(), src.resilienceRating()
        );

        // overview / stakeholders / attributes (used by rules; not persisted in profile_field)
        Map<String,Object> auto = new LinkedHashMap<>();
        auto.put("app_id", appId);
        auto.put("business_application_name", src.businessServiceName());
        auto.put("business_service_name", src.businessServiceName());

        auto.put("transaction_cycle", src.transactionCycle());
        auto.put("transaction_cycle_number", src.transactionCycleId());
        auto.put("application_type", src.applicationType());
        auto.put("architecture_type", src.architectureType());
        auto.put("install_type", src.installType());
        auto.put("application_parent", src.applicationParent());
        auto.put("application_parent_id", src.applicationParentId());
        auto.put("application_tier", src.applicationTier());

        auto.put("application_product_owner", src.applicationProductOwner());
        auto.put("application_product_owner_brid", src.applicationProductOwnerBrid());
        auto.put("system_architect", src.systemArchitect());
        auto.put("system_architect_brid", src.systemArchitectBrid());

        auto.put("operational_status", src.operationalStatus());
        auto.put("house_position", src.housePosition());
        auto.put("architecture_hosting", src.architectureHosting());
        auto.put("business_application_sys_id", src.businessApplicationSysId());

        // service instance preview for derivation signals
        auto.put("service_instance_count", instances.size());
        auto.put("service_instances",
                instances.stream().map(ServiceInstanceRow::serviceInstance).distinct().toList());
        auto.put("service_instance_envs",
                instances.stream().map(ServiceInstanceRow::environment).distinct().toList());

        Map<String,Object> base = new LinkedHashMap<>(ctx);
        base.putAll(auto);
        if (log.isDebugEnabled()) log.debug("autoProfile: base ctx for {} -> {}", appId, base);
        return base;
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
