package org.rowtown.rms.rrr.controller;

import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.DocumentResponse;
import org.rowtown.rms.rrr.service.AuthorizationService;
import org.rowtown.rms.rrr.service.DocumentManagerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(
        summary = "Create a new race timing document",
        description = "Creates a new document with version 1. The document is stored with full version history and can include race results, start lists, or other timing data in EMF format."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Document created successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DocumentResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - missing required fields or invalid data format"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permissions for this regatta or document type"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - document with same identifier already exists"
        )
    })
    public ResponseEntity<DocumentResponse> createDocument(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Document creation request",
            required = true,
            content = @Content(
                schema = @Schema(implementation = DocumentRequest.class),
                examples = @ExampleObject(
                    name = "Race Results",
                    value = """
                    {
                      "type": "RACE_RESULTS",
                      "regattaId": "REG2025-SPRING",
                      "timerId": "TIMER-001",
                      "milestoneId": "HEAT-1",
                      "versionType": "OFFICIAL",
                      "author": "john.doe@rowing.org",
                      "description": "Final race results for Heat 1",
                      "tags": ["finals", "mens-8"],
                      "metadata": {
                        "weather": "sunny",
                        "wind": "5mph"
                      }
                    }
                    """
                )
            )
        )
        @RequestBody DocumentRequest request) {
        // Check authorization
        authorizationService.checkAuthorization(request.getRegattaId(), request.getType(), org.rowtown.rms.rrr.domain.Operation.CREATE);

        DocumentResponse response = documentService.createDocument(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get document by ID",
        description = "Retrieves the latest version of a document including all metadata and the EMF model data"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Document retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DocumentResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permissions to access this document"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found"
        )
    })
    public ResponseEntity<DocumentResponse> getDocument(
        @Parameter(description = "Document ID", example = "12345", required = true)
        @PathVariable Long id) {
        DocumentResponse response = documentService.getLatestDocument(id);

        // Check authorization
        authorizationService.checkDocumentAccess(id, response.getRegattaId(),
            response.getType(), org.rowtown.rms.rrr.domain.Operation.READ);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update document",
        description = "Updates a document by creating a new version. All previous versions are preserved for full history tracking."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Document updated successfully, new version created",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DocumentResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - malformed model data or invalid format"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permissions to update this document"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found"
        )
    })
    public ResponseEntity<DocumentResponse> updateDocument(
            @Parameter(description = "Document ID", example = "12345", required = true)
            @PathVariable Long id,
            @Parameter(description = "Description of changes in this version", example = "Updated race times after timing correction")
            @RequestParam(defaultValue = "Updated document") String changeDescription,
            @Parameter(description = "Serialization format of the model data", example = "XMI")
            @RequestParam(defaultValue = "XMI") SerializationFormat format,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Binary EMF model data",
                required = true,
                content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
            )
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
    @Operation(
        summary = "Delete document",
        description = "Permanently deletes a document and all its versions. This operation cannot be undone."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "204",
            description = "Document deleted successfully"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permissions to delete this document"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found"
        )
    })
    public ResponseEntity<Void> deleteDocument(
        @Parameter(description = "Document ID", example = "12345", required = true)
        @PathVariable Long id) {
        // Get document to check permissions
        DocumentResponse doc = documentService.getLatestDocument(id);

        // Check authorization
        authorizationService.checkDocumentAccess(id, doc.getRegattaId(),
            doc.getType(), org.rowtown.rms.rrr.domain.Operation.DELETE);

        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
