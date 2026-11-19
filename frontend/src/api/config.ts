const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
/** Same-origin default. The production Maven build explicitly supplies this value. */
export const API_BASE_URL = configuredBaseUrl || '/api/v1'
export const API_TIMEOUT_MS = 10_000
