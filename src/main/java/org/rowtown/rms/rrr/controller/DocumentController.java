package org.rowtown.rms.rrr.controller;

import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.DocumentResponse;
import org.rowtown.rms.rrr.service.AuthorizationService;
import org.rowtown.rms.rrr.service.DocumentManagerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for document management operations.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document management API")
public class DocumentController {

    private final DocumentManagerService documentService;
    private final AuthorizationService authorizationService;

    @PostMapping
    @Operation(summary = "Create a new document", description = "Creates a new document with an initial version")
    public ResponseEntity<DocumentResponse> createDocument(@RequestBody DocumentRequest request) {
        // Check authorization
        authorizationService.checkAuthorization(request.getRegattaId(), request.getType(), org.rowtown.rms.rrr.domain.Operation.CREATE);

        DocumentResponse response = documentService.createDocument(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID", description = "Retrieves the latest version of a document")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        DocumentResponse response = documentService.getLatestDocument(id);

        // Check authorization
        authorizationService.checkDocumentAccess(id, response.getRegattaId(),
            response.getType(), org.rowtown.rms.rrr.domain.Operation.READ);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/versions/{version}")
    @Operation(summary = "Get specific document version", description = "Retrieves a specific version of a document")
    public ResponseEntity<DocumentResponse> getDocumentVersion(
            @PathVariable Long id,
            @PathVariable Long version) {
        DocumentResponse response = documentService.getDocument(id, version);

        // Check authorization
        authorizationService.checkDocumentAccess(id, response.getRegattaId(),
            response.getType(), org.rowtown.rms.rrr.domain.Operation.READ);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update document", description = "Updates a document, creating a new version")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Updated document") String changeDescription,
            @RequestParam(defaultValue = "XMI") SerializationFormat format,
            @RequestBody byte[] modelData) {

        // Get document to check permissions
        DocumentResponse existingDoc = documentService.getLatestDocument(id);

        // Check authorization
        authorizationService.checkDocumentAccess(id, existingDoc.getRegattaId(),
            existingDoc.getType(), org.rowtown.rms.rrr.domain.Operation.UPDATE);

        String author = authorizationService.getCurrentUser().getUserId();
        DocumentResponse response = documentService.updateDocument(id, modelData, author, changeDescription, format);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document", description = "Deletes a document and all its versions")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        // Get document to check permissions
        DocumentResponse doc = documentService.getLatestDocument(id);

        // Check authorization
        authorizationService.checkDocumentAccess(id, doc.getRegattaId(),
            doc.getType(), org.rowtown.rms.rrr.domain.Operation.DELETE);

        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
