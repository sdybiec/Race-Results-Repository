package org.rowtown.rms.rrr.client;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.mqtt.NotificationListener;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;
import org.rowtown.rms.rrr.client.sync.RaceResultsSyncEngine;
import org.rowtown.rms.rrr.client.sync.StartListSyncEngine;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main facade for the Race Timer Client library.
 * Provides offline-first functionality for rowing race timers.
 *
 * Features:
 * 1. Synchronize local Start List with repository
 * 2. Receive notifications when Start List changes
 * 3. Synchronize captured race results with repository
 */
@Slf4j
public class RaceTimerClient implements AutoCloseable {

    private final LocalStorageManager storage;
    private final RepositoryClient apiClient;
    private final NotificationListener mqttListener;
    private final StartListSyncEngine startListSync;
    private final RaceResultsSyncEngine raceResultsSync;
    private final ScheduledExecutorService scheduler;

    private final String regattaId;
    private final String timerId;
    private boolean autoSyncEnabled;

    /**
     * Create a new Race Timer Client.
     *
     * @param config Configuration for the client
     */
    public RaceTimerClient(ClientConfig config) {
        this.regattaId = config.getRegattaId();
        this.timerId = config.getTimerId();

        // Initialize local storage
        this.storage = new LocalStorageManager(config.getLocalDatabasePath());

        // Initialize API client
        this.apiClient = new RepositoryClient(config.getServerUrl(), config.getJwtToken());

        // Initialize MQTT listener
        this.mqttListener = new NotificationListener(
            config.getMqttBrokerUrl(),
            config.getTimerId() + "-client",
            config.getRegattaId()
        );

        // Initialize sync engines
        this.startListSync = new StartListSyncEngine(storage, apiClient, regattaId);
        this.raceResultsSync = new RaceResultsSyncEngine(storage, apiClient, regattaId, timerId);

        // Initialize scheduler for auto-sync
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.autoSyncEnabled = false;

        // Set up notification handler
        setupNotificationHandler();

        log.info("RaceTimerClient initialized for regatta: {}, timer: {}", regattaId, timerId);
    }

    /**
     * Start the client (connect to MQTT, initial sync).
     */
    public void start() {
        log.info("Starting Race Timer Client");

        try {
            // Connect to MQTT broker for notifications
            mqttListener.connect();

            // Perform initial synchronization
            syncStartList();

            log.info("Race Timer Client started successfully");

        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker", e);
            // Continue without MQTT - client will work offline
        } catch (Exception e) {
            log.error("Error starting client", e);
        }
    }

    /**
     * Enable automatic synchronization.
     *
     * @param intervalMinutes Interval in minutes between sync attempts
     */
    public void enableAutoSync(int intervalMinutes) {
        if (autoSyncEnabled) {
            log.warn("Auto-sync already enabled");
            return;
        }

        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.debug("Auto-sync triggered");
                syncAll();
            } catch (Exception e) {
                log.error("Error during auto-sync", e);
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);

        autoSyncEnabled = true;
        log.info("Enabled auto-sync with interval: {} minutes", intervalMinutes);
    }

    /**
     * Disable automatic synchronization.
     */
    public void disableAutoSync() {
        autoSyncEnabled = false;
        log.info("Disabled auto-sync");
    }

    // ========== Start List Operations ==========

    /**
     * Synchronize Start List from server.
     *
     * @return true if successful
     */
    public boolean syncStartList() {
        log.info("Syncing Start List");
        return startListSync.synchronize();
    }

    /**
     * Get the local Start List.
     *
     * @return Local Start List document, or empty if not available
     */
    public Optional<LocalDocument> getStartList() {
        return startListSync.getLocalStartList();
    }

    /**
     * Check if Start List needs synchronization.
     */
    public boolean startListNeedsSync() {
        return startListSync.needsSync();
    }

    // ========== Race Results Operations ==========

    /**
     * Save race results locally (will be synced when online).
     *
     * @param milestoneId Timing milestone (e.g., "start", "finish")
     * @param versionType Results version (e.g., "primary", "firstBackup")
     * @param author Author/timer ID
     * @param modelData Serialized EMF model data
     * @return Saved local document
     */
    public LocalDocument saveRaceResults(String milestoneId, String versionType,
                                        String author, byte[] modelData) {
        log.info("Saving race results for milestone: {}", milestoneId);
        LocalDocument doc = raceResultsSync.saveLocal(milestoneId, versionType, author, modelData);

        // Try to sync immediately if online
        if (apiClient.isServerReachable()) {
            scheduler.execute(this::syncRaceResults);
        }

        return doc;
    }

    /**
     * Update race results locally.
     *
     * @param localId Local document ID
     * @param modelData Updated model data
     * @return Updated local document
     */
    public LocalDocument updateRaceResults(Long localId, byte[] modelData) {
        log.info("Updating race results: {}", localId);
        LocalDocument doc = raceResultsSync.updateLocal(localId, modelData);

        // Try to sync immediately if online
        if (apiClient.isServerReachable()) {
            scheduler.execute(this::syncRaceResults);
        }

        return doc;
    }

    /**
     * Synchronize all pending race results to server.
     *
     * @return Sync result with statistics
     */
    public RaceResultsSyncEngine.SyncResult syncRaceResults() {
        log.info("Syncing race results");
        return raceResultsSync.synchronizePending();
    }

    /**
     * Get all local race results for this timer.
     */
    public List<LocalDocument> getAllRaceResults() {
        return raceResultsSync.getAllLocal();
    }

    /**
     * Get count of pending race results (not yet synced).
     */
    public int getPendingRaceResultsCount() {
        return raceResultsSync.getPendingCount();
    }

    // ========== Combined Operations ==========

    /**
     * Synchronize everything (Start List + Race Results).
     */
    public void syncAll() {
        log.info("Syncing all data");
        syncStartList();
        syncRaceResults();
    }

    /**
     * Check if client is online (server reachable).
     */
    public boolean isOnline() {
        return apiClient.isServerReachable();
    }

    /**
     * Check if MQTT is connected.
     */
    public boolean isMqttConnected() {
        return mqttListener.isConnected();
    }

    /**
     * Get client status information.
     */
    public ClientStatus getStatus() {
        ClientStatus status = new ClientStatus();
        status.online = isOnline();
        status.mqttConnected = isMqttConnected();
        status.autoSyncEnabled = autoSyncEnabled;
        status.pendingRaceResults = getPendingRaceResultsCount();
        status.startListNeedsSync = startListNeedsSync();

        Long minutesSinceSync = startListSync.getMinutesSinceLastSync();
        status.minutesSinceLastStartListSync = minutesSinceSync;

        return status;
    }

    /**
     * Set up MQTT notification handler for Start List changes.
     */
    private void setupNotificationHandler() {
        mqttListener.addChangeHandler(event -> {
            log.info("Received Start List change notification: {}", event.eventType);

            // Refresh Start List when notified of changes
            if ("START_LIST".equals(event.documentType)) {
                log.info("Refreshing Start List due to server change (version: {})",
                    event.versionNumber);
                startListSync.forceRefresh();
            }
        });
    }

    @Override
    public void close() {
        log.info("Closing Race Timer Client");

        // Disconnect MQTT
        mqttListener.disconnect();

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // Close storage
        storage.close();

        log.info("Race Timer Client closed");
    }

    /**
     * Client status information.
     */
    public static class ClientStatus {
        public boolean online;
        public boolean mqttConnected;
        public boolean autoSyncEnabled;
        public int pendingRaceResults;
        public boolean startListNeedsSync;
        public Long minutesSinceLastStartListSync;

        @Override
        public String toString() {
            return String.format(
                "ClientStatus[online=%b, mqtt=%b, autoSync=%b, pending=%d, startListNeedsSync=%b]",
                online, mqttConnected, autoSyncEnabled, pendingRaceResults, startListNeedsSync
            );
        }
    }

    /**
     * Configuration for RaceTimerClient.
     */
    public static class ClientConfig {
        private final String regattaId;
        private final String timerId;
        private final String serverUrl;
        private final String mqttBrokerUrl;
        private final String localDatabasePath;
        private final String jwtToken;

        public ClientConfig(String regattaId, String timerId, String serverUrl,
                          String mqttBrokerUrl, String localDatabasePath, String jwtToken) {
            this.regattaId = regattaId;
            this.timerId = timerId;
            this.serverUrl = serverUrl;
            this.mqttBrokerUrl = mqttBrokerUrl;
            this.localDatabasePath = localDatabasePath;
            this.jwtToken = jwtToken;
        }

        public String getRegattaId() { return regattaId; }
        public String getTimerId() { return timerId; }
        public String getServerUrl() { return serverUrl; }
        public String getMqttBrokerUrl() { return mqttBrokerUrl; }
        public String getLocalDatabasePath() { return localDatabasePath; }
        public String getJwtToken() { return jwtToken; }
    }
}
