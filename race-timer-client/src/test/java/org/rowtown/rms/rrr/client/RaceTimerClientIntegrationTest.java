package org.rowtown.rms.rrr.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.model.SyncStatus;
import org.rowtown.rms.rrr.client.sync.RaceResultsSyncEngine;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test for RaceTimerClient.
 * Tests the complete workflow with a real SQLite database.
 */
@ExtendWith(MockitoExtension.class)
class RaceTimerClientIntegrationTest {

    @Mock
    private RepositoryClient mockApiClient;

    private RaceTimerClient client;
    private String testDbPath;

    private static final String REGATTA_ID = "INTEGRATION_TEST_2025";
    private static final String TIMER_ID = "timer001";

    @BeforeEach
    void setUp() {
        // Use temporary database for testing
        testDbPath = "target/test-db-" + System.currentTimeMillis() + ".db";

        // We'll inject the mock API client after construction
        // For now, create a config
        RaceTimerClient.ClientConfig config = new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            TIMER_ID,
            "http://localhost:8080",
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );

        // Note: In a real integration test, we'd need dependency injection
        // For this test, we'll work with the real client but test offline scenarios
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }

        // Clean up test database
        File dbFile = new File(testDbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    void offlineWorkflow_SaveAndRetrieveRaceResults() throws IOException {
        // This test uses a real client with mocked server interactions
        // to verify offline-first behavior

        // Arrange
        RaceTimerClient.ClientConfig config = new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            TIMER_ID,
            "http://localhost:8080", // Server won't be reachable
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );

        client = new RaceTimerClient(config);

        // Act 1: Save race results while offline
        byte[] resultsData = "finish line results".getBytes();
        LocalDocument saved = client.saveRaceResults(
            "finish",
            "primary",
            "timer001",
            resultsData
        );

        // Assert 1: Document saved locally
        assertNotNull(saved);
        assertNotNull(saved.getLocalId());
        assertEquals("finish", saved.getMilestoneId());
        assertEquals(SyncStatus.PENDING, saved.getSyncStatus());

        // Act 2: Retrieve all race results
        List<LocalDocument> allResults = client.getAllRaceResults();

        // Assert 2: Can retrieve saved document
        assertEquals(1, allResults.size());
        assertEquals(saved.getLocalId(), allResults.get(0).getLocalId());

        // Act 3: Update the race results
        byte[] updatedData = "updated finish line results".getBytes();
        LocalDocument updated = client.updateRaceResults(saved.getLocalId(), updatedData);

        // Assert 3: Document updated locally
        assertEquals(2L, updated.getLocalVersion());
        assertEquals(SyncStatus.PENDING, updated.getSyncStatus());

        // Act 4: Check pending count
        int pendingCount = client.getPendingRaceResultsCount();

        // Assert 4: One document pending sync
        assertEquals(1, pendingCount);
    }

    @Test
    void multipleDocuments_OfflineStorage() {
        // Arrange
        RaceTimerClient.ClientConfig config = new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            TIMER_ID,
            "http://localhost:8080",
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );

        client = new RaceTimerClient(config);

        // Act: Save multiple race results for different milestones
        LocalDocument finish = client.saveRaceResults(
            "finish",
            "primary",
            "timer001",
            "finish data".getBytes()
        );

        LocalDocument halfway = client.saveRaceResults(
            "halfway",
            "primary",
            "timer001",
            "halfway data".getBytes()
        );

        LocalDocument start = client.saveRaceResults(
            "start",
            "primary",
            "timer001",
            "start data".getBytes()
        );

        // Assert: All documents saved
        List<LocalDocument> allResults = client.getAllRaceResults();
        assertEquals(3, allResults.size());

        // Verify each milestone
        assertTrue(allResults.stream()
            .anyMatch(doc -> "finish".equals(doc.getMilestoneId())));
        assertTrue(allResults.stream()
            .anyMatch(doc -> "halfway".equals(doc.getMilestoneId())));
        assertTrue(allResults.stream()
            .anyMatch(doc -> "start".equals(doc.getMilestoneId())));

        // All should be pending
        assertEquals(3, client.getPendingRaceResultsCount());
    }

    @Test
    void clientStatus_OfflineMode() {
        // Arrange
        RaceTimerClient.ClientConfig config = new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            TIMER_ID,
            "http://localhost:8080",
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );

        client = new RaceTimerClient(config);

        // Save some pending results
        client.saveRaceResults("finish", "primary", "timer001", "data".getBytes());
        client.saveRaceResults("start", "primary", "timer001", "data".getBytes());

        // Act
        RaceTimerClient.ClientStatus status = client.getStatus();

        // Assert
        assertNotNull(status);
        assertFalse(status.online); // Server not reachable
        assertFalse(status.mqttConnected); // MQTT not reachable
        assertEquals(2, status.pendingRaceResults);
        assertTrue(status.startListNeedsSync); // No Start List downloaded
    }

    @Test
    void versionType_DifferentBackups() {
        // Arrange
        RaceTimerClient.ClientConfig config = new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            TIMER_ID,
            "http://localhost:8080",
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );

        client = new RaceTimerClient(config);

        // Act: Save different version types for same milestone
        LocalDocument primary = client.saveRaceResults(
            "finish",
            "primary",
            "timer001",
            "primary data".getBytes()
        );

        LocalDocument firstBackup = client.saveRaceResults(
            "finish",
            "firstBackup",
            "timer002",
            "backup1 data".getBytes()
        );

        LocalDocument secondBackup = client.saveRaceResults(
            "finish",
            "secondBackup",
            "timer003",
            "backup2 data".getBytes()
        );

        // Assert: All three versions saved
        List<LocalDocument> allResults = client.getAllRaceResults();
        assertEquals(3, allResults.size());

        // Verify version types
        assertTrue(allResults.stream()
            .anyMatch(doc -> "primary".equals(doc.getVersionType())));
        assertTrue(allResults.stream()
            .anyMatch(doc -> "firstBackup".equals(doc.getVersionType())));
        assertTrue(allResults.stream()
            .anyMatch(doc -> "secondBackup".equals(doc.getVersionType())));
    }

    @Test
    void synchronization_FailsGracefully_WhenOffline() {
        // Arrange
        RaceTimerClient.ClientConfig config = new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            TIMER_ID,
            "http://localhost:8080",
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );

        client = new RaceTimerClient(config);

        // Save a document
        client.saveRaceResults("finish", "primary", "timer001", "data".getBytes());

        // Act: Try to sync while offline
        RaceResultsSyncEngine.SyncResult result = client.syncRaceResults();

        // Assert: Should indicate offline status
        assertTrue(result.offline);
        assertEquals(0, result.syncedDocuments);

        // Document should still be pending
        assertEquals(1, client.getPendingRaceResultsCount());
    }

    @Test
    void closeClient_CleansUpResources() {
        // Arrange
        RaceTimerClient.ClientConfig config = new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            TIMER_ID,
            "http://localhost:8080",
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );

        client = new RaceTimerClient(config);

        // Save some data
        client.saveRaceResults("finish", "primary", "timer001", "data".getBytes());

        // Act: Close client
        client.close();

        // Assert: Database file should exist (data persisted)
        File dbFile = new File(testDbPath);
        assertTrue(dbFile.exists());

        // Subsequent operations should fail or handle gracefully
        // (In production code, we'd add state checking)
    }

    @Test
    void isOnline_ReturnsFalse_WhenServerUnreachable() {
        // Arrange
        RaceTimerClient.ClientConfig config = new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            TIMER_ID,
            "http://localhost:9999", // Non-existent server
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );

        client = new RaceTimerClient(config);

        // Act
        boolean online = client.isOnline();

        // Assert
        assertFalse(online);
    }
}
