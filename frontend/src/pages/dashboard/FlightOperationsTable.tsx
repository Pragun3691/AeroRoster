import { ArrowRight } from 'lucide-react'
import { DataTable, EmptyState, TableHead } from '../../components/common/DataTable'
import { StatusBadge } from '../../components/common/StatusBadge'
import type { DemoFlight } from './dashboardData'

export function FlightOperationsTable({ flights }: { flights: DemoFlight[] }) {
  if (!flights.length) {
    return (
      <EmptyState
        title="No flights match this view"
        detail="Adjust the dashboard filter to change the flight list."
      />
    )
  }

  return (
    <DataTable>
      <caption className="sr-only">Flight operations</caption>
      <TableHead>
        <th className="px-4 py-3.5">Flight</th>
        <th className="px-4 py-3.5">Route</th>
        <th className="px-4 py-3.5">Aircraft</th>
        <th className="px-4 py-3.5">Departure UTC</th>
        <th className="px-4 py-3.5">Status</th>
      </TableHead>
      <tbody className="divide-y divide-line">
        {flights.map(flight => (
          <tr key={flight.id} className="transition hover:bg-slate-50/60">
            <td className="px-4 py-3.5">
              <span className="font-semibold text-ink">{flight.number}</span>
              <span className="block text-xs font-medium text-muted">{flight.coverage}</span>
            </td>
            <td className="px-4 py-3.5">
              <span className="inline-flex items-center gap-1.5 font-semibold text-ink">
                <span>{flight.origin}</span>
                <ArrowRight size={13} className="text-muted" aria-hidden="true" />
                <span>{flight.destination}</span>
              </span>
            </td>
            <td className="px-4 py-3.5 font-medium text-muted">{flight.aircraft}</td>
            <td className="px-4 py-3.5 font-mono text-xs text-muted">{flight.departure}</td>
            <td className="px-4 py-3.5">
              <StatusBadge tone={flight.tone}>{flight.status}</StatusBadge>
            </td>
          </tr>
        ))}
      </tbody>
    </DataTable>
  )
}
