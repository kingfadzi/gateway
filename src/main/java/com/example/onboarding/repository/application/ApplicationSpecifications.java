package com.example.onboarding.repository.application;

import com.example.onboarding.model.Application;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApplicationSpecifications {
    public static Specification<Application> withDynamicParams(Map<String, Object> params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            params.forEach((key, value) -> {
                if (value != null && !value.toString().isEmpty()) {
                    if ("search".equals(key)) {
                        // Search parameter: partial match on appId or name
                        String searchValue = "%" + value.toString().toLowerCase() + "%";
                        Predicate appIdMatch = cb.like(cb.lower(root.get("appId")), searchValue);
                        Predicate nameMatch = cb.like(cb.lower(root.get("name")), searchValue);
                        predicates.add(cb.or(appIdMatch, nameMatch));
                    } else {
                        // All other parameters: exact match
                        predicates.add(cb.equal(root.get(mapParamToField(key)), value));
                    }
                }
            });
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    private static String mapParamToField(String param) {
        return switch (param) {
            case "criticality" -> "appCriticalityAssessment";
            case "applicationType" -> "applicationType";
            case "architectureType" -> "architectureType";
            case "installType" -> "installType";
            default -> param;
        };
    }
}
