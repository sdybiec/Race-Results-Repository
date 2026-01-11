import { LocalDocument } from '../models/LocalDocument';
import { SyncStatus } from '../models/SyncStatus';

/**
 * Manages document persistence in browser LocalStorage.
 *
 * Storage structure:
 * - race-timer:documents - Map of all documents keyed by localId
 * - race-timer:index:regatta:{regattaId} - Array of localIds for regatta
 * - race-timer:index:pending - Array of localIds with pending sync
 */
export class LocalStorageManager {
  private readonly DOCUMENTS_KEY = 'race-timer:documents';
  private readonly INDEX_PREFIX = 'race-timer:index:';

  /**
   * Save or update a document in LocalStorage.
   */
  save(document: LocalDocument): LocalDocument {
    // Ensure document has a local ID
    if (!document.localId) {
      document.localId = this.generateId();
    }

    // Update modified timestamp
    document.modifiedAt = new Date().toISOString();

    // Get all documents
    const documents = this.getAllDocuments();
    documents[document.localId] = document;

    // Save back to storage
    this.setItem(this.DOCUMENTS_KEY, JSON.stringify(documents));

    // Update indexes
    this.updateIndexes(document);

    return document;
  }

  /**
   * Find a document by its local ID.
   */
  findById(localId: string): LocalDocument | null {
    const documents = this.getAllDocuments();
    return documents[localId] || null;
  }

  /**
   * Find documents by regatta ID and document type.
   */
  findByRegattaAndType(regattaId: string, documentType: string): LocalDocument[] {
    const indexKey = `${this.INDEX_PREFIX}regatta:${regattaId}`;
    const localIds = this.getIndexedIds(indexKey);

    const documents = this.getAllDocuments();
    return localIds
      .map(id => documents[id])
      .filter(doc => doc && doc.documentType === documentType);
  }

  /**
   * Find all documents with pending synchronization.
   */
  findPendingSync(): LocalDocument[] {
    const indexKey = `${this.INDEX_PREFIX}pending`;
    const localIds = this.getIndexedIds(indexKey);

    const documents = this.getAllDocuments();
    return localIds
      .map(id => documents[id])
      .filter(doc => doc && (
        doc.syncStatus === SyncStatus.PENDING ||
        doc.syncStatus === SyncStatus.FAILED
      ));
  }

  /**
   * Delete a document by its local ID.
   */
  delete(localId: string): boolean {
    const documents = this.getAllDocuments();
    const document = documents[localId];

    if (!document) {
      return false;
    }

    delete documents[localId];
    this.setItem(this.DOCUMENTS_KEY, JSON.stringify(documents));

    // Remove from indexes
    this.removeFromIndexes(document);

    return true;
  }

  /**
   * Clear all documents from storage.
   */
  clear(): void {
    const keysToRemove: string[] = [];

    // Find all race-timer keys
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith('race-timer:')) {
        keysToRemove.push(key);
      }
    }

    // Remove all race-timer keys
    keysToRemove.forEach(key => localStorage.removeItem(key));
  }

  /**
   * Get storage usage statistics.
   */
  getStats(): { documentCount: number; pendingCount: number; storageSize: number } {
    const documents = this.getAllDocuments();
    const documentCount = Object.keys(documents).length;

    const pending = this.findPendingSync();
    const pendingCount = pending.length;

    // Estimate storage size
    const documentsJson = this.getItem(this.DOCUMENTS_KEY) || '';
    const storageSize = new Blob([documentsJson]).size;

    return { documentCount, pendingCount, storageSize };
  }

  /**
   * Get all documents from storage.
   */
  private getAllDocuments(): Record<string, LocalDocument> {
    const json = this.getItem(this.DOCUMENTS_KEY);
    if (!json) {
      return {};
    }

    try {
      return JSON.parse(json);
    } catch (error) {
      console.error('Failed to parse documents from LocalStorage', error);
      return {};
    }
  }

  /**
   * Update indexes for a document.
   */
  private updateIndexes(document: LocalDocument): void {
    // Regatta index
    const regattaIndexKey = `${this.INDEX_PREFIX}regatta:${document.regattaId}`;
    this.addToIndex(regattaIndexKey, document.localId);

    // Pending sync index
    const pendingIndexKey = `${this.INDEX_PREFIX}pending`;
    if (document.syncStatus === SyncStatus.PENDING || document.syncStatus === SyncStatus.FAILED) {
      this.addToIndex(pendingIndexKey, document.localId);
    } else {
      this.removeFromIndex(pendingIndexKey, document.localId);
    }
  }

  /**
   * Remove document from all indexes.
   */
  private removeFromIndexes(document: LocalDocument): void {
    // Regatta index
    const regattaIndexKey = `${this.INDEX_PREFIX}regatta:${document.regattaId}`;
    this.removeFromIndex(regattaIndexKey, document.localId);

    // Pending sync index
    const pendingIndexKey = `${this.INDEX_PREFIX}pending`;
    this.removeFromIndex(pendingIndexKey, document.localId);
  }

  /**
   * Add an ID to an index.
   */
  private addToIndex(indexKey: string, localId: string): void {
    const ids = this.getIndexedIds(indexKey);
    if (!ids.includes(localId)) {
      ids.push(localId);
      this.setItem(indexKey, JSON.stringify(ids));
    }
  }

  /**
   * Remove an ID from an index.
   */
  private removeFromIndex(indexKey: string, localId: string): void {
    const ids = this.getIndexedIds(indexKey);
    const filtered = ids.filter(id => id !== localId);
    if (filtered.length !== ids.length) {
      this.setItem(indexKey, JSON.stringify(filtered));
    }
  }

  /**
   * Get IDs from an index.
   */
  private getIndexedIds(indexKey: string): string[] {
    const json = this.getItem(indexKey);
    if (!json) {
      return [];
    }

    try {
      return JSON.parse(json);
    } catch (error) {
      console.error(`Failed to parse index ${indexKey}`, error);
      return [];
    }
  }

  /**
   * Generate a unique document ID.
   */
  private generateId(): string {
    return `doc_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Wrapper for localStorage.getItem with error handling.
   */
  private getItem(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch (error) {
      console.error(`Failed to read from LocalStorage: ${key}`, error);
      return null;
    }
  }

  /**
   * Wrapper for localStorage.setItem with error handling.
   */
  private setItem(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'QuotaExceededError') {
        console.error('LocalStorage quota exceeded', error);
        // Could implement cleanup strategy here
      } else {
        console.error(`Failed to write to LocalStorage: ${key}`, error);
      }
    }
  }
}
