package org.rowtown.rms.rrr.client.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.client.model.LocalDocument;

import java.time.LocalDateTime;

/**
 * Event representing a document change notification.
 * Contains both the notification metadata and the synchronized document.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEvent {

    /**
     * Type of event that occurred.
     */
    private EventType eventType;

    /**
     * Document ID on the server.
     */
    private Long documentId;

    /**
     * Type of document (START_LIST, RACE_RESULTS, etc.).
     */
    private String documentType;

    /**
     * Regatta ID this document belongs to.
     */
    private String regattaId;

    /**
     * Timer ID (for race results).
     */
    private String timerId;

    /**
     * Version number that was created/updated.
     */
    private Long versionNumber;

    /**
     * Author who made the change.
     */
    private String author;

    /**
     * Description of the change.
     */
    private String changeDescription;

    /**
     * Timestamp when the event occurred.
     */
    private LocalDateTime timestamp;

    /**
     * The synchronized document (already fetched from server and cached locally).
     * Will be null if the fetch failed or for delete events.
     */
    private LocalDocument document;

    /**
     * Indicates whether the document was successfully synchronized.
     */
    private boolean syncSuccessful;

    /**
     * Event type enumeration.
     */
    public enum EventType {
        /**
         * A new document was created.
         */
        DOCUMENT_CREATED,

        /**
         * A new version of an existing document was created (document updated).
         */
        VERSION_CREATED,

        /**
         * A specific field in the document changed.
         */
        FIELD_CHANGED,

        /**
         * A document was deleted.
         */
        DOCUMENT_DELETED
    }
}
