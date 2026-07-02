package org.rowtown.rms.rrr.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Local representation of a document with synchronization metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalDocument {
    // Server identifiers
    private Long serverId;
    private String regattaId;
    // Regatta start date (ISO-8601, yyyy-MM-dd); part of the regatta key.
    private String regattaStartDate;
    private String documentType; // START_LIST or RACE_RESULTS

    // Race Results key components
    private String raceId;       // derived from the model
    private String milestoneId;
    private String timer;        // PRIMARY | FIRST_BACKUP | SECOND_BACKUP

    // Document metadata
    private String author;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    // Version tracking
    private Long localVersion;
    private Long serverVersion;

    // Sync metadata
    private SyncStatus syncStatus;
    private LocalDateTime lastSyncedAt;
    private String lastSyncError;
    private Integer retryCount;

    // Document data (serialized EMF model)
    private byte[] modelData;
    private String serializationFormat;

    // Local tracking
    private Long localId;
    private LocalDateTime localCreatedAt;
}
