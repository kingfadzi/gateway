package com.example.onboarding.service.application;

import com.example.onboarding.model.Application;
import com.example.onboarding.repository.application.ApplicationRepository;
import com.example.onboarding.repository.application.ApplicationSpecifications;
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
}
