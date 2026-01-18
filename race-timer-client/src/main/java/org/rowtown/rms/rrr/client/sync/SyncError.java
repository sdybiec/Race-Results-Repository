package org.rowtown.rms.rrr.client.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an error that occurred during document synchronization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncError {

    /**
     * Type of error that occurred.
     */
    private ErrorType errorType;

    /**
     * Human-readable error message.
     */
    private String message;

    /**
     * Document ID that failed to sync (may be null for connection errors).
     */
    private Long documentId;

    /**
     * Regatta ID (may be null for connection errors).
     */
    private String regattaId;

    /**
     * The underlying exception that caused the error (may be null).
     */
    private Throwable cause;

    /**
     * Timestamp when the error occurred.
     */
    private LocalDateTime timestamp;

    /**
     * Number of retry attempts so far.
     */
    private int retryCount;

    /**
     * Whether the operation will be retried automatically.
     */
    private boolean willRetry;

    /**
     * Error type enumeration.
     */
    public enum ErrorType {
        /**
         * Failed to connect to MQTT broker.
         */
        MQTT_CONNECTION_FAILED,

        /**
         * MQTT connection was lost.
         */
        MQTT_DISCONNECTED,

        /**
         * Failed to fetch document from server.
         */
        DOCUMENT_FETCH_FAILED,

        /**
         * Failed to parse notification message.
         */
        NOTIFICATION_PARSE_ERROR,

        /**
         * Failed to save document to local storage.
         */
        LOCAL_STORAGE_ERROR,

        /**
         * Network error communicating with server.
         */
        NETWORK_ERROR,

        /**
         * Server returned an error response.
         */
        SERVER_ERROR,

        /**
         * Document not found on server (may have been deleted).
         */
        DOCUMENT_NOT_FOUND,

        /**
         * General synchronization error.
         */
        SYNC_ERROR
    }
}
