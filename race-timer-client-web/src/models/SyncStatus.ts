/**
 * Synchronization status for local documents.
 */
export enum SyncStatus {
  /** Document is synchronized with server */
  SYNCED = 'SYNCED',

  /** Document has local changes pending upload */
  PENDING = 'PENDING',

  /** Document is currently being synchronized */
  SYNCING = 'SYNCING',

  /** Synchronization failed (will retry) */
  FAILED = 'FAILED',

  /** Conflict detected between local and server versions */
  CONFLICT = 'CONFLICT'
}
