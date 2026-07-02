/**
 * Race Timer Client - Web Edition
 * Offline-first client library for React applications
 */

// Main client
export { RaceTimerClient } from './RaceTimerClient';

// React integration
export { RaceTimerProvider, useRaceTimer } from './react/RaceTimerContext';
export type { RaceTimerContextValue, RaceTimerProviderProps } from './react/RaceTimerContext';

// React hooks
export {
  useStartList,
  useRaceResults,
  useClientStatus,
  useAutoSync,
  useStorageStats,
} from './react/hooks';

// Models
export { SyncStatus } from './models/SyncStatus';
export type { LocalDocument } from './models/LocalDocument';
export type {
  ClientConfig,
  ClientStatus,
  SyncResult,
  DocumentRequest,
  DocumentResponse,
  NotificationEvent,
  TimerRole,
} from './models/ApiTypes';

// Core components (for advanced usage)
export { LocalStorageManager } from './storage/LocalStorageManager';
export { RepositoryClient } from './api/RepositoryClient';
export { NotificationListener } from './mqtt/NotificationListener';
export type { StartListChangeHandler } from './mqtt/NotificationListener';
export { StartListSyncEngine } from './sync/StartListSyncEngine';
export { RaceResultsSyncEngine } from './sync/RaceResultsSyncEngine';
