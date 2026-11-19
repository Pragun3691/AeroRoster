import type { VersionedResource } from '../types/api'
export const EXPECTED_VERSION_HEADER = 'X-Expected-Version'
export function expectedVersion(id: number, version: number): string {
  if (!Number.isSafeInteger(id) || id < 1 || !Number.isSafeInteger(version) || version < 0) throw new Error('Expected-version requires a positive resource id and nonnegative version.')
  return `${id}:${version}`
}
export function expectedVersionHeaders(resource: VersionedResource): Record<string, string> { return { [EXPECTED_VERSION_HEADER]: expectedVersion(resource.id, resource.version) } }
