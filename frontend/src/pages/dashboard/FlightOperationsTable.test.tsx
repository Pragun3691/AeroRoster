import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FlightOperationsTable } from './FlightOperationsTable'
describe('FlightOperationsTable', () => { it('shows an empty state when no flights are provided', () => { render(<FlightOperationsTable flights={[]} />); expect(screen.getByText('No flights match this view')).toBeInTheDocument() }) })
