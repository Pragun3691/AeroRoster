export type FlightStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'

/** Local visual-development data shaped after the read-only FlightView contract. */
export interface DemoFlight { id: number; flightNumber: string; sourceAirportId: number; sourceIataCode: string; destinationAirportId: number; destinationIataCode: string; aircraftId: number; aircraftRegistration: string; aircraftTypeId: number; departureTime: string; arrivalTime: string; status: FlightStatus; version: number }
export interface FlightReference { id: number; label: string }
export const airportReferences: FlightReference[] = [{ id: 1, label: 'DEL — Delhi' }, { id: 2, label: 'BOM — Mumbai' }, { id: 3, label: 'BLR — Bengaluru' }, { id: 4, label: 'HYD — Hyderabad' }]
export const aircraftReferences: FlightReference[] = [{ id: 201, label: 'VT-AXR — A320neo' }, { id: 202, label: 'VT-ARC — A320neo' }, { id: 203, label: 'VT-ARJ — ATR 72-600' }]
export const demoFlights: DemoFlight[] = [
  { id: 301, flightNumber: 'AR 218', sourceAirportId: 1, sourceIataCode: 'DEL', destinationAirportId: 2, destinationIataCode: 'BOM', aircraftId: 201, aircraftRegistration: 'VT-AXR', aircraftTypeId: 1, departureTime: '2026-11-18T06:45:00Z', arrivalTime: '2026-11-18T09:00:00Z', status: 'SCHEDULED', version: 4 },
  { id: 302, flightNumber: 'AR 641', sourceAirportId: 2, sourceIataCode: 'BOM', destinationAirportId: 3, destinationIataCode: 'BLR', aircraftId: 202, aircraftRegistration: 'VT-ARC', aircraftTypeId: 1, departureTime: '2026-11-18T09:20:00Z', arrivalTime: '2026-11-18T11:05:00Z', status: 'SCHEDULED', version: 2 },
  { id: 303, flightNumber: 'AR 719', sourceAirportId: 3, sourceIataCode: 'BLR', destinationAirportId: 1, destinationIataCode: 'DEL', aircraftId: 201, aircraftRegistration: 'VT-AXR', aircraftTypeId: 1, departureTime: '2026-11-18T14:35:00Z', arrivalTime: '2026-11-18T17:20:00Z', status: 'SCHEDULED', version: 1 },
  { id: 304, flightNumber: 'AR 110', sourceAirportId: 4, sourceIataCode: 'HYD', destinationAirportId: 2, destinationIataCode: 'BOM', aircraftId: 203, aircraftRegistration: 'VT-ARJ', aircraftTypeId: 2, departureTime: '2026-11-17T17:15:00Z', arrivalTime: '2026-11-17T18:45:00Z', status: 'COMPLETED', version: 7 },
  { id: 305, flightNumber: 'AR 808', sourceAirportId: 1, sourceIataCode: 'DEL', destinationAirportId: 4, destinationIataCode: 'HYD', aircraftId: 202, aircraftRegistration: 'VT-ARC', aircraftTypeId: 1, departureTime: '2026-11-19T08:10:00Z', arrivalTime: '2026-11-19T10:20:00Z', status: 'CANCELLED', version: 3 },
]
export const flightStatusLabel: Record<FlightStatus, string> = { SCHEDULED: 'Scheduled', COMPLETED: 'Completed', CANCELLED: 'Cancelled' }
