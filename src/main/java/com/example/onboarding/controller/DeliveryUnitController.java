package com.example.onboarding.controller;

import com.example.onboarding.model.DeliveryUnit;
import com.example.onboarding.model.DeliveryUnitRequest;
import com.example.onboarding.service.DeliveryUnitBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/delivery-units")
public class DeliveryUnitController {

    private final DeliveryUnitBuilder builder;

    public DeliveryUnitController(DeliveryUnitBuilder builder) {
        this.builder = builder;
    }

    @PostMapping("/build")
    public ResponseEntity<DeliveryUnit> build(@RequestBody DeliveryUnitRequest request) {
        DeliveryUnit unit = builder.build(request);
        return ResponseEntity.ok(unit);
    }
}