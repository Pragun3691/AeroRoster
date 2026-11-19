import type { CrewRole } from '../../types/api'

export type QualificationStatus = 'VALID' | 'EXPIRING_SOON' | 'EXPIRED' | 'MISSING'
export interface QualificationRequirement { id: number; aircraftTypeId: number; model: string; manufacturer: string; requiredRole: CrewRole }
export interface DemoCrewReference { id: number; name: string; role: CrewRole }
export interface DemoHeldQualification { id: number; crewId: number; qualificationId: number; aircraftTypeId: number; model: string; requiredRole: CrewRole; validUntil: string | null; version: number }
export interface QualificationRow extends DemoHeldQualification { crewName: string; status: QualificationStatus }

export const demoOperatingDate = '2026-11-18'
export const demoReviewDate = '2026-12-18'
export const roleLabels: Record<CrewRole, string> = { PILOT: 'Pilot', CO_PILOT: 'Co-pilot', CABIN_CREW: 'Cabin crew' }
export const requirements: QualificationRequirement[] = [
  { id: 1, aircraftTypeId: 1, model: 'A320neo', manufacturer: 'Airbus', requiredRole: 'PILOT' },
  { id: 2, aircraftTypeId: 1, model: 'A320neo', manufacturer: 'Airbus', requiredRole: 'CO_PILOT' },
  { id: 3, aircraftTypeId: 1, model: 'A320neo', manufacturer: 'Airbus', requiredRole: 'CABIN_CREW' },
  { id: 4, aircraftTypeId: 2, model: 'ATR 72-600', manufacturer: 'ATR', requiredRole: 'PILOT' },
  { id: 5, aircraftTypeId: 2, model: 'ATR 72-600', manufacturer: 'ATR', requiredRole: 'CO_PILOT' },
  { id: 6, aircraftTypeId: 2, model: 'ATR 72-600', manufacturer: 'ATR', requiredRole: 'CABIN_CREW' },
]
export const crewReferences: DemoCrewReference[] = [
  { id: 101, name: 'Aarav Mehta', role: 'PILOT' }, { id: 102, name: 'Nisha Rao', role: 'CO_PILOT' }, { id: 103, name: 'Sana Kapoor', role: 'CABIN_CREW' }, { id: 104, name: 'Vikram Shah', role: 'PILOT' },
]
export const heldQualifications: DemoHeldQualification[] = [
  { id: 501, crewId: 101, qualificationId: 1, aircraftTypeId: 1, model: 'A320neo', requiredRole: 'PILOT', validUntil: '2027-09-30', version: 4 },
  { id: 502, crewId: 101, qualificationId: 4, aircraftTypeId: 2, model: 'ATR 72-600', requiredRole: 'PILOT', validUntil: '2026-11-29', version: 1 },
  { id: 503, crewId: 102, qualificationId: 2, aircraftTypeId: 1, model: 'A320neo', requiredRole: 'CO_PILOT', validUntil: '2027-04-11', version: 2 },
  { id: 504, crewId: 103, qualificationId: 3, aircraftTypeId: 1, model: 'A320neo', requiredRole: 'CABIN_CREW', validUntil: '2026-12-08', version: 3 },
  { id: 505, crewId: 104, qualificationId: 4, aircraftTypeId: 2, model: 'ATR 72-600', requiredRole: 'PILOT', validUntil: '2026-10-31', version: 7 },
  { id: 506, crewId: 104, qualificationId: 1, aircraftTypeId: 1, model: 'A320neo', requiredRole: 'PILOT', validUntil: null, version: 1 },
]
export function qualificationStatus(validUntil: string | null): Exclude<QualificationStatus, 'MISSING'> { if (validUntil !== null && validUntil < demoOperatingDate) return 'EXPIRED'; if (validUntil !== null && validUntil <= demoReviewDate) return 'EXPIRING_SOON'; return 'VALID' }
export function qualificationRows(): QualificationRow[] { const held = heldQualifications.map(item => ({ ...item, crewName: crewReferences.find(crew => crew.id === item.crewId)!.name, status: qualificationStatus(item.validUntil) })); const missing = crewReferences.flatMap(crew => requirements.filter(requirement => requirement.requiredRole === crew.role && !held.some(item => item.crewId === crew.id && item.qualificationId === requirement.id)).map(requirement => ({ id: -`${crew.id}${requirement.id}`, crewId: crew.id, crewName: crew.name, qualificationId: requirement.id, aircraftTypeId: requirement.aircraftTypeId, model: requirement.model, requiredRole: requirement.requiredRole, validUntil: null, version: 0, status: 'MISSING' as const }))); return [...held, ...missing] }
export const qualificationStatusLabel: Record<QualificationStatus, string> = { VALID: 'Valid', EXPIRING_SOON: 'Expiring soon', EXPIRED: 'Expired', MISSING: 'No qualification' }
