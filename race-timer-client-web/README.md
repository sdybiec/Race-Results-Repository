# Race Timer Client - Web Edition

Offline-first client library for React applications that enables rowing race timers to synchronize Start Lists and Race Results with the Race Results Repository.

## Features

✅ **Offline-First Architecture**
- Work completely offline with LocalStorage persistence
- Automatic synchronization when connection is restored
- Queue pending operations for later upload

✅ **Start List Synchronization**
- Download and cache Start List locally
- Real-time MQTT notifications when Start List changes
- Automatic refresh on notifications

✅ **Race Results Capture & Sync**
- Save results locally while offline
- Batch upload pending results
- Conflict detection and retry mechanism

✅ **React Integration**
- Context provider for easy setup
- Custom hooks for granular control
- TypeScript support

## Installation

```bash
npm install @rowtown/race-timer-client-web
```

## Quick Start

### 1. Wrap your app with RaceTimerProvider

```tsx
import { RaceTimerProvider } from '@rowtown/race-timer-client-web';

function App() {
  const config = {
    regattaId: 'Stotesbury Cup Regatta',   // regatta name
    regattaStartDate: '2024-05-17',        // ISO-8601; part of the regatta key
    timer: 'PRIMARY',                      // this timer's role: PRIMARY | FIRST_BACKUP | SECOND_BACKUP
    serverUrl: 'https://api.example.com',
    mqttBrokerUrl: 'wss://mqtt.example.com:8083/mqtt',
    jwtToken: 'your-jwt-token',
  };

  return (
    <RaceTimerProvider config={config} autoSyncIntervalMinutes={5}>
      <YourApp />
    </RaceTimerProvider>
  );
}
```

### 2. Use the hook in your components

```tsx
import { useRaceTimer } from '@rowtown/race-timer-client-web';

function TimerComponent() {
  const {
    startList,
    raceResults,
    status,
    saveRaceResults,
    syncAll
  } = useRaceTimer();

  const handleSaveResults = () => {
    const modelData = btoa(JSON.stringify({
      /* Your EMF model data */
    }));

    // The timer role comes from the provider config; raceId is derived from the model.
    saveRaceResults(
      'Finish Line',      // milestoneId
      'timer@club.org',   // author
      modelData           // base64-encoded XMI model data (must identify its race)
    );
  };

  return (
    <div>
      <h2>Status</h2>
      <p>Online: {status.online ? 'Yes' : 'No'}</p>
      <p>MQTT: {status.mqttConnected ? 'Connected' : 'Disconnected'}</p>
      <p>Pending Results: {status.pendingRaceResults}</p>

      <h2>Start List</h2>
      {startList && (
        <pre>{atob(startList.modelData)}</pre>
      )}

      <h2>Race Results</h2>
      <p>Total: {raceResults.length}</p>

      <button onClick={handleSaveResults}>
        Save Race Results
      </button>

      <button onClick={syncAll}>
        Sync All
      </button>
    </div>
  );
}
```

## Advanced Usage

### Using Individual Hooks

For more granular control, use individual hooks:

```tsx
import { RaceTimerClient } from '@rowtown/race-timer-client-web';
import {
  useStartList,
  useRaceResults,
  useClientStatus
} from '@rowtown/race-timer-client-web';

function AdvancedComponent() {
  const [client] = useState(() => new RaceTimerClient(config));

  const {
    startList,
    loading: startListLoading,
    sync: syncStartList
  } = useStartList(client);

  const {
    raceResults,
    pendingCount,
    save,
    sync: syncResults
  } = useRaceResults(client);

  const { status } = useClientStatus(client);

  return (
    <div>
      {startListLoading && <p>Loading Start List...</p>}

      <button onClick={syncStartList}>
        Refresh Start List
      </button>

      <button onClick={() => save('Finish Line', 'timer@club.org', modelData)}>
        Save Result
      </button>

      {pendingCount > 0 && (
        <button onClick={syncResults}>
          Upload {pendingCount} Pending Results
        </button>
      )}
    </div>
  );
}
```

### Direct Client Usage (No React)

You can use the client directly without React:

```typescript
import { RaceTimerClient } from '@rowtown/race-timer-client-web';

const client = new RaceTimerClient({
  regattaId: 'Stotesbury Cup Regatta',
  regattaStartDate: '2024-05-17',
  timer: 'PRIMARY',
  serverUrl: 'https://api.example.com',
  mqttBrokerUrl: 'wss://mqtt.example.com:8083/mqtt',
  jwtToken: 'your-jwt-token',
});

// Start the client
await client.start();

// Enable auto-sync every 5 minutes
client.enableAutoSync(5);

// Get Start List
const startList = client.getStartList();
console.log('Start List:', startList);

// Save race results
const doc = client.saveRaceResults(
  'Finish Line',
  'timer@club.org',
  btoa(JSON.stringify(modelData))
);
console.log('Saved:', doc.localId);

// Check status
const status = await client.getStatus();
console.log('Status:', status);

// Sync all
await client.syncAll();

// Shutdown when done
client.shutdown();
```

## API Reference

### RaceTimerProvider

Context provider that manages the client lifecycle.

**Props:**
- `config: ClientConfig` - Client configuration
- `autoSyncIntervalMinutes?: number` - Auto-sync interval (default: 5)
- `children: ReactNode` - Child components

### useRaceTimer()

Hook that provides access to the full client context.

**Returns:**
```typescript
{
  client: RaceTimerClient;
  status: ClientStatus;
  startList: LocalDocument | null;
  raceResults: LocalDocument[];
  syncStartList: () => Promise<boolean>;
  syncRaceResults: () => Promise<SyncResult>;
  syncAll: () => Promise<void>;
  saveRaceResults: (milestoneId, author, modelData) => LocalDocument;
  updateRaceResults: (localId, modelData) => LocalDocument;
  refreshStatus: () => Promise<void>;
}
```

### useStartList(client)

Hook for managing Start List synchronization.

**Returns:**
```typescript
{
  startList: LocalDocument | null;
  loading: boolean;
  error: Error | null;
  sync: () => Promise<boolean>;
  refresh: () => void;
}
```

### useRaceResults(client)

Hook for managing Race Results.

**Returns:**
```typescript
{
  raceResults: LocalDocument[];
  pendingCount: number;
  loading: boolean;
  error: Error | null;
  save: (milestoneId, author, modelData) => LocalDocument;
  update: (localId, modelData) => LocalDocument;
  sync: () => Promise<SyncResult>;
  refresh: () => void;
}
```

### useClientStatus(client, refreshIntervalMs?)

Hook for monitoring client status.

**Returns:**
```typescript
{
  status: ClientStatus;
  refresh: () => Promise<void>;
}
```

### useAutoSync(client, intervalMinutes?)

Hook for managing auto-sync.

**Returns:**
```typescript
{
  enabled: boolean;
  enable: () => void;
  disable: () => void;
  toggle: () => void;
}
```

### useStorageStats(client, refreshIntervalMs?)

Hook for monitoring local storage usage. Polls `client.getStorageStats()` on an
interval (`refreshIntervalMs`, default `60000`).

**Returns:**
```typescript
{
  stats: {
    documentCount: number;   // Number of stored documents
    pendingCount: number;    // Documents awaiting sync
    storageSize: number;     // Approximate bytes used
  };
  refresh: () => void;       // Force an immediate refresh
}
```

Example:

```tsx
import { useStorageStats } from '@rowtown/race-timer-client-web';

function StorageIndicator() {
  const { client } = useRaceTimer();
  const { stats } = useStorageStats(client);

  return <span>{stats.documentCount} docs · {stats.storageSize} bytes</span>;
}
```

## Data Models

### ClientConfig

```typescript
interface ClientConfig {
  regattaId: string;        // Regatta name
  regattaStartDate: string; // ISO-8601 (yyyy-MM-dd); part of the regatta key
  timer: TimerRole;         // this timer's role: 'PRIMARY' | 'FIRST_BACKUP' | 'SECOND_BACKUP'
  serverUrl: string;        // Repository API URL
  mqttBrokerUrl: string;    // MQTT broker URL (WebSocket)
  jwtToken: string;         // JWT authentication token
}
```

### LocalDocument

```typescript
interface LocalDocument {
  localId: string;                      // Local unique ID
  serverId?: number;                    // Server document ID
  regattaId: string;                    // Regatta name
  regattaStartDate: string;             // Regatta start date (ISO-8601)
  raceId?: string;                      // Race ID (Race Results), derived from the model
  milestoneId?: string;                 // Milestone (e.g., "Finish Line")
  documentType: 'START_LIST' | 'RACE_RESULTS';
  timer?: TimerRole;                    // 'PRIMARY' | 'FIRST_BACKUP' | 'SECOND_BACKUP'
  author?: string;                      // Document author
  description?: string;                 // Description
  localVersion: number;                 // Local version number
  serverVersion?: number;               // Server version number
  syncStatus: SyncStatus;               // Sync status
  createdAt: string;                    // Created timestamp (ISO)
  modifiedAt: string;                   // Modified timestamp (ISO)
  lastSyncedAt?: string;                // Last sync timestamp (ISO)
  lastSyncError?: string;               // Last sync error message
  retryCount: number;                   // Sync retry count
  modelData: string;                    // Base64-encoded EMF model
  serializationFormat: 'JSON' | 'XMI';  // Serialization format
}
```

### SyncStatus

```typescript
enum SyncStatus {
  SYNCED = 'SYNCED',         // Synchronized with server
  PENDING = 'PENDING',       // Pending upload
  SYNCING = 'SYNCING',       // Currently syncing
  FAILED = 'FAILED',         // Sync failed (will retry)
  CONFLICT = 'CONFLICT'      // Conflict detected
}
```

### ClientStatus

```typescript
interface ClientStatus {
  online: boolean;                      // Server reachable
  mqttConnected: boolean;               // MQTT connected
  autoSyncEnabled: boolean;             // Auto-sync enabled
  pendingRaceResults: number;           // Number of pending results
  startListNeedsSync: boolean;          // Start List needs sync
  minutesSinceLastStartListSync?: number; // Minutes since last sync
}
```

## Architecture

```
┌─────────────────────────────────────────────────┐
│           React Application Layer               │
│  (RaceTimerProvider, useRaceTimer, etc.)       │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│           RaceTimerClient (Facade)              │
└─────┬──────────────────────────┬────────────────┘
      │                          │
      ▼                          ▼
┌─────────────────┐      ┌─────────────────┐
│ StartListSync   │      │ RaceResultsSync │
│   Engine        │      │     Engine      │
└────┬────────────┘      └────┬────────────┘
     │                        │
     ├────────────┬───────────┤
     ▼            ▼           ▼
┌─────────┐  ┌──────────┐  ┌──────────────┐
│ Storage │  │   API    │  │     MQTT     │
│ Manager │  │  Client  │  │   Listener   │
└─────────┘  └──────────┘  └──────────────┘
     │            │              │
     ▼            ▼              ▼
┌─────────┐  ┌──────────┐  ┌──────────────┐
│LocalStor│  │  fetch() │  │   mqtt.js    │
│   age   │  │   API    │  │  (WebSocket) │
└─────────┘  └──────────┘  └──────────────┘
```

## Best Practices

### 1. Always Wrap with Provider

```tsx
// ✅ Good
<RaceTimerProvider config={config}>
  <App />
</RaceTimerProvider>

// ❌ Bad - useRaceTimer() will throw error
<App />
```

### 2. Handle Offline State

```tsx
function SmartComponent() {
  const { status, saveRaceResults } = useRaceTimer();

  const handleSave = () => {
    // Always save locally first
    const doc = saveRaceResults(milestoneId, author, modelData);

    if (status.online) {
      // Will auto-sync if online
      console.log('Syncing immediately');
    } else {
      // Will sync when connection restored
      console.log('Saved offline, will sync later');
    }
  };

  return (
    <div>
      {!status.online && (
        <div className="offline-banner">
          Working Offline - Results will sync when online
        </div>
      )}
      <button onClick={handleSave}>Save Results</button>
    </div>
  );
}
```

### 3. Monitor Pending Results

```tsx
function SyncIndicator() {
  const { status } = useRaceTimer();

  if (status.pendingRaceResults === 0) {
    return <span>✓ All synced</span>;
  }

  return (
    <span>
      ⏳ {status.pendingRaceResults} results pending upload
    </span>
  );
}
```

### 4. Handle Errors Gracefully

```tsx
function ResultsList() {
  const { raceResults, syncRaceResults } = useRaceTimer();
  const [error, setError] = useState<string | null>(null);

  const handleSync = async () => {
    try {
      const result = await syncRaceResults();
      if (result.failedDocuments > 0) {
        setError(`${result.failedDocuments} results failed to sync`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sync failed');
    }
  };

  return (
    <div>
      {error && <div className="error">{error}</div>}
      <button onClick={handleSync}>Sync Now</button>
    </div>
  );
}
```

## Storage Considerations

The client uses browser LocalStorage for persistence:

- **Storage Limit**: ~5-10MB depending on browser
- **Namespace**: All keys prefixed with `race-timer:`
- **Clear Data**: Use `client.clearLocalData()` to reset

To check storage usage:

```tsx
const { client } = useRaceTimer();
const stats = client.getStorageStats();

console.log(`Documents: ${stats.documentCount}`);
console.log(`Pending: ${stats.pendingCount}`);
console.log(`Size: ${stats.storageSize} bytes`);
```

## MQTT Connection

The client subscribes to Start List change notifications:

**Topic Pattern**: `regatta/{regattaId}/startlist`

**WebSocket URL**: Use `wss://` protocol for secure connections
- Example: `wss://mqtt.example.com:8083/mqtt`

## License

Released under the [MIT License](../LICENSE). Copyright © 2025 RowTown.

## Support

For issues and questions, please visit the [GitHub repository](https://github.com/rowtown/race-results-repository).
