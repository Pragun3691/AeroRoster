import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/layout/AppShell'
import { AssignmentsPage, QualificationsPage, UnavailabilityPage } from './pages/DirectoryPages'
import { AircraftManagementPage } from './pages/aircraft/AircraftManagementPage'
import { CrewManagementPage } from './pages/crew/CrewManagementPage'
import { DashboardPage } from './pages/DashboardPage'
import { FlightManagementPage } from './pages/flights/FlightManagementPage'
export default function App() { return <AppShell><Routes><Route path="/dashboard" element={<DashboardPage />} /><Route path="/crew" element={<CrewManagementPage />} /><Route path="/crew/:crewId" element={<CrewManagementPage />} /><Route path="/aircraft" element={<AircraftManagementPage />} /><Route path="/aircraft/:aircraftId" element={<AircraftManagementPage />} /><Route path="/flights" element={<FlightManagementPage />} /><Route path="/flights/:flightId" element={<FlightManagementPage />} /><Route path="/qualifications" element={<QualificationsPage />} /><Route path="/assignments" element={<AssignmentsPage />} /><Route path="/unavailability" element={<UnavailabilityPage />} /><Route path="*" element={<Navigate to="/dashboard" replace />} /></Routes></AppShell> }
