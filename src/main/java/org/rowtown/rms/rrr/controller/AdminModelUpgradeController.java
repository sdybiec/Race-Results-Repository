package org.rowtown.rms.rrr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.UserRole;
import org.rowtown.rms.rrr.exception.AuthorizationException;
import org.rowtown.rms.rrr.service.AuthorizationService;
import org.rowtown.rms.rrr.service.ModelUpgradePersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Administrative endpoint to proactively upgrade stored documents to the current
 * model version (the batch counterpart to the lazy, read-triggered upgrade).
 *
 * <p>Typically run once after publishing a new model version to drain the
 * backlog of older-version documents.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/models")
@RequiredArgsConstructor
public class AdminModelUpgradeController {

    private final ModelUpgradePersistenceService upgradeService;
    private final AuthorizationService authorizationService;

    @PostMapping("/upgrade")
    @Operation(
        summary = "Upgrade stored documents of a type to the current model version",
        description = "Finds documents whose stored model namespace is older than the current "
            + "generated model and rewrites each as a new, current-version version. Administrator only."
    )
    public ResponseEntity<Map<String, Object>> upgrade(
            @Parameter(description = "Document type to upgrade", example = "RML", required = true)
            @RequestParam DocumentType type) {
        if (!authorizationService.hasRole(UserRole.REGATTA_ADMIN)) {
            throw new AuthorizationException("Only administrators may trigger model upgrades");
        }
        int upgraded = upgradeService.upgradeAll(type);
        return ResponseEntity.ok(Map.of("type", type, "upgraded", upgraded));
    }
}
