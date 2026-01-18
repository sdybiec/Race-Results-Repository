package org.rowtown.rms.rrr.client.sync.conflict;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for resolving conflicts between local and server document versions.
 *
 * <p>Implementations of this interface define different strategies for resolving
 * conflicts when both local and server versions of a document have changed.
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe as they may be called
 * from multiple threads concurrently.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Automatic resolution
 * ConflictResolver resolver = new LastWriteWinsResolver();
 * ConflictResolution resolution = resolver.resolve(conflict).get();
 *
 * // Manual resolution with user interaction
 * ConflictResolver resolver = new ManualMergeResolver(
 *     conflict -> promptUserForMerge(conflict)
 * );
 * ConflictResolution resolution = resolver.resolve(conflict).get();
 *
 * // Custom application logic
 * ConflictResolver resolver = new CustomConflictResolver(
 *     conflict -> mergeUsingBusinessRules(conflict)
 * );
 * ConflictResolution resolution = resolver.resolve(conflict).get();
 * }</pre>
 */
@FunctionalInterface
public interface ConflictResolver {

    /**
     * Resolves a conflict between local and server versions of a document.
     *
     * <p>This method returns a CompletableFuture to support both automatic
     * (synchronous) and manual (asynchronous) resolution strategies.
     *
     * @param conflict the conflict to resolve
     * @return a future that completes with the resolution
     * @throws ConflictResolutionException if the conflict cannot be resolved
     */
    CompletableFuture<ConflictResolution> resolve(Conflict conflict);

    /**
     * Exception thrown when a conflict cannot be resolved
     */
    class ConflictResolutionException extends RuntimeException {
        private final Conflict conflict;

        public ConflictResolutionException(String message, Conflict conflict) {
            super(message);
            this.conflict = conflict;
        }

        public ConflictResolutionException(String message, Conflict conflict, Throwable cause) {
            super(message, cause);
            this.conflict = conflict;
        }

        public Conflict getConflict() {
            return conflict;
        }
    }
}
