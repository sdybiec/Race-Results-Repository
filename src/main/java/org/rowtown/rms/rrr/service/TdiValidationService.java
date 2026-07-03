package org.rowtown.rms.rrr.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.rowtown.rms.rrr.config.TdiModelConfig;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Validates submitted TDI (Timing Data Interchange) models on ingest, using the
 * generated EMF model.
 *
 * <p>Validation runs in three modes controlled by properties:</p>
 * <ul>
 *   <li>{@code rrr.tdi.validation.enabled=false} &mdash; skip entirely (bytes
 *       are treated as opaque).</li>
 *   <li>{@code enabled=true, strict=false} (default) &mdash; the payload must
 *       parse as a TDI model; structural (EMF {@link Diagnostician}) errors are
 *       logged but the document is still accepted. This suits real-world data
 *       such as start lists that legitimately omit result-only fields.</li>
 *   <li>{@code enabled=true, strict=true} &mdash; additionally reject documents
 *       whose model has {@link Diagnostic#ERROR}-level problems.</li>
 * </ul>
 *
 * <p>Rejections are raised as {@link IllegalArgumentException}, which the global
 * exception handler maps to HTTP 400.</p>
 */
@Service
@Slf4j
public class TdiValidationService {

    private final ModelSerializationService serializationService;
    private final boolean enabled;
    private final boolean strict;

    public TdiValidationService(
            ModelSerializationService serializationService,
            @Value("${rrr.tdi.validation.enabled:true}") boolean enabled,
            @Value("${rrr.tdi.validation.strict:false}") boolean strict) {
        this.serializationService = serializationService;
        this.enabled = enabled;
        this.strict = strict;
    }

    /**
     * Validate model bytes. No-op when validation is disabled.
     *
     * @throws IllegalArgumentException if the payload cannot be parsed as a TDI
     *         model, or (in strict mode) if the model has ERROR-level problems.
     */
    public void validate(byte[] modelData, SerializationFormat format) {
        if (!enabled) {
            return;
        }
        if (modelData == null || modelData.length == 0) {
            throw new IllegalArgumentException("Model data is required");
        }

        EObject root;
        try {
            root = serializationService.deserializeToEObject(modelData, format);
        } catch (IOException | RuntimeException ex) {
            // XMI parse / package-resolution failures surface as a 400.
            throw new IllegalArgumentException(
                "Model data is not a valid " + format + " document: " + ex.getMessage(), ex);
        }

        if (root == null) {
            throw new IllegalArgumentException("Model data did not contain a model object");
        }

        EPackage ePackage = root.eClass().getEPackage();
        String nsUri = ePackage != null ? ePackage.getNsURI() : null;
        if (!TdiModelConfig.TDI_NS_URI.equals(nsUri)) {
            throw new IllegalArgumentException(
                "Model is not a TDI model (expected namespace " + TdiModelConfig.TDI_NS_URI
                    + " but was " + nsUri + ")");
        }

        Diagnostic diagnostic = Diagnostician.INSTANCE.validate(root);
        if (diagnostic.getSeverity() >= Diagnostic.ERROR) {
            String summary = summarize(diagnostic);
            if (strict) {
                throw new IllegalArgumentException("TDI model failed validation: " + summary);
            }
            log.warn("TDI model has validation errors (accepted; strict mode off): {}", summary);
        } else if (diagnostic.getSeverity() >= Diagnostic.WARNING) {
            log.debug("TDI model validation warnings: {}", summarize(diagnostic));
        }
    }

    private String summarize(Diagnostic diagnostic) {
        return diagnostic.getChildren().stream()
            .filter(child -> child.getSeverity() >= Diagnostic.WARNING)
            .map(Diagnostic::getMessage)
            .collect(Collectors.joining("; "));
    }
}
