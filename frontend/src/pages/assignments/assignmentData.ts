export type AssignmentStatus = 'ACTIVE' | 'CANCELLED'
export type AssignmentRole = 'PILOT' | 'COPILOT' | 'CABIN_CREW'
export interface DemoAssignment { id: number; crewId: number; crewName: string; flightId: number; flightNumber: string; assignedRole: AssignmentRole; dutyStartTime: string; dutyEndTime: string; status: AssignmentStatus; createdAt: string; version: number }
export interface DemoCoverage { flightId: number; flightNumber: string; departureTime: string; minimumStaffingSatisfied: boolean; reasons: Array<{ code: string; explanation: string }> }
export const roleLabel: Record<AssignmentRole, string> = { PILOT: 'Pilot', COPILOT: 'Co-pilot', CABIN_CREW: 'Cabin crew' }
export const assignments: DemoAssignment[] = [
  { id: 701, crewId: 101, crewName: 'Aarav Mehta', flightId: 301, flightNumber: 'AR 218', assignedRole: 'PILOT', dutyStartTime: '2026-11-18T05:45:00Z', dutyEndTime: '2026-11-18T09:30:00Z', status: 'ACTIVE', createdAt: '2026-11-17T10:20:00Z', version: 2 },
  { id: 702, crewId: 102, crewName: 'Nisha Rao', flightId: 301, flightNumber: 'AR 218', assignedRole: 'COPILOT', dutyStartTime: '2026-11-18T05:45:00Z', dutyEndTime: '2026-11-18T09:30:00Z', status: 'ACTIVE', createdAt: '2026-11-17T10:22:00Z', version: 1 },
  { id: 703, crewId: 103, crewName: 'Sana Kapoor', flightId: 302, flightNumber: 'AR 641', assignedRole: 'CABIN_CREW', dutyStartTime: '2026-11-18T08:20:00Z', dutyEndTime: '2026-11-18T11:35:00Z', status: 'ACTIVE', createdAt: '2026-11-17T11:00:00Z', version: 1 },
  { id: 704, crewId: 104, crewName: 'Vikram Shah', flightId: 304, flightNumber: 'AR 110', assignedRole: 'PILOT', dutyStartTime: '2026-11-17T16:15:00Z', dutyEndTime: '2026-11-17T19:15:00Z', status: 'CANCELLED', createdAt: '2026-11-16T09:00:00Z', version: 3 },
]
export const coverage: DemoCoverage[] = [
  { flightId: 301, flightNumber: 'AR 218', departureTime: '2026-11-18T06:45:00Z', minimumStaffingSatisfied: false, reasons: [{ code: 'MISSING_CABIN_CREW', explanation: 'Flight needs 4 additional CABIN_CREW assignment(s)' }] },
  { flightId: 302, flightNumber: 'AR 641', departureTime: '2026-11-18T09:20:00Z', minimumStaffingSatisfied: false, reasons: [{ code: 'MISSING_PILOT', explanation: 'Flight needs 1 additional PILOT assignment(s)' }, { code: 'MISSING_COPILOT', explanation: 'Flight needs 1 additional COPILOT assignment(s)' }, { code: 'MISSING_CABIN_CREW', explanation: 'Flight needs 3 additional CABIN_CREW assignment(s)' }] },
]
export const crewChoices = [{ id: 101, name: 'Aarav Mehta' }, { id: 102, name: 'Nisha Rao' }, { id: 103, name: 'Sana Kapoor' }, { id: 104, name: 'Vikram Shah' }]
export const flightChoices = [{ id: 301, number: 'AR 218' }, { id: 302, number: 'AR 641' }]
export const demoEligibility = { eligible: true, reasons: [] as Array<{ code: string; explanation: string }>, policyNotice: 'Demo response only. The backend rechecks eligibility inside its assignment transaction.' }
