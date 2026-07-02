import { LocalStorageManager } from '../storage/LocalStorageManager';
import { LocalDocument } from '../models/LocalDocument';
import { SyncStatus } from '../models/SyncStatus';

describe('LocalStorageManager', () => {
  let storage: LocalStorageManager;

  beforeEach(() => {
    storage = new LocalStorageManager();
    localStorage.clear();
  });

  describe('save and findById', () => {
    it('should save a new document and assign a local ID', () => {
      const doc: LocalDocument = {
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        timer: 'PRIMARY',
        milestoneId: 'finish',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'test-data',
        serializationFormat: 'JSON',
      };

      const saved = storage.save(doc);

      expect(saved.localId).toBeTruthy();
      expect(saved.localId.startsWith('doc_')).toBe(true);
      expect(saved.regattaId).toBe('TEST2025');
    });

    it('should retrieve a document by local ID', () => {
      const doc: LocalDocument = {
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'START_LIST',
        localVersion: 1,
        syncStatus: SyncStatus.SYNCED,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'start-list-data',
        serializationFormat: 'JSON',
      };

      const saved = storage.save(doc);
      const found = storage.findById(saved.localId);

      expect(found).toBeTruthy();
      expect(found?.localId).toBe(saved.localId);
      expect(found?.regattaId).toBe('TEST2025');
      expect(found?.documentType).toBe('START_LIST');
    });

    it('should return null when document not found', () => {
      const found = storage.findById('non-existent-id');
      expect(found).toBeNull();
    });

    it('should update an existing document', () => {
      const doc: LocalDocument = {
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'original-data',
        serializationFormat: 'JSON',
      };

      const saved = storage.save(doc);

      // Update the document
      saved.modelData = 'updated-data';
      saved.localVersion = 2;

      const updated = storage.save(saved);

      expect(updated.localId).toBe(saved.localId);
      expect(updated.modelData).toBe('updated-data');
      expect(updated.localVersion).toBe(2);

      // Verify it's actually updated in storage
      const found = storage.findById(saved.localId);
      expect(found?.modelData).toBe('updated-data');
    });
  });

  describe('findByRegattaAndType', () => {
    it('should find documents by regatta ID and type', () => {
      // Save multiple documents
      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'START_LIST',
        localVersion: 1,
        syncStatus: SyncStatus.SYNCED,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'start-list',
        serializationFormat: 'JSON',
      });

      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        timer: 'PRIMARY',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'results-1',
        serializationFormat: 'JSON',
      });

      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        timer: 'FIRST_BACKUP',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'results-2',
        serializationFormat: 'JSON',
      });

      storage.save({
        localId: '',
        regattaId: 'OTHER2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'other-results',
        serializationFormat: 'JSON',
      });

      // Find Start List
      const startLists = storage.findByRegattaAndType('TEST2025', 'START_LIST');
      expect(startLists).toHaveLength(1);
      expect(startLists[0].documentType).toBe('START_LIST');

      // Find Race Results
      const raceResults = storage.findByRegattaAndType('TEST2025', 'RACE_RESULTS');
      expect(raceResults).toHaveLength(2);
      expect(raceResults.every((doc) => doc.documentType === 'RACE_RESULTS')).toBe(true);

      // Find from other regatta
      const otherResults = storage.findByRegattaAndType('OTHER2025', 'RACE_RESULTS');
      expect(otherResults).toHaveLength(1);
    });

    it('should return empty array when no documents match', () => {
      const results = storage.findByRegattaAndType('NONEXISTENT', 'START_LIST');
      expect(results).toEqual([]);
    });
  });

  describe('findPendingSync', () => {
    it('should find documents with PENDING status', () => {
      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'pending-1',
        serializationFormat: 'JSON',
      });

      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.SYNCED,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'synced',
        serializationFormat: 'JSON',
      });

      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.FAILED,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 1,
        modelData: 'failed',
        serializationFormat: 'JSON',
      });

      const pending = storage.findPendingSync();

      expect(pending).toHaveLength(2);
      expect(pending.some((doc) => doc.syncStatus === SyncStatus.PENDING)).toBe(true);
      expect(pending.some((doc) => doc.syncStatus === SyncStatus.FAILED)).toBe(true);
      expect(pending.every((doc) => doc.syncStatus !== SyncStatus.SYNCED)).toBe(true);
    });

    it('should return empty array when no pending documents', () => {
      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'START_LIST',
        localVersion: 1,
        syncStatus: SyncStatus.SYNCED,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'synced',
        serializationFormat: 'JSON',
      });

      const pending = storage.findPendingSync();
      expect(pending).toEqual([]);
    });
  });

  describe('delete', () => {
    it('should delete a document by local ID', () => {
      const doc: LocalDocument = {
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.SYNCED,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'test-data',
        serializationFormat: 'JSON',
      };

      const saved = storage.save(doc);
      const deleted = storage.delete(saved.localId);

      expect(deleted).toBe(true);

      const found = storage.findById(saved.localId);
      expect(found).toBeNull();
    });

    it('should return false when document does not exist', () => {
      const deleted = storage.delete('non-existent-id');
      expect(deleted).toBe(false);
    });
  });

  describe('clear', () => {
    it('should clear all documents from storage', () => {
      // Save multiple documents
      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'START_LIST',
        localVersion: 1,
        syncStatus: SyncStatus.SYNCED,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'start-list',
        serializationFormat: 'JSON',
      });

      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'results',
        serializationFormat: 'JSON',
      });

      const statsBefore = storage.getStats();
      expect(statsBefore.documentCount).toBe(2);

      storage.clear();

      const statsAfter = storage.getStats();
      expect(statsAfter.documentCount).toBe(0);
      expect(statsAfter.pendingCount).toBe(0);
    });
  });

  describe('getStats', () => {
    it('should return correct statistics', () => {
      // Initially empty
      const emptyStats = storage.getStats();
      expect(emptyStats.documentCount).toBe(0);
      expect(emptyStats.pendingCount).toBe(0);

      // Add some documents
      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'START_LIST',
        localVersion: 1,
        syncStatus: SyncStatus.SYNCED,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'start-list',
        serializationFormat: 'JSON',
      });

      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'results-1',
        serializationFormat: 'JSON',
      });

      storage.save({
        localId: '',
        regattaId: 'TEST2025',
        regattaStartDate: '2024-05-17',
        documentType: 'RACE_RESULTS',
        localVersion: 1,
        syncStatus: SyncStatus.PENDING,
        createdAt: new Date().toISOString(),
        modifiedAt: new Date().toISOString(),
        retryCount: 0,
        modelData: 'results-2',
        serializationFormat: 'JSON',
      });

      const stats = storage.getStats();
      expect(stats.documentCount).toBe(3);
      expect(stats.pendingCount).toBe(2);
      expect(stats.storageSize).toBeGreaterThan(0);
    });
  });
});
