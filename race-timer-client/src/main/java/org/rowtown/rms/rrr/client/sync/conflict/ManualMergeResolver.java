package org.rowtown.rms.rrr.client.sync.conflict;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.model.LocalDocument;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Conflict resolver that prompts the user to manually resolve conflicts.
 *
 * <p>This resolver delegates the conflict resolution decision to application code,
 * typically by showing a UI dialog where the user can choose which version to keep
 * or manually merge the changes.
 *
 * <p><b>When to use:</b>
 * <ul>
 *   <li>When conflicts require human judgment to resolve correctly</li>
 *   <li>When you want users to review conflicts before accepting changes</li>
 *   <li>When data accuracy is critical and automatic resolution is risky</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. However, the provided
 * {@link MergeHandler} must also be thread-safe if used from multiple threads.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Create resolver with UI prompt
 * ManualMergeResolver resolver = new ManualMergeResolver(conflict -> {
 *     // Show dialog to user in UI thread
 *     return CompletableFuture.supplyAsync(() -> {
 *         MergeDialog dialog = new MergeDialog(conflict);
 *         dialog.showAndWait();
 *         return dialog.getMergedDocument();
 *     }, Platform::runLater);
 * });
 *
 * // Use with sync service
 * DocumentSynchronizationService service = DocumentSynchronizationService.builder()
 *     .conflictResolver(resolver)
 *     .build();
 * }</pre>
 */
@Slf4j
public class ManualMergeResolver implements ConflictResolver {

    private final MergeHandler mergeHandler;

    /**
     * Creates a manual merge resolver with the specified merge handler.
     *
     * @param mergeHandler the handler that will prompt the user and perform the merge
     */
    public ManualMergeResolver(MergeHandler mergeHandler) {
        if (mergeHandler == null) {
            throw new IllegalArgumentException("MergeHandler cannot be null");
        }
        this.mergeHandler = mergeHandler;
    }

    /**
     * Resolves a conflict by delegating to the merge handler.
     *
     * <p>The merge handler is expected to prompt the user and return a merged document.
     * The returned future may complete asynchronously if user interaction is required.
     *
     * @param conflict the conflict to resolve
     * @return a future that completes when the user has resolved the conflict
     */
    @Override
    public CompletableFuture<ConflictResolution> resolve(Conflict conflict) {
        log.debug("Resolving conflict for document {} using manual merge",
                conflict.getDocumentId());

        return mergeHandler.merge(conflict)
                .thenApply(mergedDocument -> {
                    if (mergedDocument == null) {
                        throw new ConflictResolutionException(
                                "Merge handler returned null document",
                                conflict);
                    }

                    ConflictResolution resolution = ConflictResolution.manualMerge(
                            conflict,
                            mergedDocument,
                            "User manually merged document");

                    log.info("User resolved conflict for document {} via manual merge",
                            conflict.getDocumentId());

                    return resolution;
                })
                .exceptionally(error -> {
                    log.error("Manual merge failed for document {}: {}",
                            conflict.getDocumentId(), error.getMessage());
                    throw new ConflictResolutionException(
                            "Manual merge failed: " + error.getMessage(),
                            conflict,
                            error);
                });
    }

    /**
     * Handler interface for manual conflict merging.
     *
     * <p>Implementations should prompt the user (typically via a UI dialog) and return
     * the merged document. The merge can happen asynchronously.
     *
     * <p><b>Implementation Requirements:</b>
     * <ul>
     *   <li>Must return a non-null CompletableFuture</li>
     *   <li>The future must eventually complete with a non-null document</li>
     *   <li>If merging is cancelled, complete exceptionally with a descriptive exception</li>
     *   <li>Must be thread-safe if used from multiple threads</li>
     * </ul>
     *
     * <p><b>Example Implementation:</b>
     * <pre>{@code
     * public class SwingMergeHandler implements MergeHandler {
     *     @Override
     *     public CompletableFuture<LocalDocument> merge(Conflict conflict) {
     *         CompletableFuture<LocalDocument> future = new CompletableFuture<>();
     *
     *         SwingUtilities.invokeLater(() -> {
     *             try {
     *                 ConflictDialog dialog = new ConflictDialog(conflict);
     *                 int result = dialog.showDialog();
     *
     *                 if (result == ConflictDialog.OK) {
     *                     future.complete(dialog.getMergedDocument());
     *                 } else {
     *                     future.completeExceptionally(
     *                         new CancellationException("User cancelled merge"));
     *                 }
     *             } catch (Exception e) {
     *                 future.completeExceptionally(e);
     *             }
     *         });
     *
     *         return future;
     *     }
     * }
     * }</pre>
     */
    @FunctionalInterface
    public interface MergeHandler {
        /**
         * Merges a conflict by prompting the user.
         *
         * @param conflict the conflict to merge
         * @return a future that completes with the merged document
         */
        CompletableFuture<LocalDocument> merge(Conflict conflict);
    }

    /**
     * Creates a simple merge handler that lets the user choose between local or server version.
     *
     * <p>This is a utility method for creating a basic merge handler without custom merge logic.
     *
     * @param choiceProvider a function that asks the user to choose between versions.
     *                      Should return true for local version, false for server version.
     * @return a merge handler that implements simple version selection
     */
    public static MergeHandler simpleChoiceHandler(Function<Conflict, CompletableFuture<Boolean>> choiceProvider) {
        return conflict -> choiceProvider.apply(conflict)
                .thenApply(chooseLocal -> chooseLocal
                        ? conflict.getLocalVersion()
                        : conflict.getServerVersion());
    }
}
