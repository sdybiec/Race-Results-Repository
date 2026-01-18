package org.rowtown.rms.rrr.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.model.LocalDocument;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Conflict resolver that automatically picks the document version with the most recent timestamp.
 *
 * <p>This is the simplest conflict resolution strategy - it assumes that the last write
 * is always the correct one, regardless of the content of the changes.
 *
 * <p><b>When to use:</b>
 * <ul>
 *   <li>When document changes are independent and don't build on each other</li>
 *   <li>When you want automatic conflict resolution without user intervention</li>
 *   <li>When timestamp-based resolution is acceptable for your use case</li>
 * </ul>
 *
 * <p><b>Limitations:</b>
 * <ul>
 *   <li>May lose important changes if clocks are not synchronized</li>
 *   <li>Does not consider the content or importance of changes</li>
 *   <li>Cannot merge changes from both versions</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe and can be reused.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * ConflictResolver resolver = new LastWriteWinsResolver();
 * DocumentSynchronizationService service = DocumentSynchronizationService.builder()
 *     .conflictResolver(resolver)
 *     .build();
 * }</pre>
 */
@Slf4j
public class LastWriteWinsResolver implements ConflictResolver {

    /**
     * Resolves a conflict by selecting the version with the most recent timestamp.
     *
     * <p>The comparison is done based on the document's last modified timestamp.
     * If both documents have the same timestamp (rare), the server version is preferred.
     *
     * @param conflict the conflict to resolve
     * @return a future that immediately completes with the resolution
     */
    @Override
    public CompletableFuture<ConflictResolution> resolve(Conflict conflict) {
        log.debug("Resolving conflict for document {} using LastWriteWins strategy",
                conflict.getDocumentId());

        LocalDocument localVersion = conflict.getLocalVersion();
        LocalDocument serverVersion = conflict.getServerVersion();

        // Get timestamps
        LocalDateTime localTime = getLastModifiedTime(localVersion);
        LocalDateTime serverTime = getLastModifiedTime(serverVersion);

        // Compare and pick winner
        LocalDocument winner;
        if (localTime == null && serverTime == null) {
            // Both timestamps missing - prefer server
            log.warn("Both versions missing timestamp for document {}, defaulting to server version",
                    conflict.getDocumentId());
            winner = serverVersion;
        } else if (localTime == null) {
            // Local timestamp missing - use server
            winner = serverVersion;
        } else if (serverTime == null) {
            // Server timestamp missing - use local
            winner = localVersion;
        } else if (localTime.isAfter(serverTime)) {
            // Local is newer
            log.debug("Local version is newer ({} vs {}), selecting local",
                    localTime, serverTime);
            winner = localVersion;
        } else if (serverTime.isAfter(localTime)) {
            // Server is newer
            log.debug("Server version is newer ({} vs {}), selecting server",
                    serverTime, localTime);
            winner = serverVersion;
        } else {
            // Same timestamp - prefer server to avoid unnecessary uploads
            log.debug("Versions have same timestamp, defaulting to server version");
            winner = serverVersion;
        }

        ConflictResolution resolution = ConflictResolution.lastWriteWins(conflict, winner);

        log.info("Resolved conflict for document {} using LastWriteWins: {} version selected",
                conflict.getDocumentId(),
                winner == serverVersion ? "server" : "local");

        return CompletableFuture.completedFuture(resolution);
    }

    /**
     * Extracts the last modified timestamp from a document.
     * Tries multiple timestamp fields in order of preference.
     */
    private LocalDateTime getLastModifiedTime(LocalDocument document) {
        if (document == null) {
            return null;
        }

        // Try lastModified field first
        if (document.getLastModified() != null) {
            return document.getLastModified();
        }

        // Fall back to syncedAt if available
        if (document.getSyncedAt() != null) {
            return document.getSyncedAt();
        }

        // Fall back to createdAt
        if (document.getCreatedAt() != null) {
            return document.getCreatedAt();
        }

        return null;
    }
}
