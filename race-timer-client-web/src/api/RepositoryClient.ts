import { DocumentRequest, DocumentResponse } from '../models/ApiTypes';

/**
 * HTTP client for Race Results Repository REST API.
 * Uses browser fetch API for communication.
 */
export class RepositoryClient {
  private readonly serverUrl: string;
  private readonly jwtToken: string;
  private readonly timeoutMs: number = 10000;

  constructor(serverUrl: string, jwtToken: string) {
    this.serverUrl = serverUrl.replace(/\/$/, ''); // Remove trailing slash
    this.jwtToken = jwtToken;
  }

  /**
   * Get the Start List for a regatta edition (name + start date).
   */
  async getStartList(regattaId: string, regattaStartDate: string): Promise<DocumentResponse | null> {
    try {
      const params = new URLSearchParams({
        regattaId,
        regattaStartDate,
        type: 'START_LIST',
        matchType: 'EXACT',
      });
      const response = await this.fetchWithTimeout(
        `${this.serverUrl}/api/v1/documents/search?${params.toString()}`,
        {
          method: 'GET',
          headers: this.getHeaders(),
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const results = await response.json();
      if (results.results && results.results.length > 0) {
        // Fetch the full document (with model data) by id.
        return await this.getDocument(results.results[0].documentId);
      }
      return null;
    } catch (error) {
      console.error('Failed to get Start List', error);
      throw error;
    }
  }

  /**
   * Get a document by ID.
   */
  async getDocument(documentId: number): Promise<DocumentResponse> {
    try {
      const response = await this.fetchWithTimeout(
        `${this.serverUrl}/api/v1/documents/${documentId}`,
        {
          method: 'GET',
          headers: this.getHeaders(),
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error(`Failed to get document ${documentId}`, error);
      throw error;
    }
  }

  /**
   * Create a new document.
   */
  async createDocument(request: DocumentRequest): Promise<DocumentResponse> {
    try {
      const response = await this.fetchWithTimeout(
        `${this.serverUrl}/api/v1/documents`,
        {
          method: 'POST',
          headers: this.getHeaders(),
          body: JSON.stringify(request),
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to create document', error);
      throw error;
    }
  }

  /**
   * Update an existing document.
   */
  async updateDocument(
    documentId: number,
    modelData: string,
    changeDescription: string
  ): Promise<DocumentResponse> {
    try {
      const response = await this.fetchWithTimeout(
        `${this.serverUrl}/api/v1/documents/${documentId}`,
        {
          method: 'PUT',
          headers: this.getHeaders(),
          body: JSON.stringify({
            modelData,
            changeDescription,
          }),
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error(`Failed to update document ${documentId}`, error);
      throw error;
    }
  }

  /**
   * Check if the server is reachable.
   */
  async isServerReachable(): Promise<boolean> {
    try {
      const response = await this.fetchWithTimeout(
        `${this.serverUrl}/actuator/health`,
        {
          method: 'GET',
          headers: this.getHeaders(),
        }
      );

      return response.ok;
    } catch (error) {
      return false;
    }
  }

  /**
   * Get standard headers for API requests.
   */
  private getHeaders(): HeadersInit {
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.jwtToken}`,
    };
  }

  /**
   * Fetch with timeout support.
   */
  private async fetchWithTimeout(
    url: string,
    options: RequestInit
  ): Promise<Response> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);
      return response;
    } catch (error) {
      clearTimeout(timeoutId);

      if (error instanceof Error && error.name === 'AbortError') {
        throw new Error(`Request timeout after ${this.timeoutMs}ms`);
      }

      throw error;
    }
  }
}
