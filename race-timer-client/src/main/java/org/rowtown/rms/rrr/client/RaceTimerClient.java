package org.rowtown.rms.rrr.client;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.mqtt.NotificationListener;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;
import org.rowtown.rms.rrr.client.sync.RaceResultsSyncEngine;
import org.rowtown.rms.rrr.client.sync.RmlSyncEngine;
import org.rowtown.rms.rrr.client.sync.StartListSyncEngine;
import org.rowtown.rms.rrr.client.util.RmlKeyExtractor;
import org.rowtown.rms.rrr.client.util.RmlModelValidator;
import org.rowtown.rms.rrr.client.util.TdiModelValidator;

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
    private final RmlSyncEngine rmlSync;
    private final ScheduledExecutorService scheduler;

    private final String regattaId;
    private final String regattaStartDate;
    private final String timer;
    private boolean autoSyncEnabled;

    /**
     * Create a new Race Timer Client.
     *
     * @param config Configuration for the client
     */
    public RaceTimerClient(ClientConfig config) {
        this.regattaId = config.getRegattaId();
        this.regattaStartDate = config.getRegattaStartDate();
        this.timer = config.getTimer();

        // Initialize local storage
        this.storage = new LocalStorageManager(config.getLocalDatabasePath());

        // Initialize API client
        this.apiClient = new RepositoryClient(config.getServerUrl(), config.getJwtToken());

        // Initialize MQTT listener (client id must be unique per connection)
        String mqttClientId = (regattaId + "-" + timer + "-client").replace(' ', '-');
        this.mqttListener = new NotificationListener(
            config.getMqttBrokerUrl(),
            mqttClientId,
            config.getRegattaId()
        );

        // Initialize sync engines
        this.startListSync = new StartListSyncEngine(storage, apiClient, regattaId, regattaStartDate);
        this.raceResultsSync = new RaceResultsSyncEngine(storage, apiClient, regattaId, regattaStartDate, timer);
        this.rmlSync = new RmlSyncEngine(storage, apiClient, regattaId, regattaStartDate);

        // Initialize scheduler for auto-sync
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.autoSyncEnabled = false;

        // Set up notification handler
        setupNotificationHandler();

        log.info("RaceTimerClient initialized for regatta '{}' on {}, timer role {}",
            regattaId, regattaStartDate, timer);
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
            syncRegattaDefinition();
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
     * @param milestoneId Timing milestone (e.g., "Start Line", "Finish Line")
     * @param author Author
     * @param modelData Serialized EMF model data (raceId is derived from it)
     * @return Saved local document
     */
    public LocalDocument saveRaceResults(String milestoneId, String author, byte[] modelData) {
        // Fail fast: reject models the generated TDI classes cannot load, matching
        // the server's ingest policy, so unusable captures never enter the queue.
        TdiModelValidator.validateLoadable(modelData);

        log.info("Saving race results for milestone: {}", milestoneId);
        LocalDocument doc = raceResultsSync.saveLocal(milestoneId, author, modelData);

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
        TdiModelValidator.validateLoadable(modelData);

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

    // ========== Regatta Definition (RML) Operations ==========

    /**
     * Download the Regatta Definition (RML) for this edition from the server.
     *
     * @return true if a definition was downloaded/refreshed
     */
    public boolean syncRegattaDefinition() {
        log.info("Syncing Regatta Definition");
        return rmlSync.synchronize();
    }

    /**
     * Get the local Regatta Definition (RML), if available.
     */
    public Optional<LocalDocument> getRegattaDefinition() {
        return rmlSync.getLocal();
    }

    /**
     * Save (create or replace) the Regatta Definition locally and push it to the
     * server when online. The regatta key is derived from the RML model.
     *
     * @param modelData Serialized RML model data
     * @param author Author
     * @return Saved local document
     */
    public LocalDocument saveRegattaDefinition(byte[] modelData, String author) {
        // Fail fast: reject models the generated RML classes cannot load.
        RmlModelValidator.validateLoadable(modelData);

        String derivedRegattaId = RmlKeyExtractor.extractRegattaName(modelData);
        String derivedStartDate = RmlKeyExtractor.extractRegattaStartDate(modelData);
        log.info("Saving Regatta Definition for '{}' on {}", derivedRegattaId, derivedStartDate);

        LocalDocument doc = rmlSync.saveLocal(modelData, author, derivedRegattaId, derivedStartDate);

        if (apiClient.isServerReachable()) {
            scheduler.execute(rmlSync::push);
        }
        return doc;
    }

    /**
     * Delete the Regatta Definition locally and on the server.
     *
     * @return true if deleted
     */
    public boolean deleteRegattaDefinition() {
        log.info("Deleting Regatta Definition");
        return rmlSync.delete();
    }

    // ========== Combined Operations ==========

    /**
     * Synchronize everything (Regatta Definition + Start List + Race Results).
     */
    public void syncAll() {
        log.info("Syncing all data");
        syncRegattaDefinition();
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
            log.info("Received change notification: {} ({})", event.eventType, event.documentType);

            // Refresh the affected document when notified of changes.
            if ("START_LIST".equals(event.documentType)) {
                log.info("Refreshing Start List due to server change (version: {})",
                    event.versionNumber);
                startListSync.forceRefresh();
            } else if ("RML".equals(event.documentType)) {
                log.info("Refreshing Regatta Definition due to server change (version: {})",
                    event.versionNumber);
                rmlSync.forceRefresh();
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
        private final String regattaStartDate;   // ISO-8601 (yyyy-MM-dd); part of the regatta key
        private final String timer;              // this timer's role: PRIMARY | FIRST_BACKUP | SECOND_BACKUP
        private final String serverUrl;
        private final String mqttBrokerUrl;
        private final String localDatabasePath;
        private final String jwtToken;

        public ClientConfig(String regattaId, String regattaStartDate, String timer, String serverUrl,
                          String mqttBrokerUrl, String localDatabasePath, String jwtToken) {
            this.regattaId = regattaId;
            this.regattaStartDate = regattaStartDate;
            this.timer = timer;
            this.serverUrl = serverUrl;
            this.mqttBrokerUrl = mqttBrokerUrl;
            this.localDatabasePath = localDatabasePath;
            this.jwtToken = jwtToken;
        }

        public String getRegattaId() { return regattaId; }
        public String getRegattaStartDate() { return regattaStartDate; }
        public String getTimer() { return timer; }
        public String getServerUrl() { return serverUrl; }
        public String getMqttBrokerUrl() { return mqttBrokerUrl; }
        public String getLocalDatabasePath() { return localDatabasePath; }
        public String getJwtToken() { return jwtToken; }
    }
}
