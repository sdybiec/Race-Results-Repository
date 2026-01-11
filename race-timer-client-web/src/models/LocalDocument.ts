import { SyncStatus } from './SyncStatus';

/**
 * Represents a document stored locally in the browser.
 */
export interface LocalDocument {
  /** Local ID (unique in browser storage) */
  localId: string;

  /** Server document ID (null if not yet uploaded) */
  serverId?: number;

  /** Regatta identifier */
  regattaId: string;

  /** Timer identifier (for Race Results only) */
  timerId?: string;

  /** Milestone identifier (for Race Results: "start", "finish", etc.) */
  milestoneId?: string;

  /** Document type: "START_LIST" or "RACE_RESULTS" */
  documentType: 'START_LIST' | 'RACE_RESULTS';

  /** Version type: "primary", "firstBackup", "secondBackup" */
  versionType?: string;

  /** Document author */
  author?: string;

  /** Document description */
  description?: string;

  /** Local version number (increments with each local change) */
  localVersion: number;

  /** Server version number (last known server version) */
  serverVersion?: number;

  /** Current synchronization status */
  syncStatus: SyncStatus;

  /** Timestamp when document was created locally */
  createdAt: string;

  /** Timestamp when document was last modified locally */
  modifiedAt: string;

  /** Timestamp of last successful sync with server */
  lastSyncedAt?: string;

  /** Error message from last failed sync attempt */
  lastSyncError?: string;

  /** Number of sync retry attempts */
  retryCount: number;

  /** Serialized EMF model data (base64 encoded) */
  modelData: string;

  /** Serialization format (JSON or XMI) */
  serializationFormat: 'JSON' | 'XMI';
}
