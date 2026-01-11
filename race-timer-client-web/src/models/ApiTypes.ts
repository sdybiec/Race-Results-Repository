/**
 * API request and response types for Race Results Repository.
 */

export interface DocumentResponse {
  documentId: number;
  type: string;
  regattaId: string;
  timerId?: string;
  milestoneId?: string;
  versionType?: string;
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
  timerId?: string;
  milestoneId?: string;
  versionType?: string;
  author: string;
  description?: string;
  tags?: string[];
  metadata?: Record<string, string>;
  modelData: string;
}

export interface NotificationEvent {
  eventType: 'CREATED' | 'UPDATED' | 'DELETED';
  documentType: string;
  documentId: number;
  regattaId: string;
  timerId?: string;
  versionNumber: number;
  timestamp: string;
}

export interface ClientConfig {
  regattaId: string;
  timerId: string;
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
