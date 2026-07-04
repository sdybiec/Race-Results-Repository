# Race Timer Client

Headless offline-first client library for rowing race timers that enables:

1. **Synchronizing a local Start List** with the Race Results Repository
2. **Receiving notifications** when the Start List changes to keep the local version current
3. **Synchronizing captured race results** stored locally with the repository

## Features

### Offline-First Architecture
- **Works without network connectivity** - all operations are stored locally
- **Automatic synchronization** when connection is restored
- **Conflict resolution** for simultaneous updates
- **Retry mechanism** with exponential backoff for failed operations

### Start List Management
- **Download and cache** Start List from repository
- **Real-time notifications** via MQTT when Start List changes
- **Automatic refresh** when updates are detected
- **Version tracking** to prevent unnecessary downloads

### Race Results Capture
- **Save results locally** while offline
- **Queue pending uploads** for later synchronization
- **Batch synchronization** of multiple results
- **Track sync status** for each result document

### Technology Stack
- **Java 17** compatible
- **SQLite** for local storage
- **OkHttp** for REST API communication
- **Eclipse Paho MQTT** for real-time notifications
- **Jackson** for JSON processing

## Installation

### Maven

```xml
<dependency>
    <groupId>org.rowtown.rms.rrr</groupId>
    <artifactId>race-timer-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```gradle
implementation 'org.rowtown.rms.rrr:race-timer-client:1.0.0-SNAPSHOT'
```

## Quick Start

### 1. Initialize the Client

```java
import org.rowtown.rms.rrr.client.RaceTimerClient;
import org.rowtown.rms.rrr.client.RaceTimerClient.ClientConfig;

// Configure the client
ClientConfig config = new ClientConfig(
    "Stotesbury Cup Regatta",             // regattaId (regatta name)
    "2024-05-17",                         // regattaStartDate (ISO-8601); part of the regatta key
    "PRIMARY",                            // timer role: PRIMARY | FIRST_BACKUP | SECOND_BACKUP
    "http://localhost:8080",              // serverUrl
    "tcp://localhost:1883",               // mqttBrokerUrl
    "./data/race-timer.db",               // localDatabasePath
    "your-jwt-token-here"                 // jwtToken
);

// Create and start client
RaceTimerClient client = new RaceTimerClient(config);
client.start();
```

### 2. Synchronize Start List

```java
// Download Start List from server
boolean success = client.syncStartList();

if (success) {
    // Get local copy
    Optional<LocalDocument> startList = client.getStartList();

    if (startList.isPresent()) {
        byte[] modelData = startList.get().getModelData();
        // Parse and use Start List data
        System.out.println("Start List version: " + startList.get().getServerVersion());
    }
}
```

### 3. Enable Auto-Sync

```java
// Enable automatic synchronization every 5 minutes
client.enableAutoSync(5);

// The client will automatically:
// - Download Start List updates
// - Upload pending race results
// - Retry failed operations
```

### 4. Save Race Results (Offline)

```java
// Capture race results locally
// These will be queued for upload when online
byte[] resultsData = captureTimingData(); // Your timing data

// The timer role comes from the client config; the raceId is derived from the model.
LocalDocument saved = client.saveRaceResults(
    "Finish Line",     // milestoneId
    "timer@club.org",  // author
    resultsData        // EMF model data (XMI; must identify its race)
);

System.out.println("Saved locally with ID: " + saved.getLocalId());
```

> **Models are validated before they are saved.** `saveRaceResults` and
> `updateRaceResults` load the model with the generated TDI classes and throw
> `IllegalArgumentException` if it cannot be loaded (bad XMI, or not a TDI model).
> This mirrors the server's fail-fast ingest policy, so an unusable capture is
> rejected immediately instead of being queued for a sync the server would reject.

### 5. Update Race Results

```java
// Update previously saved results
Long localId = saved.getLocalId();
byte[] updatedData = captureUpdatedTimingData();

LocalDocument updated = client.updateRaceResults(localId, updatedData);

System.out.println("Updated local version: " + updated.getLocalVersion());
```

### 6. Synchronize Race Results

```java
// Manually trigger synchronization of pending results
RaceResultsSyncEngine.SyncResult result = client.syncRaceResults();

System.out.println("Synced: " + result.syncedDocuments + " documents");
System.out.println("Failed: " + result.failedDocuments + " documents");
System.out.println("Total pending: " + client.getPendingRaceResultsCount());
```

### 7. Check Client Status

```java
RaceTimerClient.ClientStatus status = client.getStatus();

System.out.println("Online: " + status.online);
System.out.println("MQTT Connected: " + status.mqttConnected);
System.out.println("Pending Results: " + status.pendingRaceResults);
System.out.println("Minutes since last Start List sync: " +
    status.minutesSinceLastStartListSync);
```

### 8. Clean Shutdown

```java
// Close client when done
client.close();
```

## Complete Example

```java
public class RaceTimerExample {
    public static void main(String[] args) {
        // Configure client
        ClientConfig config = new ClientConfig(
            "Stotesbury Cup Regatta",
            "2024-05-17",
            "PRIMARY",
            "http://localhost:8080",
            "tcp://localhost:1883",
            "./race-timer.db",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        );

        try (RaceTimerClient client = new RaceTimerClient(config)) {
            // Start client and sync Start List
            client.start();
            client.enableAutoSync(5);

            // Get Start List
            Optional<LocalDocument> startList = client.getStartList();
            if (startList.isPresent()) {
                System.out.println("Start List ready, version: " +
                    startList.get().getServerVersion());
            }

            // Simulate capturing race results
            byte[] results = createRaceResults(); // Your method

            LocalDocument saved = client.saveRaceResults(
                "Finish Line", "timer@club.org", results
            );

            System.out.println("Results saved locally: " + saved.getLocalId());

            // Check sync status
            if (client.isOnline()) {
                RaceResultsSyncEngine.SyncResult syncResult = client.syncRaceResults();
                System.out.println("Sync result: " + syncResult);
            } else {
                System.out.println("Offline - results will sync when connection restored");
            }

            // Keep running to receive notifications
            Thread.sleep(60000); // Run for 1 minute

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] createRaceResults() {
        // Create your EMF Timing Data Interchange model here
        return new byte[0];
    }
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                 RaceTimerClient (Facade)                 │
│  - Unified API for all operations                       │
│  - Lifecycle management                                  │
│  - Auto-sync scheduling                                  │
└───────────┬─────────────────────────────────┬───────────┘
            │                                 │
            ├─────────────────┐               ├─────────────────┐
            │                 │               │                 │
┌───────────▼──────────┐  ┌──▼────────────┐ ┌▼─────────────┐  │
│ StartListSyncEngine  │  │ RaceResults   │ │ Notification │  │
│                      │  │ SyncEngine    │ │ Listener     │  │
│ - Download           │  │               │ │ (MQTT)       │  │
│ - Update             │  │ - Save local  │ │              │  │
│ - Version tracking   │  │ - Upload      │ │ - Subscribe  │  │
│                      │  │ - Retry       │ │ - Notify     │  │
└───────────┬──────────┘  └──┬────────────┘ └┬─────────────┘  │
            │                │               │                 │
            └────────────────┼───────────────┴─────────────────┘
                             │
                   ┌─────────▼──────────┐
                   │ LocalStorageManager│
                   │                    │
                   │ - SQLite database  │
                   │ - Transactions     │
                   │ - Query methods    │
                   └─────────┬──────────┘
                             │
                   ┌─────────▼──────────┐
                   │   Repository API   │
                   │                    │
                   │ - REST client      │
                   │ - Authentication   │
                   │ - Connectivity     │
                   └────────────────────┘
```

## Local Storage Schema

The client maintains a SQLite database with the following structure:

### documents table
```sql
local_id            INTEGER PRIMARY KEY
server_id           INTEGER
regatta_id          TEXT NOT NULL
regatta_start_date  TEXT
race_id             TEXT
milestone_id        TEXT
timer               TEXT
document_type       TEXT NOT NULL
author              TEXT NOT NULL
description         TEXT
created_at          TEXT
modified_at         TEXT
local_version       INTEGER DEFAULT 1
server_version      INTEGER
sync_status         TEXT NOT NULL DEFAULT 'PENDING'
last_synced_at      TEXT
last_sync_error     TEXT
retry_count         INTEGER DEFAULT 0
model_data          BLOB
serialization_format TEXT DEFAULT 'XMI'
local_created_at    TEXT NOT NULL
```

### Sync Status Values
- `SYNCED` - Document is synchronized with server
- `PENDING` - Local changes awaiting upload
- `SYNCING` - Currently being synchronized
- `FAILED` - Synchronization failed (will retry)
- `CONFLICT` - Conflict detected (needs resolution)

## API Reference

### RaceTimerClient

#### Constructor
```java
RaceTimerClient(ClientConfig config)
```

#### Lifecycle Methods
```java
void start()                           // Initialize and connect
void close()                           // Clean shutdown
void enableAutoSync(int intervalMins)  // Enable auto-sync
void disableAutoSync()                 // Disable auto-sync
```

#### Start List Operations
```java
boolean syncStartList()                // Download from server
Optional<LocalDocument> getStartList() // Get local copy
boolean startListNeedsSync()           // Check if update needed
```

#### Race Results Operations
```java
LocalDocument saveRaceResults(String milestoneId, String author, byte[] modelData)
LocalDocument updateRaceResults(Long localId, byte[] modelData)
SyncResult syncRaceResults()
List<LocalDocument> getAllRaceResults()
int getPendingRaceResultsCount()
```

#### Regatta Definition (RML) Operations
```java
boolean syncRegattaDefinition()                          // Download from server
Optional<LocalDocument> getRegattaDefinition()           // Get local copy
LocalDocument saveRegattaDefinition(byte[] modelData, String author) // Save + push
boolean deleteRegattaDefinition()                        // Delete local + server
```

The RML (Regatta Modeling Language) document defines a regatta edition and is
one-per-edition. `saveRegattaDefinition` validates that the model loads into the
generated RML classes (fail-fast) and derives the regatta key (name + start
date) from the model itself. The client also refreshes its local copy when it
receives an RML change notification.

#### Status Methods
```java
boolean isOnline()                     // Check server connectivity
boolean isMqttConnected()              // Check MQTT connection
ClientStatus getStatus()               // Get comprehensive status
void syncAll()                         // Sync everything (RML + Start List + Results)
```

## Configuration

### ClientConfig

```java
ClientConfig config = new ClientConfig(
    String regattaId,          // regatta name, e.g., "Stotesbury Cup Regatta"
    String regattaStartDate,   // ISO-8601, e.g., "2024-05-17" (part of the regatta key)
    String timer,              // this timer's role: "PRIMARY" | "FIRST_BACKUP" | "SECOND_BACKUP"
    String serverUrl,          // e.g., "http://api.example.com"
    String mqttBrokerUrl,      // e.g., "tcp://mqtt.example.com:1883"
    String localDatabasePath,  // e.g., "./data/timer.db"
    String jwtToken            // JWT authentication token
);
```

## Error Handling

The client is designed to be resilient to network failures:

### Offline Operation
```java
// All operations work offline
LocalDocument doc = client.saveRaceResults(...);
// Saved locally, will sync when online

// Check connectivity
if (!client.isOnline()) {
    System.out.println("Working offline");
}
```

### Retry Logic
```java
// Failed syncs are automatically retried
// Check failed documents
List<LocalDocument> all = client.getAllRaceResults();
for (LocalDocument doc : all) {
    if (doc.getSyncStatus() == SyncStatus.FAILED) {
        System.out.println("Failed: " + doc.getLastSyncError());
        System.out.println("Retry count: " + doc.getRetryCount());
    }
}
```

### Manual Sync
```java
// Force synchronization
SyncResult result = client.syncRaceResults();

if (!result.isSuccess()) {
    System.err.println("Sync failed: " + result.error);
    System.err.println("Failed documents: " + result.failedDocuments);
}
```

## Best Practices

1. **Always use try-with-resources** to ensure proper cleanup
2. **Enable auto-sync** for continuous operation
3. **Check sync status** before critical operations
4. **Handle offline scenarios** gracefully
5. **Monitor pending count** to ensure data isn't stuck
6. **Configure the timer role** per client instance (PRIMARY, FIRST_BACKUP, SECOND_BACKUP)
7. **Keep JWT tokens secure** and refresh as needed

## Testing

Run the test suite:

```bash
mvn test
```

## Logging

The client uses SLF4J for logging. Configure your logging framework:

```properties
# Example logback configuration
org.rowtown.rms.rrr.client=DEBUG
```

## License

Released under the [MIT License](../LICENSE). Copyright © 2025 RowTown.
