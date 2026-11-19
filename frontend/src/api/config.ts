const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
/** Defaults to the documented loopback API for local development; production must set VITE_API_BASE_URL. */
export const API_BASE_URL = configuredBaseUrl || 'http://127.0.0.1:8080/api/v1'
export const API_TIMEOUT_MS = 10_000
