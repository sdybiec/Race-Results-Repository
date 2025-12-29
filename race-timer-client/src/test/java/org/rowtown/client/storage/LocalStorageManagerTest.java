package org.rowtown.client.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rowtown.client.model.LocalDocument;
import org.rowtown.client.model.SyncStatus;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocalStorageManager.
 */
class LocalStorageManagerTest {

    @TempDir
    Path tempDir;

    private LocalStorageManager storage;
    private String dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db").toString();
        storage = new LocalStorageManager(dbPath);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void testSaveAndFindDocument() {
        // Create test document
        LocalDocument doc = LocalDocument.builder()
            .regattaId("TEST2025")
            .timerId("timer001")
            .milestoneId("finish")
            .documentType("RACE_RESULTS")
            .versionType("primary")
            .author("test@example.com")
            .description("Test document")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .localVersion(1L)
            .syncStatus(SyncStatus.PENDING)
            .modelData("test data".getBytes())
            .serializationFormat("JSON")
            .build();

        // Save document
        LocalDocument saved = storage.save(doc);

        // Verify it was saved
        assertNotNull(saved.getLocalId());
        assertTrue(saved.getLocalId() > 0);

        // Find by local ID
        Optional<LocalDocument> found = storage.findById(saved.getLocalId());
        assertTrue(found.isPresent());
        assertEquals("TEST2025", found.get().getRegattaId());
        assertEquals("timer001", found.get().getTimerId());
        assertEquals(SyncStatus.PENDING, found.get().getSyncStatus());
    }

    @Test
    void testUpdateDocument() {
        // Create and save document
        LocalDocument doc = LocalDocument.builder()
            .regattaId("TEST2025")
            .documentType("START_LIST")
            .author("admin@example.com")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .localVersion(1L)
            .syncStatus(SyncStatus.PENDING)
            .modelData("initial".getBytes())
            .serializationFormat("JSON")
            .build();

        LocalDocument saved = storage.save(doc);
        Long localId = saved.getLocalId();

        // Update document
        saved.setModelData("updated".getBytes());
        saved.setLocalVersion(2L);
        saved.setSyncStatus(SyncStatus.SYNCED);

        storage.save(saved);

        // Verify update
        Optional<LocalDocument> found = storage.findById(localId);
        assertTrue(found.isPresent());
        assertEquals("updated", new String(found.get().getModelData()));
        assertEquals(2L, found.get().getLocalVersion());
        assertEquals(SyncStatus.SYNCED, found.get().getSyncStatus());
    }

    @Test
    void testFindByRegattaAndType() {
        // Create multiple documents
        storage.save(createTestDocument("REGATTA1", "START_LIST"));
        storage.save(createTestDocument("REGATTA1", "RACE_RESULTS"));
        storage.save(createTestDocument("REGATTA2", "START_LIST"));

        // Find by regatta and type
        List<LocalDocument> results = storage.findByRegattaAndType("REGATTA1", "RACE_RESULTS");

        assertEquals(1, results.size());
        assertEquals("REGATTA1", results.get(0).getRegattaId());
        assertEquals("RACE_RESULTS", results.get(0).getDocumentType());
    }

    @Test
    void testFindPendingSync() {
        // Create documents with different sync statuses
        storage.save(createTestDocumentWithStatus(SyncStatus.PENDING));
        storage.save(createTestDocumentWithStatus(SyncStatus.SYNCED));
        storage.save(createTestDocumentWithStatus(SyncStatus.FAILED));
        storage.save(createTestDocumentWithStatus(SyncStatus.PENDING));

        // Find pending
        List<LocalDocument> pending = storage.findPendingSync();

        assertEquals(3, pending.size()); // PENDING and FAILED
    }

    @Test
    void testDelete() {
        // Create and save document
        LocalDocument doc = createTestDocument("TEST", "START_LIST");
        LocalDocument saved = storage.save(doc);
        Long localId = saved.getLocalId();

        // Verify it exists
        assertTrue(storage.findById(localId).isPresent());

        // Delete it
        storage.delete(localId);

        // Verify it's gone
        assertFalse(storage.findById(localId).isPresent());
    }

    @Test
    void testFindByServerId() {
        // Create document with server ID
        LocalDocument doc = createTestDocument("TEST", "START_LIST");
        doc.setServerId(123L);
        storage.save(doc);

        // Find by server ID
        Optional<LocalDocument> found = storage.findByServerId(123L);

        assertTrue(found.isPresent());
        assertEquals(123L, found.get().getServerId());
    }

    @Test
    void testTransactions() throws Exception {
        storage.beginTransaction();

        try {
            // Create document in transaction
            LocalDocument doc = createTestDocument("TEST", "START_LIST");
            storage.save(doc);

            // Commit transaction
            storage.commitTransaction();

            // Verify document was saved
            List<LocalDocument> results = storage.findByRegattaAndType("TEST", "START_LIST");
            assertEquals(1, results.size());

        } catch (Exception e) {
            storage.rollbackTransaction();
            fail("Transaction failed");
        }
    }

    private LocalDocument createTestDocument(String regattaId, String type) {
        return LocalDocument.builder()
            .regattaId(regattaId)
            .documentType(type)
            .author("test@example.com")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .localVersion(1L)
            .syncStatus(SyncStatus.PENDING)
            .modelData("test".getBytes())
            .serializationFormat("JSON")
            .build();
    }

    private LocalDocument createTestDocumentWithStatus(SyncStatus status) {
        LocalDocument doc = createTestDocument("TEST", "RACE_RESULTS");
        doc.setSyncStatus(status);
        return doc;
    }
}
