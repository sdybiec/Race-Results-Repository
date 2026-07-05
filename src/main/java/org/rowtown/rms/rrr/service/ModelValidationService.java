package org.rowtown.rms.rrr.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Validates submitted EMF models on ingest, per document type, using the
 * generated models.
 *
 * <p><strong>Fail-fast policy.</strong> A model that the generated classes
 * cannot load is unusable elsewhere in the system, so it is rejected at ingest
 * rather than stored. The model must also be in the namespace expected for its
 * document type, which prevents cross-type payloads (e.g. an RML model submitted
 * as race results).</p>
 *
 * <p>Controlled by two properties:</p>
 * <ul>
 *   <li>{@code rrr.tdi.validation.enabled=false} &mdash; skip entirely (opaque
 *       bytes; no load guarantee).</li>
 *   <li>{@code enabled=true, strict=false} (default) &mdash; must load into the
 *       generated classes and match the expected namespace; structural
 *       ({@link Diagnostician}) problems are logged but not rejected.</li>
 *   <li>{@code enabled=true, strict=true} &mdash; additionally reject models
 *       that load but have {@link Diagnostic#ERROR}-level problems.</li>
 * </ul>
 *
 * <p>Rejections are raised as {@link IllegalArgumentException}, which the global
 * exception handler maps to HTTP 400.</p>
 */
@Service
@Slf4j
public class ModelValidationService {

    private final ModelSerializationService serializationService;
    private final CurrentModelNamespaces namespaces;
    private final boolean enabled;
    private final boolean strict;

    public ModelValidationService(
            ModelSerializationService serializationService,
            CurrentModelNamespaces namespaces,
            @Value("${rrr.tdi.validation.enabled:true}") boolean enabled,
            @Value("${rrr.tdi.validation.strict:false}") boolean strict) {
        this.serializationService = serializationService;
        this.namespaces = namespaces;
        this.enabled = enabled;
        this.strict = strict;
    }

    /**
     * Validate model bytes for the given document type. No-op when disabled.
     *
     * @throws IllegalArgumentException if the payload cannot be loaded by the
     *         generated classes, is in the wrong namespace for its type, or (in
     *         strict mode) has ERROR-level problems.
     */
    public void validate(byte[] modelData, SerializationFormat format, DocumentType type) {
        if (!enabled) {
            return;
        }
        if (modelData == null || modelData.length == 0) {
            throw new IllegalArgumentException("Model data is required");
        }

        // Fail-fast: the model MUST load into the generated classes.
        EObject root;
        try {
            root = serializationService.deserializeToEObject(modelData, format);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                "Model cannot be loaded by the generated classes and would be unusable: "
                    + ex.getMessage(), ex);
        }
        if (root == null) {
            throw new IllegalArgumentException("Model data did not contain a loadable model object");
        }

        EPackage ePackage = root.eClass().getEPackage();
        String nsUri = ePackage != null ? ePackage.getNsURI() : null;
        String expected = namespaces.currentNsUri(type);
        if (expected != null && !expected.equals(nsUri)) {
            throw new IllegalArgumentException(
                "Model namespace " + nsUri + " does not match the expected namespace "
                    + expected + " for document type " + type);
        }

        Diagnostic diagnostic = Diagnostician.INSTANCE.validate(root);
        if (diagnostic.getSeverity() >= Diagnostic.ERROR) {
            String summary = summarize(diagnostic);
            if (strict) {
                throw new IllegalArgumentException("Model failed validation: " + summary);
            }
            log.warn("Model has validation errors (accepted; strict mode off): {}", summary);
        } else if (diagnostic.getSeverity() >= Diagnostic.WARNING) {
            log.debug("Model validation warnings: {}", summarize(diagnostic));
        }
    }

    private String summarize(Diagnostic diagnostic) {
        return diagnostic.getChildren().stream()
            .filter(child -> child.getSeverity() >= Diagnostic.WARNING)
            .map(Diagnostic::getMessage)
            .collect(Collectors.joining("; "));
    }
}
