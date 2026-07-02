/**
 * API request and response types for Race Results Repository.
 */

/** Timing redundancy role that produced a Race Results document. */
export type TimerRole = 'PRIMARY' | 'FIRST_BACKUP' | 'SECOND_BACKUP';

export interface DocumentResponse {
  documentId: number;
  type: string;
  regattaId: string;
  /** Regatta start date (ISO-8601, yyyy-MM-dd); part of the regatta key. */
  regattaStartDate: string;
  /** Race Results only; derived from the model server-side. */
  raceId?: string;
  milestoneId?: string;
  timer?: TimerRole;
  author: string;
  description?: string;
  latestVersion: number;
  modelData: string;
  serializationFormat: string;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentRequest {
  type: string;
  regattaId: string;
  regattaStartDate: string;
  /** Race Results only; part of its key. */
  milestoneId?: string;
  timer?: TimerRole;
  author: string;
  description?: string;
  tags?: string[];
  metadata?: Record<string, string>;
  modelData: string;
  // raceId is derived server-side from modelData; not sent.
}

export interface NotificationEvent {
  eventType: 'CREATED' | 'UPDATED' | 'DELETED';
  documentType: string;
  documentId: number;
  regattaId: string;
  /** For Race Results, the timer role segment of the topic. */
  timer?: string;
  versionNumber: number;
  timestamp: string;
}

export interface ClientConfig {
  regattaId: string;
  /** Regatta start date (ISO-8601, yyyy-MM-dd). */
  regattaStartDate: string;
  /** This client's fixed timer role. */
  timer: TimerRole;
  serverUrl: string;
  mqttBrokerUrl: string;
  jwtToken: string;
}

export interface ClientStatus {
  online: boolean;
  mqttConnected: boolean;
  autoSyncEnabled: boolean;
  pendingRaceResults: number;
  startListNeedsSync: boolean;
  minutesSinceLastStartListSync?: number;
}

export interface SyncResult {
  totalDocuments: number;
  syncedDocuments: number;
  failedDocuments: number;
  offline: boolean;
  error?: string;
}
