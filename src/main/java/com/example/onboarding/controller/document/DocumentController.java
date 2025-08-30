package com.example.onboarding.controller.document;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.document.CreateDocumentRequest;
import com.example.onboarding.dto.document.DocumentResponse;
import com.example.onboarding.dto.document.DocumentSummary;
import com.example.onboarding.dto.document.EnhancedDocumentResponse;
import com.example.onboarding.dto.profile.ProfileFieldTypeInfo;
import com.example.onboarding.service.document.DocumentService;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;
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
        try {
            DocumentResponse response = documentService.createDocument(appId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
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