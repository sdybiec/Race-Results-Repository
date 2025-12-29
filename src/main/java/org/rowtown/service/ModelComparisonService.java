package org.rowtown.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.EObject;
import org.rowtown.domain.SerializationFormat;
import org.rowtown.dto.ModelDiff;
import org.rowtown.exception.VersionControlException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for comparing EMF models using EMF Compare.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelComparisonService {

    private final ModelSerializationService serializationService;

    /**
     * Compare two model versions and return the differences.
     */
    public ModelDiff compareModels(byte[] sourceData, SerializationFormat sourceFormat,
                                   byte[] targetData, SerializationFormat targetFormat) {
        try {
            // Deserialize models
            Object sourceObj = serializationService.deserialize(sourceData, sourceFormat);
            Object targetObj = serializationService.deserialize(targetData, targetFormat);

            if (!(sourceObj instanceof EObject) || !(targetObj instanceof EObject)) {
                throw new VersionControlException("Models must be EMF EObjects for comparison");
            }

            EObject sourceModel = (EObject) sourceObj;
            EObject targetModel = (EObject) targetObj;

            // Create comparison scope
            IComparisonScope scope = new DefaultComparisonScope(targetModel, sourceModel, null);

            // Perform comparison
            Comparison comparison = EMFCompare.builder().build().compare(scope);

            // Extract differences
            List<String> changes = new ArrayList<>();
            for (Diff diff : comparison.getDifferences()) {
                changes.add(formatDiff(diff));
            }

            return ModelDiff.builder()
                .totalChanges(changes.size())
                .changes(changes)
                .build();

        } catch (IOException e) {
            throw new VersionControlException("Failed to deserialize models for comparison", e);
        } catch (Exception e) {
            throw new VersionControlException("Failed to compare models", e);
        }
    }

    /**
     * Format a Diff object into a human-readable string.
     */
    private String formatDiff(Diff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append(diff.getKind()).append(": ");

        if (diff.getMatch() != null && diff.getMatch().getLeft() != null) {
            sb.append(diff.getMatch().getLeft().eClass().getName());
        }

        return sb.toString();
    }
}
