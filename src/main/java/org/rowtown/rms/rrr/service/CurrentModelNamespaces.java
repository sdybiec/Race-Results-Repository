package org.rowtown.rms.rrr.service;

import org.rowtown.rms.rrr.config.RmlModelConfig;
import org.rowtown.rms.rrr.config.TdiModelConfig;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Single source of truth for the <em>current</em> metamodel namespace of each
 * document type (i.e. the version of the generated model on the classpath).
 *
 * <p>Used both to enforce the expected namespace on ingest
 * ({@link ModelValidationService}) and to detect which stored documents are on an
 * older version and should be upgraded
 * ({@link ModelUpgradePersistenceService}).</p>
 */
@Component
public class CurrentModelNamespaces {

    private final Map<DocumentType, String> byType = Map.of(
        DocumentType.START_LIST, TdiModelConfig.TDI_NS_URI,
        DocumentType.RACE_RESULTS, TdiModelConfig.TDI_NS_URI,
        DocumentType.RML, RmlModelConfig.RML_NS_URI
    );

    /**
     * @return the current metamodel {@code nsURI} for the given document type, or
     *         {@code null} if the type has no associated model namespace.
     */
    public String currentNsUri(DocumentType type) {
        return byType.get(type);
    }

    /**
     * @return true if {@code nsUri} is the current namespace for the type (or the
     *         type has no known namespace, in which case nothing is enforced).
     */
    public boolean isCurrent(DocumentType type, String nsUri) {
        String current = byType.get(type);
        return current == null || current.equals(nsUri);
    }
}
