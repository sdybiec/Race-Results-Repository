package org.rowtown.rms.rrr.client.sync.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.client.model.LocalDocument;

import java.time.LocalDateTime;

/**
 * Represents an operation that was performed while offline and needs to be synchronized
 * with the server when the connection is restored.
 *
 * <p>Pending operations are queued locally and replayed in order when the client
 * reconnects to the server.
 *
 * <p><b>Operation Lifecycle:</b>
 * <ol>
 *   <li>PENDING - Operation queued, waiting for connectivity</li>
 *   <li>REPLAYING - Operation is being sent to server</li>
 *   <li>SUCCEEDED - Operation successfully synced to server</li>
 *   <li>FAILED - Operation failed, may be retried</li>
 *   <li>CONFLICT - Operation conflicts with server state, needs resolution</li>
 * </ol>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingOperation {

    /**
     * Unique identifier for this operation (for tracking and deduplication)
     */
    private String operationId;

    /**
     * The type of operation
     */
    private OperationType operationType;

    /**
     * The document affected by this operation
     */
    private LocalDocument document;

    /**
     * The document ID (may be temporary for creates)
     */
    private Long documentId;

    /**
     * The regatta ID
     */
    private String regattaId;

    /**
     * The timer ID (for results documents)
     */
    private String timerId;

    /**
     * When the operation was originally performed
     */
    private LocalDateTime performedAt;

    /**
     * When the operation was queued
     */
    private LocalDateTime queuedAt;

    /**
     * Current status of the operation
     */
    private OperationStatus status;

    /**
     * Number of times this operation has been attempted
     */
    private int attemptCount;

    /**
     * Last error message (if any)
     */
    private String lastError;

    /**
     * When the last attempt was made
     */
    private LocalDateTime lastAttemptAt;

    /**
     * Whether this operation depends on previous operations completing first
     */
    private boolean hasDependencies;

    /**
     * ID of operation this depends on (if any)
     */
    private String dependsOnOperationId;

    /**
     * Additional metadata about the operation (JSON)
     */
    private String metadata;

    /**
     * Types of operations that can be queued
     */
    public enum OperationType {
        /** Create a new document */
        CREATE,

        /** Update an existing document */
        UPDATE,

        /** Delete a document */
        DELETE,

        /** Upload a local modification */
        UPLOAD
    }

    /**
     * Status of a pending operation
     */
    public enum OperationStatus {
        /** Queued and waiting for connectivity */
        PENDING,

        /** Currently being replayed to server */
        REPLAYING,

        /** Successfully synced to server */
        SUCCEEDED,

        /** Failed to sync (will retry) */
        FAILED,

        /** Conflicts with server state (needs resolution) */
        CONFLICT,

        /** Cancelled by user or system */
        CANCELLED
    }

    /**
     * Checks if this operation is in a terminal state (won't be retried)
     */
    public boolean isTerminal() {
        return status == OperationStatus.SUCCEEDED ||
               status == OperationStatus.CANCELLED;
    }

    /**
     * Checks if this operation needs retry
     */
    public boolean needsRetry() {
        return status == OperationStatus.FAILED && attemptCount < 5;
    }

    /**
     * Checks if this operation is ready to replay
     * (pending and has no unresolved dependencies)
     */
    public boolean isReadyToReplay() {
        return status == OperationStatus.PENDING && !hasDependencies;
    }

    /**
     * Records a failed attempt
     */
    public void recordFailure(String error) {
        this.status = OperationStatus.FAILED;
        this.lastError = error;
        this.lastAttemptAt = LocalDateTime.now();
        this.attemptCount++;
    }

    /**
     * Records a successful completion
     */
    public void recordSuccess() {
        this.status = OperationStatus.SUCCEEDED;
        this.lastAttemptAt = LocalDateTime.now();
        this.lastError = null;
    }

    /**
     * Records a conflict
     */
    public void recordConflict(String conflictDetails) {
        this.status = OperationStatus.CONFLICT;
        this.lastError = conflictDetails;
        this.lastAttemptAt = LocalDateTime.now();
    }

    /**
     * Marks operation as replaying
     */
    public void markReplaying() {
        this.status = OperationStatus.REPLAYING;
        this.lastAttemptAt = LocalDateTime.now();
    }
}
