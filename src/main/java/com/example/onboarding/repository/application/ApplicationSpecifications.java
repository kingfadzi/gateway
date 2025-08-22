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
                    predicates.add(cb.equal(root.get(key), value));
                }
            });
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
