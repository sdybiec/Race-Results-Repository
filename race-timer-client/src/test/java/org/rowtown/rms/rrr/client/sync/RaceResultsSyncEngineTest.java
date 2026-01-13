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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RaceResultsSyncEngine.
 */
@ExtendWith(MockitoExtension.class)
class RaceResultsSyncEngineTest {

    @Mock
    private LocalStorageManager storage;

    @Mock
    private RepositoryClient apiClient;

    private RaceResultsSyncEngine syncEngine;
    private static final String REGATTA_ID = "TEST2025";
    private static final String TIMER_ID = "timer001";

    @BeforeEach
    void setUp() {
        syncEngine = new RaceResultsSyncEngine(storage, apiClient, REGATTA_ID, TIMER_ID);
    }

    @Test
    void saveLocal_CreatesNewDocument() {
        // Arrange
        byte[] modelData = "race results data".getBytes();
        LocalDocument savedDoc = LocalDocument.builder()
            .localId(1L)
            .regattaId(REGATTA_ID)
            .timerId(TIMER_ID)
            .milestoneId("finish")
            .syncStatus(SyncStatus.PENDING)
            .build();

        when(storage.save(any())).thenReturn(savedDoc);

        // Act
        LocalDocument result = syncEngine.saveLocal("finish", "primary", "author", modelData);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getLocalId());
        verify(storage, times(1)).save(argThat(doc ->
            doc.getMilestoneId().equals("finish") &&
            doc.getVersionType().equals("primary") &&
            doc.getSyncStatus() == SyncStatus.PENDING
        ));
    }

    @Test
    void updateLocal_UpdatesExistingDocument() {
        // Arrange
        Long localId = 1L;
        byte[] newData = "updated data".getBytes();

        LocalDocument existingDoc = LocalDocument.builder()
            .localId(localId)
            .localVersion(1L)
            .syncStatus(SyncStatus.SYNCED)
            .build();

        when(storage.findById(localId)).thenReturn(Optional.of(existingDoc));
        when(storage.save(any())).thenReturn(existingDoc);

        // Act
        LocalDocument result = syncEngine.updateLocal(localId, newData);

        // Assert
        assertNotNull(result);
        verify(storage).save(argThat(doc ->
            doc.getLocalVersion().equals(2L) &&
            doc.getSyncStatus() == SyncStatus.PENDING
        ));
    }

    @Test
    void updateLocal_DocumentNotFound_ThrowsException() {
        // Arrange
        when(storage.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            syncEngine.updateLocal(999L, new byte[0])
        );
    }

    @Test
    void synchronizePending_ServerNotReachable_ReturnsOfflineResult() {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(false);

        // Act
        RaceResultsSyncEngine.SyncResult result = syncEngine.synchronizePending();

        // Assert
        assertTrue(result.offline);
        assertEquals(0, result.totalDocuments);
        verify(storage, never()).save(any());
    }

    @Test
    void synchronizePending_NoPendingDocuments_ReturnsEmptyResult() {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);
        when(storage.findPendingSync()).thenReturn(List.of());

        // Act
        RaceResultsSyncEngine.SyncResult result = syncEngine.synchronizePending();

        // Assert
        assertFalse(result.offline);
        assertEquals(0, result.totalDocuments);
        assertEquals(0, result.syncedDocuments);
        assertEquals(0, result.failedDocuments);
    }

    @Test
    void synchronizePending_CreateNewOnServer_Success() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        LocalDocument localDoc = LocalDocument.builder()
            .localId(1L)
            .regattaId(REGATTA_ID)
            .timerId(TIMER_ID)
            .milestoneId("finish")
            .documentType("RACE_RESULTS")
            .versionType("primary")
            .author("author")
            .syncStatus(SyncStatus.PENDING)
            .modelData("data".getBytes())
            .build();

        when(storage.findPendingSync()).thenReturn(List.of(localDoc));

        RepositoryClient.DocumentResponse serverResponse = new RepositoryClient.DocumentResponse();
        serverResponse.documentId = 100L;
        serverResponse.latestVersion = 1L;

        when(apiClient.createDocument(any())).thenReturn(serverResponse);
        when(storage.save(any())).thenReturn(localDoc);

        // Act
        RaceResultsSyncEngine.SyncResult result = syncEngine.synchronizePending();

        // Assert
        assertEquals(1, result.totalDocuments);
        assertEquals(1, result.syncedDocuments);
        assertEquals(0, result.failedDocuments);
        assertTrue(result.isSuccess());

        verify(apiClient).createDocument(any());
        verify(storage, atLeast(2)).save(argThat(doc ->
            doc.getSyncStatus() == SyncStatus.SYNCING ||
            (doc.getSyncStatus() == SyncStatus.SYNCED && doc.getServerId() != null)
        ));
    }

    @Test
    void synchronizePending_UpdateExistingOnServer_Success() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        LocalDocument localDoc = LocalDocument.builder()
            .localId(1L)
            .serverId(100L) // Already has server ID
            .regattaId(REGATTA_ID)
            .timerId(TIMER_ID)
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.PENDING)
            .modelData("updated data".getBytes())
            .build();

        when(storage.findPendingSync()).thenReturn(List.of(localDoc));

        RepositoryClient.DocumentResponse serverResponse = new RepositoryClient.DocumentResponse();
        serverResponse.documentId = 100L;
        serverResponse.latestVersion = 2L;

        when(apiClient.updateDocument(eq(100L), any(), any())).thenReturn(serverResponse);
        when(storage.save(any())).thenReturn(localDoc);

        // Act
        RaceResultsSyncEngine.SyncResult result = syncEngine.synchronizePending();

        // Assert
        assertEquals(1, result.totalDocuments);
        assertEquals(1, result.syncedDocuments);
        assertEquals(0, result.failedDocuments);

        verify(apiClient).updateDocument(eq(100L), any(), any());
        verify(storage, atLeast(2)).save(argThat(doc ->
            doc.getSyncStatus() == SyncStatus.SYNCING ||
            doc.getSyncStatus() == SyncStatus.SYNCED
        ));
    }

    @Test
    void synchronizePending_SyncFails_MarksAsFailed() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        LocalDocument localDoc = LocalDocument.builder()
            .localId(1L)
            .regattaId(REGATTA_ID)
            .timerId(TIMER_ID)
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.PENDING)
            .retryCount(0)
            .build();

        when(storage.findPendingSync()).thenReturn(List.of(localDoc));
        when(apiClient.createDocument(any())).thenThrow(new IOException("Network error"));
        when(storage.save(any())).thenReturn(localDoc);

        // Act
        RaceResultsSyncEngine.SyncResult result = syncEngine.synchronizePending();

        // Assert
        assertEquals(1, result.totalDocuments);
        assertEquals(0, result.syncedDocuments);
        assertEquals(1, result.failedDocuments);
        assertFalse(result.isSuccess());

        verify(storage, atLeast(2)).save(argThat(doc ->
            doc.getSyncStatus() == SyncStatus.SYNCING ||
            (doc.getSyncStatus() == SyncStatus.FAILED && doc.getRetryCount() > 0)
        ));
    }

    @Test
    void synchronizePending_MultipleDocuments_MixedResults() throws IOException {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        LocalDocument doc1 = LocalDocument.builder()
            .localId(1L)
            .regattaId(REGATTA_ID)
            .timerId(TIMER_ID)
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.PENDING)
            .build();

        LocalDocument doc2 = LocalDocument.builder()
            .localId(2L)
            .regattaId(REGATTA_ID)
            .timerId(TIMER_ID)
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.PENDING)
            .build();

        when(storage.findPendingSync()).thenReturn(List.of(doc1, doc2));

        RepositoryClient.DocumentResponse successResponse = new RepositoryClient.DocumentResponse();
        successResponse.documentId = 100L;
        successResponse.latestVersion = 1L;

        // First succeeds, second fails
        when(apiClient.createDocument(any()))
            .thenReturn(successResponse)
            .thenThrow(new IOException("Network error"));

        when(storage.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RaceResultsSyncEngine.SyncResult result = syncEngine.synchronizePending();

        // Assert
        assertEquals(2, result.totalDocuments);
        assertEquals(1, result.syncedDocuments);
        assertEquals(1, result.failedDocuments);
        assertFalse(result.isSuccess());
    }

    @Test
    void synchronizePending_FiltersOtherTimers() {
        // Arrange
        when(apiClient.isServerReachable()).thenReturn(true);

        LocalDocument ourDoc = LocalDocument.builder()
            .localId(1L)
            .regattaId(REGATTA_ID)
            .timerId(TIMER_ID)
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.PENDING)
            .build();

        LocalDocument otherTimerDoc = LocalDocument.builder()
            .localId(2L)
            .regattaId(REGATTA_ID)
            .timerId("timer002")
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.PENDING)
            .build();

        when(storage.findPendingSync()).thenReturn(List.of(ourDoc, otherTimerDoc));

        // Act
        RaceResultsSyncEngine.SyncResult result = syncEngine.synchronizePending();

        // Assert
        assertEquals(1, result.totalDocuments); // Only our timer's doc
    }

    @Test
    void getPendingCount_ReturnsCorrectCount() {
        // Arrange
        LocalDocument pending1 = LocalDocument.builder()
            .timerId(TIMER_ID)
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.PENDING)
            .build();

        LocalDocument pending2 = LocalDocument.builder()
            .timerId(TIMER_ID)
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.FAILED)
            .build();

        LocalDocument otherTimer = LocalDocument.builder()
            .timerId("other")
            .documentType("RACE_RESULTS")
            .syncStatus(SyncStatus.PENDING)
            .build();

        when(storage.findPendingSync()).thenReturn(List.of(pending1, pending2, otherTimer));

        // Act
        int count = syncEngine.getPendingCount();

        // Assert
        assertEquals(2, count); // Only this timer's pending docs
    }

    @Test
    void getAllLocal_ReturnsOnlyThisTimersDocuments() {
        // Arrange
        LocalDocument ourDoc1 = LocalDocument.builder()
            .timerId(TIMER_ID)
            .build();

        LocalDocument ourDoc2 = LocalDocument.builder()
            .timerId(TIMER_ID)
            .build();

        LocalDocument otherDoc = LocalDocument.builder()
            .timerId("other")
            .build();

        when(storage.findByRegattaAndType(REGATTA_ID, "RACE_RESULTS"))
            .thenReturn(List.of(ourDoc1, ourDoc2, otherDoc));

        // Act
        List<LocalDocument> result = syncEngine.getAllLocal();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(doc -> TIMER_ID.equals(doc.getTimerId())));
    }
}
