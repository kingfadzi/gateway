package com.example.gateway.sme.service;

import com.example.gateway.sme.dto.SmeRiskQueueResponse;
import com.example.gateway.risk.model.RiskStatus;
import com.example.gateway.risk.model.RiskStory;
import com.example.gateway.risk.repository.RiskStoryRepository;
import com.example.gateway.application.repository.ApplicationManagementRepository;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SmeRiskServiceImpl implements SmeRiskService {
    
    private final RiskStoryRepository riskStoryRepository;
    private final ApplicationManagementRepository applicationRepository;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    
    public SmeRiskServiceImpl(RiskStoryRepository riskStoryRepository,
                             ApplicationManagementRepository applicationRepository,
                             ProfileFieldRegistryService profileFieldRegistryService) {
        this.riskStoryRepository = riskStoryRepository;
        this.applicationRepository = applicationRepository;
        this.profileFieldRegistryService = profileFieldRegistryService;
    }
    
    @Override
    public List<SmeRiskQueueResponse> getMyReviewQueue(String smeId) {
        // Get all risks pending SME review for this user
        List<RiskStory> risks = riskStoryRepository.findByAssignedSmeAndStatus(smeId, RiskStatus.PENDING_SME_REVIEW);
        
        // Get application names for all risks
        Map<String, String> appNames = getApplicationNames(risks);
        
        return risks.stream()
                .map(risk -> {
                    String appName = appNames.get(risk.getAppId());
                    String domain = getDomainForFieldKey(risk.getFieldKey());
                    OffsetDateTime dueDate = SmeRiskQueueResponse.calculateDueDate(risk);
                    
                    return SmeRiskQueueResponse.fromRiskStory(risk, appName, domain, dueDate);
                })
                .collect(Collectors.toList());
    }
    
    
    /**
     * Get application names for a list of risks
     */
    private Map<String, String> getApplicationNames(List<RiskStory> risks) {
        List<String> appIds = risks.stream()
                .map(RiskStory::getAppId)
                .distinct()
                .collect(Collectors.toList());
        
        return applicationRepository.getApplicationNamesByIds(appIds)
                .stream()
                .collect(Collectors.toMap(
                    app -> (String) app.get("app_id"),
                    app -> (String) app.get("app_name")
                ));
    }
    
    /**
     * Get domain name for a field key using the registry service
     */
    private String getDomainForFieldKey(String fieldKey) {
        return profileFieldRegistryService.getFieldTypeInfo(fieldKey)
                .map(fieldInfo -> fieldInfo.domain())
                .orElse("unknown");
    }
}