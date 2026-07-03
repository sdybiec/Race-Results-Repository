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

import java.util.stream.Collectors;

/**
 * Validates submitted TDI (Timing Data Interchange) models on ingest, using the
 * generated EMF model where possible.
 *
 * <p>Validation runs in three modes controlled by properties:</p>
 * <ul>
 *   <li>{@code rrr.tdi.validation.enabled=false} &mdash; skip entirely (bytes
 *       are treated as opaque).</li>
 *   <li>{@code enabled=true, strict=false} (default) &mdash; the payload must be
 *       a TDI document. It is accepted if it either loads cleanly into the
 *       generated classes <em>or</em> is a well-formed XML document in the TDI
 *       namespace. This tolerates real-world data (e.g. start lists that omit
 *       result-only fields) and model/data version skew where the generated
 *       datatype converters reject otherwise-valid values.</li>
 *   <li>{@code enabled=true, strict=true} &mdash; require a clean typed load and
 *       reject models with {@link Diagnostic#ERROR}-level problems.</li>
 * </ul>
 *
 * <p>Rejections are raised as {@link IllegalArgumentException}, which the global
 * exception handler maps to HTTP 400.</p>
 */
@Service
@Slf4j
public class TdiValidationService {

    private final ModelSerializationService serializationService;
    private final TdiModelInspector tdiModelInspector;
    private final boolean enabled;
    private final boolean strict;

    public TdiValidationService(
            ModelSerializationService serializationService,
            TdiModelInspector tdiModelInspector,
            @Value("${rrr.tdi.validation.enabled:true}") boolean enabled,
            @Value("${rrr.tdi.validation.strict:false}") boolean strict) {
        this.serializationService = serializationService;
        this.tdiModelInspector = tdiModelInspector;
        this.enabled = enabled;
        this.strict = strict;
    }

    /**
     * Validate model bytes. No-op when validation is disabled.
     *
     * @throws IllegalArgumentException if the payload is not a TDI document, or
     *         (in strict mode) if it cannot be loaded as typed objects or has
     *         ERROR-level problems.
     */
    public void validate(byte[] modelData, SerializationFormat format) {
        if (!enabled) {
            return;
        }
        if (modelData == null || modelData.length == 0) {
            throw new IllegalArgumentException("Model data is required");
        }

        // Preferred path: load into the generated (typed) classes.
        EObject root = null;
        Exception loadFailure = null;
        try {
            root = serializationService.deserializeToEObject(modelData, format);
        } catch (Exception ex) {
            loadFailure = ex;
        }

        if (root != null) {
            validateTypedModel(root);
            return;
        }

        // Typed load failed or produced nothing. Fall back to a schema-agnostic
        // check so real TDI documents the generated model cannot fully parse
        // (version skew, strict datatype converters) are still accepted.
        if (!tdiModelInspector.isTdiDocument(modelData)) {
            throw new IllegalArgumentException(
                "Model data is not a valid TDI " + format + " document"
                    + (loadFailure != null ? ": " + loadFailure.getMessage() : ""));
        }

        if (strict) {
            throw new IllegalArgumentException(
                "TDI model could not be loaded as typed objects under strict validation"
                    + (loadFailure != null ? ": " + loadFailure.getMessage() : ""));
        }

        log.warn("Accepted TDI document that did not fully load as typed objects "
            + "(strict mode off): {}",
            loadFailure != null ? loadFailure.getMessage() : "no root object produced");
    }

    /**
     * Namespace and structural checks for a successfully loaded typed model.
     */
    private void validateTypedModel(EObject root) {
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
