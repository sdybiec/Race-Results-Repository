import React, { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { RaceTimerClient } from '../RaceTimerClient';
import { LocalDocument } from '../models/LocalDocument';
import { ClientConfig, ClientStatus, SyncResult } from '../models/ApiTypes';

/**
 * Context value provided to React components.
 */
export interface RaceTimerContextValue {
  client: RaceTimerClient;
  status: ClientStatus;
  startList: LocalDocument | null;
  raceResults: LocalDocument[];

  // Actions
  syncStartList: () => Promise<boolean>;
  syncRaceResults: () => Promise<SyncResult>;
  syncAll: () => Promise<void>;
  saveRaceResults: (milestoneId: string, author: string, modelData: string) => LocalDocument;
  updateRaceResults: (localId: string, modelData: string) => LocalDocument;
  refreshStatus: () => Promise<void>;
}

const RaceTimerContext = createContext<RaceTimerContextValue | null>(null);

/**
 * Props for RaceTimerProvider.
 */
export interface RaceTimerProviderProps {
  config: ClientConfig;
  autoSyncIntervalMinutes?: number;
  children: ReactNode;
}

/**
 * Provider component that makes RaceTimerClient available to React components.
 */
export const RaceTimerProvider: React.FC<RaceTimerProviderProps> = ({
  config,
  autoSyncIntervalMinutes = 5,
  children,
}) => {
  const [client] = useState(() => new RaceTimerClient(config));
  const [status, setStatus] = useState<ClientStatus>({
    online: false,
    mqttConnected: false,
    autoSyncEnabled: false,
    pendingRaceResults: 0,
    startListNeedsSync: true,
  });
  const [startList, setStartList] = useState<LocalDocument | null>(null);
  const [raceResults, setRaceResults] = useState<LocalDocument[]>([]);

  // Initialize client on mount
  useEffect(() => {
    const initialize = async () => {
      await client.start();
      client.enableAutoSync(autoSyncIntervalMinutes);
      await refreshStatus();
      refreshData();
    };

    initialize();

    // Set up periodic status refresh
    const statusInterval = setInterval(() => {
      refreshStatus();
      refreshData();
    }, 30000); // Every 30 seconds

    // Cleanup on unmount
    return () => {
      clearInterval(statusInterval);
      client.shutdown();
    };
  }, [client, autoSyncIntervalMinutes]);

  /**
   * Refresh client status.
   */
  const refreshStatus = async () => {
    const newStatus = await client.getStatus();
    setStatus(newStatus);
  };

  /**
   * Refresh local data.
   */
  const refreshData = () => {
    setStartList(client.getStartList());
    setRaceResults(client.getAllRaceResults());
  };

  /**
   * Sync Start List and refresh data.
   */
  const syncStartList = async () => {
    const success = await client.syncStartList();
    refreshData();
    await refreshStatus();
    return success;
  };

  /**
   * Sync Race Results and refresh data.
   */
  const syncRaceResults = async () => {
    const result = await client.syncRaceResults();
    refreshData();
    await refreshStatus();
    return result;
  };

  /**
   * Sync all and refresh data.
   */
  const syncAll = async () => {
    await client.syncAll();
    refreshData();
    await refreshStatus();
  };

  /**
   * Save Race Results and refresh data.
   */
  const saveRaceResults = (
    milestoneId: string,
    author: string,
    modelData: string
  ) => {
    const doc = client.saveRaceResults(milestoneId, author, modelData);
    refreshData();
    refreshStatus();
    return doc;
  };

  /**
   * Update Race Results and refresh data.
   */
  const updateRaceResults = (localId: string, modelData: string) => {
    const doc = client.updateRaceResults(localId, modelData);
    refreshData();
    refreshStatus();
    return doc;
  };

  const contextValue: RaceTimerContextValue = {
    client,
    status,
    startList,
    raceResults,
    syncStartList,
    syncRaceResults,
    syncAll,
    saveRaceResults,
    updateRaceResults,
    refreshStatus,
  };

  return (
    <RaceTimerContext.Provider value={contextValue}>
      {children}
    </RaceTimerContext.Provider>
  );
};

/**
 * Hook to access RaceTimerClient from React components.
 */
export const useRaceTimer = (): RaceTimerContextValue => {
  const context = useContext(RaceTimerContext);
  if (!context) {
    throw new Error('useRaceTimer must be used within a RaceTimerProvider');
  }
  return context;
};
