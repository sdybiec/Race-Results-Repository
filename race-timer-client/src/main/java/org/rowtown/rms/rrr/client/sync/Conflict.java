package org.rowtown.rms.rrr.client.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.client.model.LocalDocument;

import java.time.LocalDateTime;

/**
 * Represents a conflict between a local document version and a server document version.
 *
 * <p>Conflicts occur when:
 * <ul>
 *   <li>A local modification was made while offline</li>
 *   <li>The server version changed in the meantime</li>
 *   <li>Both versions have different content that cannot be automatically merged</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conflict {

    /**
     * The document ID where the conflict occurred
     */
    private Long documentId;

    /**
     * The type of document (e.g., "STARTLIST", "RESULTS")
     */
    private String documentType;

    /**
     * The regatta ID
     */
    private String regattaId;

    /**
     * The timer ID (for results documents)
     */
    private String timerId;

    /**
     * The local version of the document (client's modified version)
     */
    private LocalDocument localVersion;

    /**
     * The server version of the document (latest from server)
     */
    private LocalDocument serverVersion;

    /**
     * The common ancestor version (last synced version before divergence)
     * May be null if no common ancestor is available
     */
    private LocalDocument baseVersion;

    /**
     * When the conflict was detected
     */
    private LocalDateTime detectedAt;

    /**
     * The operation that triggered the conflict detection
     */
    private ConflictTrigger trigger;

    /**
     * Additional context about the conflict
     */
    private String description;

    /**
     * Describes what operation triggered the conflict detection
     */
    public enum ConflictTrigger {
        /** Conflict detected during offline operation replay */
        OFFLINE_REPLAY,

        /** Conflict detected during incoming notification sync */
        INCOMING_UPDATE,

        /** Conflict detected during manual sync operation */
        MANUAL_SYNC,

        /** Conflict detected during background sync */
        BACKGROUND_SYNC
    }
}
