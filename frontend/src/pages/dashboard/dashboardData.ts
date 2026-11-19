export type DashboardTone = 'success' | 'warning' | 'error' | 'info' | 'neutral'
export type FlightFilter = 'all' | 'scheduled' | 'attention'

export interface DemoFlight { id: string; number: string; origin: string; destination: string; aircraft: string; departure: string; status: string; tone: DashboardTone; coverage: string; attention: boolean }
export interface AlertItem { id: string; kind: 'qualification' | 'coverage' | 'conflict' | 'maintenance'; title: string; detail: string; tone: DashboardTone }
export interface ActivityItem { id: string; time: string; category: string; detail: string; actor: string }

export const dashboardDate = '18 Nov 2026'
export const operationalMetrics = [
  { label: 'Total crew', value: '42', detail: 'Rostered personnel', tone: 'neutral' as const },
  { label: 'Available crew', value: '31', detail: 'Clear for sample duties', tone: 'success' as const },
  { label: 'Active aircraft', value: '8', detail: 'of 10 sample fleet', tone: 'info' as const },
  { label: 'Scheduled flights', value: '12', detail: 'In current UTC day', tone: 'info' as const },
  { label: 'Active assignments', value: '27', detail: 'Sample active duty links', tone: 'neutral' as const },
]
export const demoFlights: DemoFlight[] = [
  { id: 'f1', number: 'AR 218', origin: 'DEL', destination: 'BOM', aircraft: 'VT-AXR · A320neo', departure: '06:45', status: 'Scheduled', tone: 'info', coverage: '1 role open', attention: true },
  { id: 'f2', number: 'AR 641', origin: 'BOM', destination: 'BLR', aircraft: 'VT-ARC · A320neo', departure: '09:20', status: 'Scheduled', tone: 'info', coverage: 'Fully staffed', attention: false },
  { id: 'f3', number: 'AR 302', origin: 'DEL', destination: 'HYD', aircraft: 'VT-ARJ · ATR 72-600', departure: '11:10', status: 'Coverage review', tone: 'warning', coverage: '2 roles open', attention: true },
  { id: 'f4', number: 'AR 719', origin: 'BLR', destination: 'DEL', aircraft: 'VT-AXR · A320neo', departure: '14:35', status: 'Scheduled', tone: 'info', coverage: 'Fully staffed', attention: false },
  { id: 'f5', number: 'AR 404', origin: 'HYD', destination: 'BOM', aircraft: 'VT-ARD · A320neo', departure: '16:05', status: 'Aircraft review', tone: 'warning', coverage: 'Fully staffed', attention: true },
]
export const crewAvailability = [{ label: 'Available', value: 31, tone: 'success' as const }, { label: 'Assigned', value: 8, tone: 'info' as const }, { label: 'Unavailable', value: 3, tone: 'warning' as const }]
export const flightStatus = [{ label: 'Scheduled', value: 9, color: '#3B82F6' }, { label: 'Coverage review', value: 2, color: '#D97706' }, { label: 'Aircraft review', value: 1, color: '#DC2626' }]
export const operationalAlerts: AlertItem[] = [
  { id: 'a1', kind: 'coverage', title: 'AR 302 requires two crew roles', detail: 'DEL → HYD at 11:10 UTC · pilot and co-pilot coverage incomplete', tone: 'warning' },
  { id: 'a2', kind: 'qualification', title: 'Two qualification reviews approach', detail: 'ATR 72 type rating reviews due within the next 14 sample days', tone: 'warning' },
  { id: 'a3', kind: 'maintenance', title: 'VT-ARD availability requires confirmation', detail: 'Aircraft review is recorded before AR 404’s scheduled departure', tone: 'error' },
  { id: 'a4', kind: 'conflict', title: 'Potential duty overlap flagged', detail: 'A sample planning conflict needs review before assignment confirmation', tone: 'error' },
]
export const recentActivity: ActivityItem[] = [
  { id: 'r1', time: '10:24 UTC', category: 'Flight change', detail: 'AR 641 departure time reviewed; schedule remains unchanged.', actor: 'Operations desk' },
  { id: 'r2', time: '09:48 UTC', category: 'Assignment', detail: 'Cabin crew duty linked to AR 218 in the demo roster.', actor: 'Roster coordinator' },
  { id: 'r3', time: '08:55 UTC', category: 'Crew record', detail: 'Qualification review reminder added for the ATR 72 pool.', actor: 'Crew services' },
]
