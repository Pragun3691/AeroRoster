import axios from 'axios'
import type { ApiError } from '../types/api'

export const apiClient = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL, headers: { Accept: 'application/json' } })

export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) return error.response?.data.message ?? error.message
  return 'An unexpected error occurred.'
}

/** Future conditional mutation helper: preserve the original resource snapshot, never refresh it silently. */
export const expectedVersion = (id: number, version: number) => `${id}:${version}`
