package com.example.gateway.deliveryunit.controller;

import com.example.gateway.deliveryunit.dto.DeliveryUnit;
import com.example.gateway.deliveryunit.dto.DeliveryUnitRequest;
import com.example.gateway.deliveryunit.service.DeliveryUnitBuilder;
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