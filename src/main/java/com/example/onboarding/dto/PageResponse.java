package com.example.onboarding.dto;

import java.util.List;

public record PageResponse<T>(
        int page,
        int pageSize,
        long total,
        List<T> items
) {}
