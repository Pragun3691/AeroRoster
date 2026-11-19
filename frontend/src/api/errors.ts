import axios from 'axios'
import type { ApiError, ApiFieldError, EligibilityDecision } from '../types/api'
export type ApiFailureKind = 'validation' | 'not-found' | 'conflict' | 'stale-version' | 'precondition' | 'server' | 'network' | 'timeout' | 'unexpected'
export interface NormalizedApiError { kind: ApiFailureKind; status: number | null; code: string | null; message: string; fieldErrors: ApiFieldError[]; eligibility: EligibilityDecision | null }
const fallback = (kind: ApiFailureKind, message: string): NormalizedApiError => ({ kind, status: null, code: null, message, fieldErrors: [], eligibility: null })
function isApiError(value: unknown): value is ApiError { return typeof value === 'object' && value !== null && typeof (value as ApiError).code === 'string' && typeof (value as ApiError).message === 'string' && Array.isArray((value as ApiError).fieldErrors) }
export function normalizeApiError(error: unknown): NormalizedApiError {
  if (!axios.isAxiosError(error)) return fallback('unexpected', 'An unexpected error occurred.')
  if (error.code === 'ECONNABORTED') return fallback('timeout', 'The request timed out. Try again.')
  if (!error.response) return fallback('network', 'The backend could not be reached.')
  const api = isApiError(error.response.data) ? error.response.data : null
  const status = error.response.status
  const kind: ApiFailureKind = status === 400 ? 'validation' : status === 404 ? 'not-found' : status === 428 ? 'precondition' : status === 500 ? 'server' : status === 409 && api?.code === 'STALE_VERSION' ? 'stale-version' : status === 409 ? 'conflict' : 'unexpected'
  return { kind, status, code: api?.code ?? null, message: api?.message ?? 'The server returned an unexpected response.', fieldErrors: api?.fieldErrors ?? [], eligibility: api?.eligibility ?? null }
}
export function apiErrorMessage(error: unknown): string { return normalizeApiError(error).message }
