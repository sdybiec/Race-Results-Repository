import { useEffect, useState, useCallback } from 'react';
import { RaceTimerClient } from '../RaceTimerClient';
import { LocalDocument } from '../models/LocalDocument';
import { ClientStatus, SyncResult } from '../models/ApiTypes';

/**
 * Hook to manage Start List synchronization.
 */
export function useStartList(client: RaceTimerClient) {
  const [startList, setStartList] = useState<LocalDocument | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const sync = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const success = await client.syncStartList();
      if (success) {
        const list = client.getStartList();
        setStartList(list);
      }
      return success;
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Unknown error');
      setError(error);
      return false;
    } finally {
      setLoading(false);
    }
  }, [client]);

  const refresh = useCallback(() => {
    const list = client.getStartList();
    setStartList(list);
  }, [client]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { startList, loading, error, sync, refresh };
}

/**
 * Hook to manage Race Results.
 */
export function useRaceResults(client: RaceTimerClient) {
  const [raceResults, setRaceResults] = useState<LocalDocument[]>([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const refresh = useCallback(() => {
    const results = client.getAllRaceResults();
    setRaceResults(results);

    const pending = client.getPendingRaceResultsCount();
    setPendingCount(pending);
  }, [client]);

  const save = useCallback(
    (milestoneId: string, versionType: string, author: string, modelData: string) => {
      try {
        const doc = client.saveRaceResults(milestoneId, versionType, author, modelData);
        refresh();
        return doc;
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Unknown error');
        setError(error);
        throw error;
      }
    },
    [client, refresh]
  );

  const update = useCallback(
    (localId: string, modelData: string) => {
      try {
        const doc = client.updateRaceResults(localId, modelData);
        refresh();
        return doc;
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Unknown error');
        setError(error);
        throw error;
      }
    },
    [client, refresh]
  );

  const sync = useCallback(async (): Promise<SyncResult> => {
    setLoading(true);
    setError(null);
    try {
      const result = await client.syncRaceResults();
      refresh();
      return result;
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Unknown error');
      setError(error);
      throw error;
    } finally {
      setLoading(false);
    }
  }, [client, refresh]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { raceResults, pendingCount, loading, error, save, update, sync, refresh };
}

/**
 * Hook to monitor client status.
 */
export function useClientStatus(client: RaceTimerClient, refreshIntervalMs: number = 30000) {
  const [status, setStatus] = useState<ClientStatus>({
    online: false,
    mqttConnected: false,
    autoSyncEnabled: false,
    pendingRaceResults: 0,
    startListNeedsSync: true,
  });

  const refresh = useCallback(async () => {
    const newStatus = await client.getStatus();
    setStatus(newStatus);
  }, [client]);

  useEffect(() => {
    refresh();

    const interval = setInterval(refresh, refreshIntervalMs);

    return () => clearInterval(interval);
  }, [refresh, refreshIntervalMs]);

  return { status, refresh };
}

/**
 * Hook to manage auto-sync.
 */
export function useAutoSync(client: RaceTimerClient, intervalMinutes: number = 5) {
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    if (enabled) {
      client.enableAutoSync(intervalMinutes);
    } else {
      client.disableAutoSync();
    }

    return () => {
      client.disableAutoSync();
    };
  }, [client, enabled, intervalMinutes]);

  const enable = useCallback(() => setEnabled(true), []);
  const disable = useCallback(() => setEnabled(false), []);
  const toggle = useCallback(() => setEnabled((prev) => !prev), []);

  return { enabled, enable, disable, toggle };
}

/**
 * Hook for storage statistics.
 */
export function useStorageStats(client: RaceTimerClient, refreshIntervalMs: number = 60000) {
  const [stats, setStats] = useState({ documentCount: 0, pendingCount: 0, storageSize: 0 });

  const refresh = useCallback(() => {
    const newStats = client.getStorageStats();
    setStats(newStats);
  }, [client]);

  useEffect(() => {
    refresh();

    const interval = setInterval(refresh, refreshIntervalMs);

    return () => clearInterval(interval);
  }, [refresh, refreshIntervalMs]);

  return { stats, refresh };
}
