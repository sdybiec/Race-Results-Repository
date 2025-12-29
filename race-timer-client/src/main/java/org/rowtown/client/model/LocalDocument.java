package org.rowtown.client.model;

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
    private String timerId;
    private String milestoneId;
    private String documentType; // START_LIST or RACE_RESULTS
    private String versionType;

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
