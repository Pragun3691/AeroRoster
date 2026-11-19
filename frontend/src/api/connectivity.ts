import { referenceService } from './services/referenceService'
import { normalizeApiError, type NormalizedApiError } from './errors'
export type BackendConnectivity = { reachable: true } | { reachable: false; error: NormalizedApiError }
let cached: Promise<BackendConnectivity> | undefined
/** Probe an existing harmless reference endpoint once per application session. This is API reachability only, not database health. */
export function checkBackendConnectivity(): Promise<BackendConnectivity> {
  cached ??= referenceService.airports('').then(() => ({ reachable: true as const })).catch((error: unknown) => ({ reachable: false as const, error: normalizeApiError(error) }))
  return cached
}
export function resetBackendConnectivityCacheForTests(): void { cached = undefined }
