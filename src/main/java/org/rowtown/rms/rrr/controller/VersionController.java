package org.rowtown.rms.rrr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.rowtown.rms.rrr.dto.ModelDiff;
import org.rowtown.rms.rrr.dto.VersionInfo;
import org.rowtown.rms.rrr.service.AuthorizationService;
import org.rowtown.rms.rrr.service.DocumentManagerService;
import org.rowtown.rms.rrr.service.ModelComparisonService;
import org.rowtown.rms.rrr.service.VersionControlService;
import org.rowtown.rms.rrr.domain.entity.Version;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for version control operations.
 */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/versions")
@RequiredArgsConstructor
@Tag(name = "Versions", description = "Version control API")
public class VersionController {

    private final VersionControlService versionControlService;
    private final ModelComparisonService comparisonService;
    private final DocumentManagerService documentService;
    private final AuthorizationService authorizationService;

    @GetMapping
    @Operation(
        summary = "List all versions of a document",
        description = "Returns paginated list of all versions for a document, ordered by version number (newest first)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Versions retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Page.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found"
        )
    })
    public ResponseEntity<Page<VersionInfo>> listVersions(
            @Parameter(description = "Document ID", example = "12345", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size) {

        Page<VersionInfo> versions = versionControlService.listVersions(documentId, page, size);
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{version}")
    @Operation(
        summary = "Get specific version",
        description = "Retrieves detailed information about a specific version of a document (metadata only, no model data)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Version retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = VersionInfo.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document or version not found"
        )
    })
    public ResponseEntity<VersionInfo> getVersion(
            @Parameter(description = "Document ID", example = "12345", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "Version number", example = "3", required = true)
            @PathVariable Long version) {

        Version v = versionControlService.getVersion(documentId, version);
        return ResponseEntity.ok(toVersionInfo(v));
    }

    @GetMapping("/{v1}/compare/{v2}")
    @Operation(
        summary = "Compare two versions",
        description = "Compares two versions of a document and returns a detailed diff showing all changes between them using EMF Compare"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Comparison completed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ModelDiff.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document or one of the versions not found"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Versions cannot be compared (format mismatch or corrupt data)"
        )
    })
    public ResponseEntity<ModelDiff> compareVersions(
            @Parameter(description = "Document ID", example = "12345", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "First version number", example = "3", required = true)
            @PathVariable Long v1,
            @Parameter(description = "Second version number", example = "5", required = true)
            @PathVariable Long v2) {

        Version version1 = versionControlService.getVersion(documentId, v1);
        Version version2 = versionControlService.getVersion(documentId, v2);

        ModelDiff diff = comparisonService.compareModels(
            version1.getModelSnapshot(), version1.getSnapshotFormat(),
            version2.getModelSnapshot(), version2.getSnapshotFormat());

        return ResponseEntity.ok(diff);
    }

    @PostMapping("/rollback")
    @Operation(
        summary = "Rollback document to previous version",
        description = "Creates a new version that restores the content from a previous version. The rollback itself becomes a new version in the history."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Rollback successful, new version created",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = VersionInfo.class)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permissions to rollback this document"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document or target version not found"
        )
    })
    public ResponseEntity<VersionInfo> rollback(
            @Parameter(description = "Document ID", example = "12345", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "Version number to rollback to", example = "3", required = true)
            @RequestParam Long targetVersion,
            @Parameter(description = "Description for the rollback operation", example = "Reverting to pre-correction state")
            @RequestParam(required = false) String description) {

        // Get document for authorization check
        var doc = documentService.getLatestDocument(documentId);

        // Check authorization
        authorizationService.checkDocumentAccess(documentId, doc.getRegattaId(),
            doc.getType(), org.rowtown.rms.rrr.domain.Operation.ROLLBACK);

        String author = authorizationService.getCurrentUser().getUserId();
        Version rolledBackVersion = versionControlService.rollback(documentId, targetVersion, author, description);

        return ResponseEntity.ok(toVersionInfo(rolledBackVersion));
    }

    private VersionInfo toVersionInfo(Version version) {
        return VersionInfo.builder()
            .versionId(version.getVersionId())
            .documentId(version.getDocument().getDocumentId())
            .versionNumber(version.getVersionNumber())
            .timestamp(version.getTimestamp())
            .author(version.getAuthor())
            .changeDescription(version.getChangeDescription())
            .checksum(version.getChecksum())
            .size(version.getModelSnapshot() != null ? (long) version.getModelSnapshot().length : 0L)
            .build();
    }
}
