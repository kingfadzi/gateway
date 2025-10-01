package com.example.onboarding.application.service;

import com.example.onboarding.application.model.Application;
import com.example.onboarding.application.repository.ApplicationRepository;
import com.example.onboarding.application.repository.ApplicationSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;

@Service
public class ApplicationService {
    private final ApplicationRepository repository;

    public ApplicationService(ApplicationRepository repository) {
        this.repository = repository;
    }

    public List<Application> search(Map<String, String> params) {
        // Convert to <String, Object> and filter empty values
        Map<String, Object> filteredParams = params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue
                ));
        Specification<Application> spec = ApplicationSpecifications.withDynamicParams(filteredParams);
        return repository.findAll(spec);
    }
}
