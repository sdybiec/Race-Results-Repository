package org.rowtown.rms.rrr.client.sync.conflict;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.model.LocalDocument;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Conflict resolver that applies custom application-specific logic to resolve conflicts.
 *
 * <p>This resolver allows applications to implement domain-specific conflict resolution
 * strategies based on business rules, document types, or other custom criteria.
 *
 * <p><b>When to use:</b>
 * <ul>
 *   <li>When you have specific business rules for merging documents</li>
 *   <li>When different document types require different merge strategies</li>
 *   <li>When you need to merge specific fields rather than choosing entire versions</li>
 *   <li>When you want field-level conflict resolution</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. However, the provided
 * {@link CustomMergeFunction} must also be thread-safe if used from multiple threads.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Field-level merge for start lists
 * CustomConflictResolver resolver = new CustomConflictResolver(conflict -> {
 *     if ("STARTLIST".equals(conflict.getDocumentType())) {
 *         return mergeStartListFields(conflict);
 *     } else if ("RESULTS".equals(conflict.getDocumentType())) {
 *         return mergeResultsFields(conflict);
 *     } else {
 *         // Fall back to server version for unknown types
 *         return CompletableFuture.completedFuture(
 *             new MergeResult(conflict.getServerVersion(), false)
 *         );
 *     }
 * });
 *
 * // Custom merge for start lists
 * private CompletableFuture<MergeResult> mergeStartListFields(Conflict conflict) {
 *     LocalDocument merged = new LocalDocument();
 *     // Copy unchanged fields from server version
 *     merged.setDocumentId(conflict.getServerVersion().getDocumentId());
 *     // Merge specific fields based on business rules
 *     merged.setModelData(mergeStartListData(
 *         conflict.getLocalVersion().getModelData(),
 *         conflict.getServerVersion().getModelData()
 *     ));
 *     return CompletableFuture.completedFuture(
 *         new MergeResult(merged, true, "Merged start list fields")
 *     );
 * }
 * }</pre>
 */
@Slf4j
public class CustomConflictResolver implements ConflictResolver {

    private final CustomMergeFunction mergeFunction;

    /**
     * Creates a custom conflict resolver with the specified merge function.
     *
     * @param mergeFunction the function that implements custom merge logic
     */
    public CustomConflictResolver(CustomMergeFunction mergeFunction) {
        if (mergeFunction == null) {
            throw new IllegalArgumentException("CustomMergeFunction cannot be null");
        }
        this.mergeFunction = mergeFunction;
    }

    /**
     * Resolves a conflict using the custom merge function.
     *
     * @param conflict the conflict to resolve
     * @return a future that completes with the resolution
     */
    @Override
    public CompletableFuture<ConflictResolution> resolve(Conflict conflict) {
        log.debug("Resolving conflict for document {} using custom resolver",
                conflict.getDocumentId());

        return mergeFunction.merge(conflict)
                .thenApply(mergeResult -> {
                    if (mergeResult == null || mergeResult.getMergedDocument() == null) {
                        throw new ConflictResolutionException(
                                "Custom merge function returned null result",
                                conflict);
                    }

                    ConflictResolution resolution = ConflictResolution.custom(
                            conflict,
                            mergeResult.getMergedDocument(),
                            mergeResult.isRequiresServerUpdate(),
                            mergeResult.getNotes());

                    log.info("Custom resolver resolved conflict for document {}: {}",
                            conflict.getDocumentId(),
                            mergeResult.getNotes() != null ? mergeResult.getNotes() : "custom merge applied");

                    return resolution;
                })
                .exceptionally(error -> {
                    log.error("Custom merge failed for document {}: {}",
                            conflict.getDocumentId(), error.getMessage());
                    throw new ConflictResolutionException(
                            "Custom merge failed: " + error.getMessage(),
                            conflict,
                            error);
                });
    }

    /**
     * Function interface for custom conflict merging.
     *
     * <p>Implementations should apply application-specific merge logic and return
     * a {@link MergeResult} containing the merged document.
     */
    @FunctionalInterface
    public interface CustomMergeFunction {
        /**
         * Merges a conflict using custom logic.
         *
         * @param conflict the conflict to merge
         * @return a future that completes with the merge result
         */
        CompletableFuture<MergeResult> merge(Conflict conflict);
    }

    /**
     * Result of a custom merge operation.
     *
     * <p>This class encapsulates the merged document and metadata about the merge.
     */
    public static class MergeResult {
        private final LocalDocument mergedDocument;
        private final boolean requiresServerUpdate;
        private final String notes;

        /**
         * Creates a merge result.
         *
         * @param mergedDocument the merged document
         * @param requiresServerUpdate whether the merged document should be uploaded to server
         */
        public MergeResult(LocalDocument mergedDocument, boolean requiresServerUpdate) {
            this(mergedDocument, requiresServerUpdate, null);
        }

        /**
         * Creates a merge result with notes.
         *
         * @param mergedDocument the merged document
         * @param requiresServerUpdate whether the merged document should be uploaded to server
         * @param notes description of the merge operation
         */
        public MergeResult(LocalDocument mergedDocument, boolean requiresServerUpdate, String notes) {
            this.mergedDocument = mergedDocument;
            this.requiresServerUpdate = requiresServerUpdate;
            this.notes = notes;
        }

        public LocalDocument getMergedDocument() {
            return mergedDocument;
        }

        public boolean isRequiresServerUpdate() {
            return requiresServerUpdate;
        }

        public String getNotes() {
            return notes;
        }
    }

    /**
     * Creates a custom resolver that uses different strategies based on document type.
     *
     * @param typeBasedStrategy a function that returns a resolver for each document type
     * @return a custom resolver that delegates to type-specific resolvers
     */
    public static CustomConflictResolver typeBasedResolver(
            Function<String, ConflictResolver> typeBasedStrategy) {
        return new CustomConflictResolver(conflict -> {
            String documentType = conflict.getDocumentType();
            ConflictResolver resolver = typeBasedStrategy.apply(documentType);

            if (resolver == null) {
                // No specific resolver for this type - default to server wins
                return CompletableFuture.completedFuture(
                        new MergeResult(conflict.getServerVersion(), false,
                                "No custom resolver for type " + documentType));
            }

            return resolver.resolve(conflict)
                    .thenApply(resolution -> new MergeResult(
                            resolution.getResolvedDocument(),
                            resolution.isRequiresServerUpdate(),
                            "Resolved using type-specific strategy for " + documentType));
        });
    }
}
