import { SyncStatus } from './SyncStatus';
import { TimerRole } from './ApiTypes';

/**
 * Represents a document stored locally in the browser.
 */
export interface LocalDocument {
  /** Local ID (unique in browser storage) */
  localId: string;

  /** Server document ID (null if not yet uploaded) */
  serverId?: number;

  /** Regatta name */
  regattaId: string;

  /** Regatta start date (ISO-8601, yyyy-MM-dd); part of the regatta key */
  regattaStartDate: string;

  /** Race identifier (Race Results only), derived from the model */
  raceId?: string;

  /** Milestone identifier (Race Results: "Start Line", "Finish Line", etc.) */
  milestoneId?: string;

  /** Document type: "START_LIST" or "RACE_RESULTS" */
  documentType: 'START_LIST' | 'RACE_RESULTS';

  /** Timer role (Race Results only) */
  timer?: TimerRole;

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
