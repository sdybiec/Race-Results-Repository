/**
 * Extracts the race identifier from a TDI (Timing Data Interchange) model.
 *
 * Mirrors the server's derivation so the offline-first client can key/label Race
 * Results by race before syncing. The server is the authority; this is for local
 * records. modelData is base64-encoded XMI; we also tolerate a raw XMI string.
 */
export function extractRaceId(modelData: string): string | undefined {
  if (!modelData) {
    return undefined;
  }

  const candidates: string[] = [];
  if (typeof atob !== 'undefined') {
    try {
      candidates.push(atob(modelData));
    } catch {
      // not valid base64 — fall through to the raw string
    }
  }
  candidates.push(modelData);

  for (const text of candidates) {
    const match = text.match(/raceId="([^"]+)"/);
    if (match && match[1]) {
      return match[1];
    }
  }
  return undefined;
}
