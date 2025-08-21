package com.example.onboarding.service.profile;

import com.example.onboarding.dto.application.ProfileSnapshot;
import com.example.onboarding.dto.application.SourceRow;
import com.example.onboarding.config.AutoProfileProperties;
import com.example.onboarding.repository.application.ApplicationRepository;
import com.example.onboarding.repository.application.ProfileFieldRepository;
import com.example.onboarding.repository.application.ProfileRepository;
import com.example.onboarding.repository.application.SourceDao;
import com.example.onboarding.service.application.RatingsNormalizer;
import com.example.onboarding.service.application.RegistryDeriver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Service
public class AutoProfileService {

    private final SourceDao sourceDao;
    private final ApplicationRepository applicationRepository;
    private final ProfileRepository profileRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final RegistryDeriver registryDeriver;
    private final AutoProfileProperties props;

    private static final Logger log = LoggerFactory.getLogger(AutoProfileService.class);

    public AutoProfileService(SourceDao sourceDao,
                              ApplicationRepository applicationRepository,
                              ProfileRepository profileRepository,
                              ProfileFieldRepository profileFieldRepository,
                              RegistryDeriver registryDeriver,
                              AutoProfileProperties props) {
        this.sourceDao = sourceDao;
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.registryDeriver = registryDeriver;
        this.props = props;
    }

    @Transactional
    public ProfileSnapshot autoSetup(String appId) {
        SourceRow src = sourceDao.fetchByAppId(appId)
                .orElseThrow(() -> new IllegalArgumentException("appId not found in source: " + appId));
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: source for {} -> {}", appId, src);
        }

        // 1) Upsert the application row (idempotent) so later joins/pre-checks succeed
        applicationRepository.upsertFromSource(
                appId,
                src.businessServiceName(),
                src.appCriticality(),
                src.applicationType(),
                src.applicationTier(),
                src.architectureType(),
                src.installType(),
                src.housePosition(),
                src.operationalStatus(),
                src.transactionCycle()
        );
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: upserted application for {}", appId);
        }

        // 2) normalize
        Map<String,Object> ctx = RatingsNormalizer.normalizeCtx(
                src.appCriticality(), src.securityRating(), src.integrityRating(),
                src.availabilityRating(), src.resilienceRating()
        );
        if (src.businessServiceName() != null) {
            var m = new java.util.LinkedHashMap<>(ctx);
            m.put("business_service_name", src.businessServiceName());
            ctx = m;
        }
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: normalized ctx for {} -> {}", appId, ctx);
        }

        // 3) derive
        Map<String,Object> derived = registryDeriver.derive(ctx);
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: derived for {} -> {}", appId, derived);
        }

        // 4) profile header
        String profileId = com.example.onboarding.util.HashIds.profileId(
                props.getScopeType(), appId, props.getProfileVersion()
        );
        profileRepository.upsertProfile(profileId, props.getScopeType(), appId, props.getProfileVersion());

        // 5) write fields
        Map<String,Object> merged = new java.util.LinkedHashMap<>(ctx);
        merged.putAll(derived);
        if (log.isDebugEnabled()) {
            log.debug("autoProfile: writing {} fields for appId={}, profileId={}", merged.size(), appId, profileId);
        }
        profileFieldRepository.upsertAll(profileId, "SERVICE_NOW", appId, merged);

        return new ProfileSnapshot(appId, profileId, props.getScopeType(), props.getProfileVersion(), merged);
    }
}
