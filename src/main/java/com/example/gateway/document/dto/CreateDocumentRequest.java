package com.example.gateway.document.dto;

import java.util.List;

public record CreateDocumentRequest(
        String title,
        String url,
        List<String> relatedEvidenceFields  // Profile field keys this document can be used as evidence for
) {}