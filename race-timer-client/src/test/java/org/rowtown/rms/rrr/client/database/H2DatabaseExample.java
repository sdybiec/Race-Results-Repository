package org.rowtown.rms.rrr.client.database;

import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;
import org.rowtown.rms.rrr.client.sync.*;
import org.rowtown.rms.rrr.client.sync.conflict.LastWriteWinsResolver;
import org.rowtown.rms.rrr.client.sync.queue.OfflineOperationQueue;
import org.rowtown.rms.rrr.client.sync.queue.PendingOperation;

/**
 * Comprehensive examples demonstrating H2 database usage with the Race Timer Client.
 *
 * <p>This example shows:</p>
 * <ul>
 *   <li>Using H2 instead of SQLite for local storage</li>
 *   <li>In-memory database for testing</li>
 *   <li>File-based H2 for production</li>
 *   <li>Database adapter features</li>
 * </ul>
 */
public class H2DatabaseExample {

    public static void main(String[] args) throws Exception {
        // ===== EXAMPLE 1: H2 File-Based Database =====
        System.out.println("\n===== Example 1: H2 File-Based Database =====");

        // Create storage manager with H2 database
        LocalStorageManager h2Storage = new LocalStorageManager(
            "./race_timer_h2",
            DatabaseType.H2
        );

        System.out.println("✓ Created H2 file-based storage");
        System.out.println("  Location: ./race_timer_h2.mv.db");
        System.out.println("  Benefits: Better write performance, native BOOLEAN, advanced SQL");

        // Use with synchronization service
        String serverUrl = "https://repository.example.com";
        String jwtToken = "your-jwt-token";
        String mqttBrokerUrl = "tcp://repository.example.com:1883";
        String clientId = "timer-client-h2-001";

        RepositoryClient repositoryClient = new RepositoryClient(serverUrl, jwtToken);

        DocumentSynchronizationService h2SyncService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(h2Storage)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId)
            .conflictResolver(new LastWriteWinsResolver())
            .enableOfflineQueue(true)
            .build();

        System.out.println("✓ Created sync service with H2 storage");

        // ===== EXAMPLE 2: H2 In-Memory Database =====
        System.out.println("\n===== Example 2: H2 In-Memory Database (Testing) =====");

        // Create in-memory storage for testing
        DatabaseAdapter h2Adapter = DatabaseAdapter.forType(DatabaseType.H2);
        var memoryConnection = h2Adapter.connectInMemory("test_db");

        System.out.println("✓ Created H2 in-memory database");
        System.out.println("  Use case: Unit tests, integration tests, temporary data");
        System.out.println("  Benefits: Ultra-fast, no disk I/O, auto-cleanup");

        // You can use in-memory database for testing without persistence
        // Perfect for CI/CD pipelines and automated tests

        // ===== EXAMPLE 3: Database Type Comparison =====
        System.out.println("\n===== Example 3: Database Type Comparison =====");

        // SQLite storage
        LocalStorageManager sqliteStorage = new LocalStorageManager(
            "./race_timer_sqlite.db",
            DatabaseType.SQLITE
        );

        // H2 storage
        LocalStorageManager h2StorageCompare = new LocalStorageManager(
            "./race_timer_h2_compare",
            DatabaseType.H2
        );

        System.out.println("Storage Comparison:");
        System.out.println("┌─────────────────┬──────────────┬──────────────┐");
        System.out.println("│ Feature         │ SQLite       │ H2           │");
        System.out.println("├─────────────────┼──────────────┼──────────────┤");
        System.out.println("│ File-based      │ Yes          │ Yes          │");
        System.out.println("│ In-memory       │ Limited      │ Yes          │");
        System.out.println("│ Pure Java       │ No (native)  │ Yes          │");
        System.out.println("│ Write perf      │ Good         │ Excellent    │");
        System.out.println("│ Read perf       │ Excellent    │ Excellent    │");
        System.out.println("│ BOOLEAN type    │ No (INT)     │ Yes          │");
        System.out.println("│ Stored procs    │ No           │ Yes          │");
        System.out.println("│ Footprint       │ Small        │ Medium       │");
        System.out.println("└─────────────────┴──────────────┴──────────────┘");

        // ===== EXAMPLE 4: Using H2 with Offline Queue =====
        System.out.println("\n===== Example 4: H2 Offline Operation Queue =====");

        // Create offline queue with H2 database
        OfflineOperationQueue h2Queue = new OfflineOperationQueue(
            "./offline_operations_h2",
            DatabaseType.H2
        );

        h2Queue.initialize();
        System.out.println("✓ Created H2-backed offline operation queue");
        System.out.println("  Benefits: Faster writes, better transaction support");

        // Queue some operations
        PendingOperation operation = PendingOperation.builder()
            .operationType(PendingOperation.OperationType.UPDATE)
            .documentId(12345L)
            .regattaId("CHARLES_REGATTA_2026")
            .status(PendingOperation.OperationStatus.PENDING)
            .performedAt(java.time.LocalDateTime.now())
            .build();

        h2Queue.enqueue(operation);
        System.out.println("✓ Queued operation to H2 database");

        int queueSize = h2Queue.getQueueSize();
        System.out.println("  Queue size: " + queueSize);

        // ===== EXAMPLE 5: Database Adapter Features =====
        System.out.println("\n===== Example 5: Database Adapter Features =====");

        DatabaseAdapter sqliteAdapter = DatabaseAdapter.forType(DatabaseType.SQLITE);
        DatabaseAdapter h2AdapterExample = DatabaseAdapter.forType(DatabaseType.H2);

        System.out.println("Feature Support:");
        System.out.println("");
        System.out.println("In-Memory:");
        System.out.println("  SQLite: " + sqliteAdapter.supportsFeature(DatabaseAdapter.DatabaseFeature.IN_MEMORY));
        System.out.println("  H2:     " + h2AdapterExample.supportsFeature(DatabaseAdapter.DatabaseFeature.IN_MEMORY));

        System.out.println("Native BOOLEAN:");
        System.out.println("  SQLite: " + sqliteAdapter.supportsFeature(DatabaseAdapter.DatabaseFeature.NATIVE_BOOLEAN));
        System.out.println("  H2:     " + h2AdapterExample.supportsFeature(DatabaseAdapter.DatabaseFeature.NATIVE_BOOLEAN));

        System.out.println("Window Functions:");
        System.out.println("  SQLite: " + sqliteAdapter.supportsFeature(DatabaseAdapter.DatabaseFeature.WINDOW_FUNCTIONS));
        System.out.println("  H2:     " + h2AdapterExample.supportsFeature(DatabaseAdapter.DatabaseFeature.WINDOW_FUNCTIONS));

        System.out.println("Stored Procedures:");
        System.out.println("  SQLite: " + sqliteAdapter.supportsFeature(DatabaseAdapter.DatabaseFeature.STORED_PROCEDURES));
        System.out.println("  H2:     " + h2AdapterExample.supportsFeature(DatabaseAdapter.DatabaseFeature.STORED_PROCEDURES));

        // ===== EXAMPLE 6: SQL Dialect Adaptation =====
        System.out.println("\n===== Example 6: SQL Dialect Adaptation =====");

        // Generic SQL that works with both databases
        String genericSql = "CREATE TABLE test (id INTEGER PRIMARY KEY AUTO_INCREMENT, active BOOLEAN)";

        String sqliteSql = sqliteAdapter.adaptSql(genericSql);
        String h2Sql = h2AdapterExample.adaptSql(genericSql);

        System.out.println("Generic SQL:");
        System.out.println("  " + genericSql);
        System.out.println("");
        System.out.println("SQLite adapted:");
        System.out.println("  " + sqliteSql);
        System.out.println("");
        System.out.println("H2 adapted:");
        System.out.println("  " + h2Sql);

        // ===== EXAMPLE 7: Production Configuration =====
        System.out.println("\n===== Example 7: Production Configuration Examples =====");

        System.out.println("// Embedded device (Raspberry Pi, mobile):");
        System.out.println("LocalStorageManager storage = new LocalStorageManager(");
        System.out.println("    \"/opt/race_timer/data.db\",");
        System.out.println("    DatabaseType.SQLITE  // Small footprint, no JVM overhead");
        System.out.println(");");
        System.out.println("");

        System.out.println("// Desktop application (Windows, Mac, Linux):");
        System.out.println("LocalStorageManager storage = new LocalStorageManager(");
        System.out.println("    \"./race_timer_data\",");
        System.out.println("    DatabaseType.H2  // Better performance, pure Java");
        System.out.println(");");
        System.out.println("");

        System.out.println("// Testing / CI:");
        System.out.println("DatabaseAdapter adapter = DatabaseAdapter.forType(DatabaseType.H2);");
        System.out.println("Connection conn = adapter.connectInMemory(\"test_db\");");
        System.out.println("// Fast tests, no cleanup needed");

        // ===== EXAMPLE 8: Migration Between Databases =====
        System.out.println("\n===== Example 8: Database Migration Considerations =====");

        System.out.println("To migrate from SQLite to H2:");
        System.out.println("1. Both databases use standard SQL - minimal changes needed");
        System.out.println("2. Export data using standard SQL or Java code");
        System.out.println("3. Import to new database type");
        System.out.println("4. Update configuration to use new database type");
        System.out.println("");
        System.out.println("Example migration code:");
        System.out.println("  LocalStorageManager oldDb = new LocalStorageManager(\"old.db\", DatabaseType.SQLITE);");
        System.out.println("  LocalStorageManager newDb = new LocalStorageManager(\"new\", DatabaseType.H2);");
        System.out.println("  // Copy data using standard queries");

        // Cleanup
        h2Queue.close();
        memoryConnection.close();

        System.out.println("\n===== All H2 Examples Completed =====");
        System.out.println("");
        System.out.println("Key Takeaways:");
        System.out.println("✓ H2 is ideal for development, testing, and write-heavy workloads");
        System.out.println("✓ SQLite is ideal for embedded systems and simple deployments");
        System.out.println("✓ Both databases work seamlessly with the Race Timer Client");
        System.out.println("✓ Switch between them with a single parameter change");
        System.out.println("✓ Use H2 in-memory for ultra-fast testing");
    }
}
