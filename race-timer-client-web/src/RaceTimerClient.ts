import { LocalStorageManager } from './storage/LocalStorageManager';
import { RepositoryClient } from './api/RepositoryClient';
import { NotificationListener } from './mqtt/NotificationListener';
import { StartListSyncEngine } from './sync/StartListSyncEngine';
import { RaceResultsSyncEngine } from './sync/RaceResultsSyncEngine';
import { LocalDocument } from './models/LocalDocument';
import { ClientConfig, ClientStatus, SyncResult } from './models/ApiTypes';

/**
 * Main facade for the Race Timer Client library.
 * Provides offline-first functionality for rowing race timers.
 *
 * Features:
 * 1. Synchronize local Start List with repository
 * 2. Receive notifications when Start List changes
 * 3. Synchronize captured race results with repository
 */
export class RaceTimerClient {
  private readonly storage: LocalStorageManager;
  private readonly apiClient: RepositoryClient;
  private readonly mqttListener: NotificationListener;
  private readonly startListSync: StartListSyncEngine;
  private readonly raceResultsSync: RaceResultsSyncEngine;

  private readonly regattaId: string;
  private readonly timerId: string;
  private autoSyncInterval: number | null = null;
  private autoSyncEnabled: boolean = false;

  /**
   * Create a new Race Timer Client.
   */
  constructor(config: ClientConfig) {
    this.regattaId = config.regattaId;
    this.timerId = config.timerId;

    // Initialize local storage
    this.storage = new LocalStorageManager();

    // Initialize API client
    this.apiClient = new RepositoryClient(config.serverUrl, config.jwtToken);

    // Initialize MQTT listener
    this.mqttListener = new NotificationListener(
      config.mqttBrokerUrl,
      `${config.timerId}-client`,
      config.regattaId
    );

    // Initialize sync engines
    this.startListSync = new StartListSyncEngine(
      this.storage,
      this.apiClient,
      this.regattaId
    );

    this.raceResultsSync = new RaceResultsSyncEngine(
      this.storage,
      this.apiClient,
      this.regattaId,
      this.timerId
    );

    // Set up notification handler
    this.setupNotificationHandler();

    console.log(
      `RaceTimerClient initialized for regatta: ${this.regattaId}, timer: ${this.timerId}`
    );
  }

  /**
   * Start the client (connect to MQTT, initial sync).
   */
  async start(): Promise<void> {
    console.log('Starting Race Timer Client');

    try {
      // Connect to MQTT broker for notifications
      await this.mqttListener.connect();

      // Perform initial synchronization
      await this.syncStartList();

      console.log('Race Timer Client started successfully');
    } catch (error) {
      console.error('Failed to connect to MQTT broker', error);
      // Continue without MQTT - client will work offline
    }
  }

  /**
   * Enable automatic synchronization.
   */
  enableAutoSync(intervalMinutes: number): void {
    if (this.autoSyncEnabled) {
      console.warn('Auto-sync already enabled');
      return;
    }

    this.autoSyncInterval = window.setInterval(() => {
      this.syncAll().catch((error) => {
        console.error('Error during auto-sync', error);
      });
    }, intervalMinutes * 60 * 1000);

    this.autoSyncEnabled = true;
    console.log(`Enabled auto-sync with interval: ${intervalMinutes} minutes`);
  }

  /**
   * Disable automatic synchronization.
   */
  disableAutoSync(): void {
    if (this.autoSyncInterval !== null) {
      window.clearInterval(this.autoSyncInterval);
      this.autoSyncInterval = null;
    }
    this.autoSyncEnabled = false;
    console.log('Disabled auto-sync');
  }

  // ========== Start List Operations ==========

  /**
   * Synchronize Start List from server.
   */
  async syncStartList(): Promise<boolean> {
    console.log('Syncing Start List');
    return this.startListSync.synchronize();
  }

  /**
   * Get the local Start List.
   */
  getStartList(): LocalDocument | null {
    return this.startListSync.getLocalStartList();
  }

  /**
   * Check if Start List needs synchronization.
   */
  startListNeedsSync(): boolean {
    return this.startListSync.needsSync();
  }

  // ========== Race Results Operations ==========

  /**
   * Save race results locally (will be synced when online).
   */
  saveRaceResults(
    milestoneId: string,
    versionType: string,
    author: string,
    modelData: string
  ): LocalDocument {
    console.log(`Saving race results for milestone: ${milestoneId}`);
    const doc = this.raceResultsSync.saveLocal(milestoneId, versionType, author, modelData);

    // Try to sync immediately if online
    this.apiClient.isServerReachable().then((online) => {
      if (online) {
        this.syncRaceResults().catch(console.error);
      }
    });

    return doc;
  }

  /**
   * Update race results locally.
   */
  updateRaceResults(localId: string, modelData: string): LocalDocument {
    console.log(`Updating race results: ${localId}`);
    const doc = this.raceResultsSync.updateLocal(localId, modelData);

    // Try to sync immediately if online
    this.apiClient.isServerReachable().then((online) => {
      if (online) {
        this.syncRaceResults().catch(console.error);
      }
    });

    return doc;
  }

  /**
   * Synchronize all pending race results to server.
   */
  async syncRaceResults(): Promise<SyncResult> {
    console.log('Syncing race results');
    return this.raceResultsSync.synchronizePending();
  }

  /**
   * Get all local race results for this timer.
   */
  getAllRaceResults(): LocalDocument[] {
    return this.raceResultsSync.getAllLocal();
  }

  /**
   * Get count of pending race results (not yet synced).
   */
  getPendingRaceResultsCount(): number {
    return this.raceResultsSync.getPendingCount();
  }

  // ========== Combined Operations ==========

  /**
   * Synchronize everything (Start List + Race Results).
   */
  async syncAll(): Promise<void> {
    console.log('Syncing all data');
    await this.syncStartList();
    await this.syncRaceResults();
  }

  /**
   * Check if client is online (server reachable).
   */
  async isOnline(): Promise<boolean> {
    return this.apiClient.isServerReachable();
  }

  /**
   * Check if MQTT is connected.
   */
  isMqttConnected(): boolean {
    return this.mqttListener.isConnected();
  }

  /**
   * Get client status information.
   */
  async getStatus(): Promise<ClientStatus> {
    const online = await this.isOnline();
    const mqttConnected = this.isMqttConnected();
    const pendingRaceResults = this.getPendingRaceResultsCount();
    const startListNeedsSync = this.startListNeedsSync();
    const minutesSinceLastStartListSync = this.startListSync.getMinutesSinceLastSync() || undefined;

    return {
      online,
      mqttConnected,
      autoSyncEnabled: this.autoSyncEnabled,
      pendingRaceResults,
      startListNeedsSync,
      minutesSinceLastStartListSync,
    };
  }

  /**
   * Get storage statistics.
   */
  getStorageStats(): { documentCount: number; pendingCount: number; storageSize: number } {
    return this.storage.getStats();
  }

  /**
   * Clear all local data.
   */
  clearLocalData(): void {
    this.storage.clear();
    console.log('Cleared all local data');
  }

  /**
   * Shut down the client.
   */
  shutdown(): void {
    console.log('Shutting down Race Timer Client');

    // Disable auto-sync
    this.disableAutoSync();

    // Disconnect MQTT
    this.mqttListener.disconnect();

    console.log('Race Timer Client shut down');
  }

  /**
   * Set up MQTT notification handler for Start List changes.
   */
  private setupNotificationHandler(): void {
    this.mqttListener.addChangeHandler((event) => {
      console.log(`Received Start List change notification: ${event.eventType}`);

      // Refresh Start List when notified of changes
      if (event.documentType === 'START_LIST') {
        console.log(
          `Refreshing Start List due to server change (version: ${event.versionNumber})`
        );
        this.startListSync.forceRefresh().catch((error) => {
          console.error('Failed to refresh Start List', error);
        });
      }
    });
  }
}
