package com.example.gateway.document.controller;

import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.document.dto.CreateDocumentRequest;
import com.example.gateway.document.dto.DocumentResponse;
import com.example.gateway.document.dto.DocumentSummary;
import com.example.gateway.document.dto.EnhancedDocumentResponse;
import com.example.gateway.profile.dto.ProfileFieldTypeInfo;
import com.example.gateway.document.service.DocumentService;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentController {
    
    private final DocumentService documentService;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    
    public DocumentController(DocumentService documentService,
                             ProfileFieldRegistryService profileFieldRegistryService) {
        this.documentService = documentService;
        this.profileFieldRegistryService = profileFieldRegistryService;
    }
    
    /**
     * Create a new document for an application
     * POST /api/apps/{appId}/documents
     */
    @PostMapping("/apps/{appId}/documents")
    public ResponseEntity<DocumentResponse> createDocument(@PathVariable String appId,
                                                          @RequestBody CreateDocumentRequest request) {
        DocumentResponse response = documentService.createDocument(appId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all documents for an application with pagination
     * Enhanced (add attachment info when profileFieldId query param provided)
     * GET /api/apps/{appId}/documents?page=1&pageSize=10
     * GET /api/apps/{appId}/documents?page=1&pageSize=10&profileFieldId={profileFieldId}
     */
    @GetMapping("/apps/{appId}/documents")
    public ResponseEntity<?> getDocuments(
            @PathVariable String appId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String profileFieldId) {
        
        if (profileFieldId != null) {
            // Return enhanced response with attachment status
            PageResponse<EnhancedDocumentResponse> documents = 
                documentService.getDocumentsWithAttachmentStatus(appId, profileFieldId, page, pageSize);
            return ResponseEntity.ok(documents);
        } else {
            // Return basic response
            PageResponse<DocumentSummary> documents = documentService.getDocumentsByApp(appId, page, pageSize);
            return ResponseEntity.ok(documents);
        }
    }
    
    /**
     * Get documents by field type (for evidence selection)
     * GET /api/apps/{appId}/documents/by-field/{fieldKey}
     */
    @GetMapping("/apps/{appId}/documents/by-field/{fieldKey}")
    public ResponseEntity<List<DocumentSummary>> getDocumentsByFieldType(@PathVariable String appId,
                                                                         @PathVariable String fieldKey) {
        List<DocumentSummary> documents = documentService.getDocumentsByFieldType(appId, fieldKey);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * Get detailed document information
     * GET /api/documents/{documentId}
     */
    @GetMapping("/documents/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable String documentId) {
        return documentService.getDocumentById(documentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get available profile field types for document tagging
     * GET /api/profile-field-types
     */
    @GetMapping("/profile-field-types")
    public ResponseEntity<List<ProfileFieldTypeInfo>> getProfileFieldTypes() {
        List<ProfileFieldTypeInfo> fieldTypes = profileFieldRegistryService.getAllProfileFieldTypes();
        return ResponseEntity.ok(fieldTypes);
    }
    
    /**
     * Get profile field types grouped by domain
     * GET /api/profile-field-types/by-domain
     */
    @GetMapping("/profile-field-types/by-domain")
    public ResponseEntity<Map<String, List<ProfileFieldTypeInfo>>> getProfileFieldTypesByDomain() {
        Map<String, List<ProfileFieldTypeInfo>> fieldTypesByDomain = 
                profileFieldRegistryService.getProfileFieldTypesByDomain();
        return ResponseEntity.ok(fieldTypesByDomain);
    }
}