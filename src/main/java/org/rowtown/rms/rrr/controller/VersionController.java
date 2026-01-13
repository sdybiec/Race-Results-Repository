package org.rowtown.rms.rrr.controller;

import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "List versions", description = "Lists all versions of a document with pagination")
    public ResponseEntity<Page<VersionInfo>> listVersions(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<VersionInfo> versions = versionControlService.listVersions(documentId, page, size);
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{version}")
    @Operation(summary = "Get specific version", description = "Retrieves a specific version of a document")
    public ResponseEntity<VersionInfo> getVersion(
            @PathVariable Long documentId,
            @PathVariable Long version) {

        Version v = versionControlService.getVersion(documentId, version);
        return ResponseEntity.ok(toVersionInfo(v));
    }

    @GetMapping("/{v1}/compare/{v2}")
    @Operation(summary = "Compare versions", description = "Compares two versions and returns the differences")
    public ResponseEntity<ModelDiff> compareVersions(
            @PathVariable Long documentId,
            @PathVariable Long v1,
            @PathVariable Long v2) {

        Version version1 = versionControlService.getVersion(documentId, v1);
        Version version2 = versionControlService.getVersion(documentId, v2);

        ModelDiff diff = comparisonService.compareModels(
            version1.getModelSnapshot(), version1.getSnapshotFormat(),
            version2.getModelSnapshot(), version2.getSnapshotFormat());

        return ResponseEntity.ok(diff);
    }

    @PostMapping("/rollback")
    @Operation(summary = "Rollback to version", description = "Rolls back a document to a specific version")
    public ResponseEntity<VersionInfo> rollback(
            @PathVariable Long documentId,
            @RequestParam Long targetVersion,
            @RequestParam(required = false) String description) {

        // Get document for authorization check
        var doc = documentService.getLatestDocument(documentId);

        // Check authorization
        authorizationService.checkDocumentAccess(documentId, doc.getRegattaId(),
            doc.getType(), org.rowtown.domain.Operation.ROLLBACK);

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
