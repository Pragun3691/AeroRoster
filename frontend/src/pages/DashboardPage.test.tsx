import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { DashboardPage } from './DashboardPage'
function renderDashboard() { return render(<MemoryRouter><DashboardPage /></MemoryRouter>) }
describe('DashboardPage', () => {
  it('renders the operational metrics and dashboard heading', () => { renderDashboard(); expect(screen.getByRole('heading', { name: 'Operational dashboard' })).toBeInTheDocument(); expect(screen.getByText('Available crew')).toBeInTheDocument(); expect(screen.getAllByText('31').length).toBeGreaterThan(0) })
  it('renders flight operations and alerts', () => { renderDashboard(); expect(screen.getByRole('table', { name: /upcoming sample flight operations/i })).toBeInTheDocument(); expect(screen.getByText('AR 218')).toBeInTheDocument(); expect(screen.getByText('Operational alerts')).toBeInTheDocument(); expect(screen.getByText('AR 302 requires two crew roles')).toBeInTheDocument() })
  it('filters flights to attention items', async () => { const user = userEvent.setup(); renderDashboard(); await user.selectOptions(screen.getByRole('combobox'), 'attention'); expect(screen.getByText('AR 302')).toBeInTheDocument(); expect(screen.queryByText('AR 641')).not.toBeInTheDocument() })
})
