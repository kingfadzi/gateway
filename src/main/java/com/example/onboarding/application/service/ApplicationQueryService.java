package com.example.onboarding.application.service;

import com.example.onboarding.application.dto.AppSummaryResponse;
import com.example.onboarding.application.model.Application;
import com.example.onboarding.application.repository.ApplicationRepository;
import com.example.onboarding.application.repository.ApplicationSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApplicationQueryService {
    private final ApplicationRepository repository;

    public ApplicationQueryService(ApplicationRepository repository) {
        this.repository = repository;
    }

    public List<Application> search(Map<String, String> params) {
        Map<String, Object> filteredParams = params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Specification<Application> spec = ApplicationSpecifications.withDynamicParams(filteredParams);
        return repository.findAll(spec);
    }

    public Application getById(String appId) {
        return repository.findById(appId).orElse(null);
    }
    
    public List<AppSummaryResponse> convertToSummaryResponse(List<Application> applications) {
        return applications.stream()
                .map(this::toAppSummaryResponse)
                .collect(Collectors.toList());
    }
    
    private AppSummaryResponse toAppSummaryResponse(Application app) {
        return new AppSummaryResponse(
            app.getAppId(),
            app.getName(),
            app.getAppCriticalityAssessment(),
            app.getBusinessServiceName(),
            app.getApplicationType(),
            app.getInstallType(),
            app.getArchitectureType()
        );
    }
}
