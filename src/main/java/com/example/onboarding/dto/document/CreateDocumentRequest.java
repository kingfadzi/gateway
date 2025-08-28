package com.example.onboarding.dto.document;

import java.util.List;

public record CreateDocumentRequest(
        String title,
        String url,
        List<String> fieldTypes  // Profile field keys this document can be used as evidence for
) {}