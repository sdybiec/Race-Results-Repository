package org.rowtown.rms.rrr.client.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.client.model.LocalDocument;

import java.time.LocalDateTime;

/**
 * Represents the resolution of a conflict between local and server document versions.
 *
 * <p>This class encapsulates the result of applying a conflict resolution strategy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConflictResolution {

    /**
     * The original conflict that was resolved
     */
    private Conflict conflict;

    /**
     * The resolution strategy that was applied
     */
    private ResolutionStrategy strategy;

    /**
     * The resolved document version to use
     */
    private LocalDocument resolvedDocument;

    /**
     * Whether the resolution requires uploading to the server
     * (true for local wins or merged versions)
     */
    private boolean requiresServerUpdate;

    /**
     * Whether the resolution requires updating local storage
     */
    private boolean requiresLocalUpdate;

    /**
     * When the resolution was completed
     */
    private LocalDateTime resolvedAt;

    /**
     * Additional notes about the resolution
     */
    private String resolutionNotes;

    /**
     * Whether this was an automatic resolution or required manual intervention
     */
    private boolean automatic;

    /**
     * The resolution strategies available
     */
    public enum ResolutionStrategy {
        /** Server version wins - discard local changes */
        SERVER_WINS,

        /** Local version wins - upload to server */
        LOCAL_WINS,

        /** Last write based on timestamp wins */
        LAST_WRITE_WINS,

        /** Manual merge by user */
        MANUAL_MERGE,

        /** Custom application logic */
        CUSTOM,

        /** Defer resolution for later */
        DEFERRED
    }

    /**
     * Creates a server-wins resolution
     */
    public static ConflictResolution serverWins(Conflict conflict) {
        return ConflictResolution.builder()
                .conflict(conflict)
                .strategy(ResolutionStrategy.SERVER_WINS)
                .resolvedDocument(conflict.getServerVersion())
                .requiresServerUpdate(false)
                .requiresLocalUpdate(true)
                .resolvedAt(LocalDateTime.now())
                .automatic(true)
                .resolutionNotes("Server version accepted")
                .build();
    }

    /**
     * Creates a local-wins resolution
     */
    public static ConflictResolution localWins(Conflict conflict) {
        return ConflictResolution.builder()
                .conflict(conflict)
                .strategy(ResolutionStrategy.LOCAL_WINS)
                .resolvedDocument(conflict.getLocalVersion())
                .requiresServerUpdate(true)
                .requiresLocalUpdate(false)
                .resolvedAt(LocalDateTime.now())
                .automatic(true)
                .resolutionNotes("Local version accepted")
                .build();
    }

    /**
     * Creates a last-write-wins resolution
     */
    public static ConflictResolution lastWriteWins(Conflict conflict, LocalDocument winner) {
        boolean serverWon = winner == conflict.getServerVersion();
        return ConflictResolution.builder()
                .conflict(conflict)
                .strategy(ResolutionStrategy.LAST_WRITE_WINS)
                .resolvedDocument(winner)
                .requiresServerUpdate(!serverWon)
                .requiresLocalUpdate(serverWon)
                .resolvedAt(LocalDateTime.now())
                .automatic(true)
                .resolutionNotes("Resolved by timestamp: " + (serverWon ? "server" : "local") + " version is newer")
                .build();
    }

    /**
     * Creates a manual merge resolution
     */
    public static ConflictResolution manualMerge(Conflict conflict, LocalDocument merged, String notes) {
        return ConflictResolution.builder()
                .conflict(conflict)
                .strategy(ResolutionStrategy.MANUAL_MERGE)
                .resolvedDocument(merged)
                .requiresServerUpdate(true)
                .requiresLocalUpdate(true)
                .resolvedAt(LocalDateTime.now())
                .automatic(false)
                .resolutionNotes(notes != null ? notes : "Manually merged by user")
                .build();
    }

    /**
     * Creates a custom resolution
     */
    public static ConflictResolution custom(Conflict conflict, LocalDocument resolved, boolean uploadToServer, String notes) {
        return ConflictResolution.builder()
                .conflict(conflict)
                .strategy(ResolutionStrategy.CUSTOM)
                .resolvedDocument(resolved)
                .requiresServerUpdate(uploadToServer)
                .requiresLocalUpdate(true)
                .resolvedAt(LocalDateTime.now())
                .automatic(true)
                .resolutionNotes(notes != null ? notes : "Resolved by custom logic")
                .build();
    }
}
