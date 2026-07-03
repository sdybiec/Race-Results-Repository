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
import org.rowtown.rms.rrr.client.testutil.SampleModels;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    private static final String START_DATE = "2025-05-17";
    private static final String TIMER = "PRIMARY";
    private static final String AUTHOR = "timer@example.com";

    private RaceTimerClient.ClientConfig config(String serverUrl) {
        return new RaceTimerClient.ClientConfig(
            REGATTA_ID,
            START_DATE,
            TIMER,
            serverUrl,
            "tcp://localhost:1883",
            testDbPath,
            "test-jwt-token"
        );
    }

    @BeforeEach
    void setUp() {
        // Use temporary database for testing
        testDbPath = "target/test-db-" + System.currentTimeMillis() + ".db";
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
        // This test uses a real client with an unreachable server to verify
        // offline-first behavior.
        client = new RaceTimerClient(config("http://localhost:8080"));

        // Act 1: Save race results while offline
        byte[] resultsData = SampleModels.raceResults("1a");
        LocalDocument saved = client.saveRaceResults("finish", AUTHOR, resultsData);

        // Assert 1: Document saved locally
        assertNotNull(saved);
        assertNotNull(saved.getLocalId());
        assertEquals("finish", saved.getMilestoneId());
        assertEquals(TIMER, saved.getTimer());
        assertEquals(SyncStatus.PENDING, saved.getSyncStatus());

        // Act 2: Retrieve all race results
        List<LocalDocument> allResults = client.getAllRaceResults();

        // Assert 2: Can retrieve saved document
        assertEquals(1, allResults.size());
        assertEquals(saved.getLocalId(), allResults.get(0).getLocalId());

        // Act 3: Update the race results
        byte[] updatedData = SampleModels.raceResults("1a");
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
        client = new RaceTimerClient(config("http://localhost:8080"));

        // Act: Save multiple race results for different milestones
        client.saveRaceResults("finish", AUTHOR, SampleModels.raceResults("1a"));
        client.saveRaceResults("halfway", AUTHOR, SampleModels.raceResults("1a"));
        client.saveRaceResults("start", AUTHOR, SampleModels.raceResults("1a"));

        // Assert: All documents saved
        List<LocalDocument> allResults = client.getAllRaceResults();
        assertEquals(3, allResults.size());

        // Verify each milestone
        assertTrue(allResults.stream().anyMatch(doc -> "finish".equals(doc.getMilestoneId())));
        assertTrue(allResults.stream().anyMatch(doc -> "halfway".equals(doc.getMilestoneId())));
        assertTrue(allResults.stream().anyMatch(doc -> "start".equals(doc.getMilestoneId())));

        // All should be pending
        assertEquals(3, client.getPendingRaceResultsCount());
    }

    @Test
    void clientStatus_OfflineMode() {
        client = new RaceTimerClient(config("http://localhost:8080"));

        // Save some pending results
        client.saveRaceResults("finish", AUTHOR, SampleModels.raceResults("1a"));
        client.saveRaceResults("start", AUTHOR, SampleModels.raceResults("1a"));

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
    void raceResults_CarryConfiguredTimerRole() {
        // A client instance is fixed to one timer role (see ClientConfig); every
        // document it saves carries that role. Different roles (FIRST_BACKUP,
        // SECOND_BACKUP) would be produced by separate client instances.
        client = new RaceTimerClient(config("http://localhost:8080"));

        client.saveRaceResults("finish", AUTHOR, SampleModels.raceResults("1a"));
        client.saveRaceResults("start", AUTHOR, SampleModels.raceResults("1a"));

        List<LocalDocument> allResults = client.getAllRaceResults();
        assertEquals(2, allResults.size());
        assertTrue(allResults.stream().allMatch(doc -> TIMER.equals(doc.getTimer())));
    }

    @Test
    void synchronization_FailsGracefully_WhenOffline() {
        client = new RaceTimerClient(config("http://localhost:8080"));

        // Save a document
        client.saveRaceResults("finish", AUTHOR, SampleModels.raceResults("1a"));

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
        client = new RaceTimerClient(config("http://localhost:8080"));

        // Save some data
        client.saveRaceResults("finish", AUTHOR, SampleModels.raceResults("1a"));

        // Act: Close client
        client.close();

        // Assert: Database file should exist (data persisted)
        File dbFile = new File(testDbPath);
        assertTrue(dbFile.exists());
    }

    @Test
    void saveRaceResults_RejectsUnloadableModel() {
        // Fail-fast: a model the generated TDI classes cannot load is rejected at
        // capture time rather than queued for a sync the server would reject.
        client = new RaceTimerClient(config("http://localhost:8080"));

        assertThrows(IllegalArgumentException.class,
            () -> client.saveRaceResults("finish", AUTHOR, "not a TDI model".getBytes()));

        assertEquals(0, client.getPendingRaceResultsCount());
    }

    @Test
    void isOnline_ReturnsFalse_WhenServerUnreachable() {
        client = new RaceTimerClient(config("http://localhost:9999"));

        // Act
        boolean online = client.isOnline();

        // Assert
        assertFalse(online);
    }
}
