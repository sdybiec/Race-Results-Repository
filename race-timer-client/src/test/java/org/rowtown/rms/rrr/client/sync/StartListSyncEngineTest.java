package org.rowtown.rms.rrr.client.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.model.SyncStatus;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StartListSyncEngine.
 */
@ExtendWith(MockitoExtension.class)
class StartListSyncEngineTest {

    @Mock
    private LocalStorageManager storage;

    @Mock
    private RepositoryClient apiClient;

    private StartListSyncEngine syncEngine;
    private static final String REGATTA_ID = "TEST2025";

    @BeforeEach
    void setUp() {
        syncEngine = new StartListSyncEngine(storage, apiClient, REGATTA_ID);
    }

    @Test
    void synchronize_ServerNotReachable_ReturnsFalse() {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(false);

        // Act
        boolean result = syncEngine.synchronize();

        // Assert
        assertFalse(result);
        verify(storage, never()).save(any());
    }

    @Test
    void synchronize_NoStartListOnServer_ReturnsFalse() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);
        when(apiClient.getStartList(REGATTA_ID)).thenReturn(null);

        // Act
        boolean result = syncEngine.synchronize();

        // Assert
        assertFalse(result);
        verify(storage, never()).save(any());
    }

    @Test
    void synchronize_CreateNewLocalStartList_Success() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        RepositoryClient.DocumentResponse serverDoc = new RepositoryClient.DocumentResponse();
        serverDoc.documentId = 1L;
        serverDoc.regattaId = REGATTA_ID;
        serverDoc.author = "admin@example.com";
        serverDoc.description = "Test start list";
        serverDoc.latestVersion = 1L;
        serverDoc.modelData = "start list data".getBytes();

        when(apiClient.getStartList(REGATTA_ID)).thenReturn(serverDoc);
        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(Collections.emptyList());

        // Act
        boolean result = syncEngine.synchronize();

        // Assert
        assertTrue(result);
        verify(storage, times(1)).save(argThat(doc ->
            doc.getServerId().equals(1L) &&
            doc.getServerVersion().equals(1L) &&
            doc.getSyncStatus() == SyncStatus.SYNCED
        ));
    }

    @Test
    void synchronize_UpdateExistingStartList_NewerVersion() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        // Existing local document
        LocalDocument localDoc = LocalDocument.builder()
            .localId(100L)
            .serverId(1L)
            .regattaId(REGATTA_ID)
            .documentType("START_LIST")
            .serverVersion(1L)
            .localVersion(1L)
            .syncStatus(SyncStatus.SYNCED)
            .modelData("old data".getBytes())
            .build();

        // Newer server document
        RepositoryClient.DocumentResponse serverDoc = new RepositoryClient.DocumentResponse();
        serverDoc.documentId = 1L;
        serverDoc.regattaId = REGATTA_ID;
        serverDoc.author = "admin@example.com";
        serverDoc.description = "Updated start list";
        serverDoc.latestVersion = 2L;
        serverDoc.modelData = "new data".getBytes();

        when(apiClient.getStartList(REGATTA_ID)).thenReturn(serverDoc);
        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(List.of(localDoc));

        // Act
        boolean result = syncEngine.synchronize();

        // Assert
        assertTrue(result);
        verify(storage, times(1)).save(argThat(doc ->
            doc.getServerVersion().equals(2L) &&
            doc.getLocalVersion().equals(2L) &&
            doc.getSyncStatus() == SyncStatus.SYNCED
        ));
    }

    @Test
    void synchronize_UpdateExistingStartList_SameVersion() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        LocalDocument localDoc = LocalDocument.builder()
            .localId(100L)
            .serverId(1L)
            .serverVersion(1L)
            .syncStatus(SyncStatus.SYNCED)
            .build();

        RepositoryClient.DocumentResponse serverDoc = new RepositoryClient.DocumentResponse();
        serverDoc.documentId = 1L;
        serverDoc.latestVersion = 1L;

        when(apiClient.getStartList(REGATTA_ID)).thenReturn(serverDoc);
        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(List.of(localDoc));

        // Act
        boolean result = syncEngine.synchronize();

        // Assert
        assertTrue(result);
        verify(storage, never()).save(any()); // No save needed, already up to date
    }

    @Test
    void synchronize_UpdateFails_MarkAsFailed() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        LocalDocument localDoc = LocalDocument.builder()
            .localId(100L)
            .serverId(1L)
            .serverVersion(1L)
            .retryCount(0)
            .build();

        RepositoryClient.DocumentResponse serverDoc = new RepositoryClient.DocumentResponse();
        serverDoc.documentId = 1L;
        serverDoc.latestVersion = 2L;
        serverDoc.modelData = "data".getBytes();

        when(apiClient.getStartList(REGATTA_ID)).thenReturn(serverDoc);
        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(List.of(localDoc));
        when(storage.save(any())).thenThrow(new RuntimeException("Database error"));

        // Act
        boolean result = syncEngine.synchronize();

        // Assert
        assertFalse(result);
        verify(storage, atLeast(1)).save(argThat(doc ->
            doc.getSyncStatus() == SyncStatus.FAILED &&
            doc.getRetryCount() > 0
        ));
    }

    @Test
    void needsSync_NoLocalCopy_ReturnsTrue() {
        // Arrange
        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(Collections.emptyList());

        // Act
        boolean result = syncEngine.needsSync();

        // Assert
        assertTrue(result);
    }

    @Test
    void needsSync_SyncFailed_ReturnsTrue() {
        // Arrange
        LocalDocument localDoc = LocalDocument.builder()
            .syncStatus(SyncStatus.FAILED)
            .build();

        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(List.of(localDoc));

        // Act
        boolean result = syncEngine.needsSync();

        // Assert
        assertTrue(result);
    }

    @Test
    void needsSync_Synced_ReturnsFalse() {
        // Arrange
        LocalDocument localDoc = LocalDocument.builder()
            .syncStatus(SyncStatus.SYNCED)
            .build();

        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(List.of(localDoc));

        // Act
        boolean result = syncEngine.needsSync();

        // Assert
        assertFalse(result);
    }

    @Test
    void getMinutesSinceLastSync_NoLocalDoc_ReturnsNull() {
        // Arrange
        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(Collections.emptyList());

        // Act
        Long result = syncEngine.getMinutesSinceLastSync();

        // Assert
        assertNull(result);
    }

    @Test
    void getMinutesSinceLastSync_WithLastSync_ReturnsMinutes() {
        // Arrange
        LocalDocument localDoc = LocalDocument.builder()
            .lastSyncedAt(LocalDateTime.now().minusMinutes(30))
            .build();

        when(storage.findByRegattaAndType(REGATTA_ID, "START_LIST"))
            .thenReturn(List.of(localDoc));

        // Act
        Long result = syncEngine.getMinutesSinceLastSync();

        // Assert
        assertNotNull(result);
        assertTrue(result >= 29 && result <= 31); // Allow 1 minute tolerance
    }

    @Test
    void forceRefresh_CallsSynchronize() {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(false);

        // Act
        boolean result = syncEngine.forceRefresh();

        // Assert
        assertFalse(result); // Server not reachable
        verify(apiClient).isServerReachable();
    }
}
