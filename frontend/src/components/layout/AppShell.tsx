import { Menu, Plane } from 'lucide-react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { Sidebar } from '../navigation/Sidebar'

export function AppShell({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)

  return (
    <div className="min-h-screen bg-canvas text-ink">
      <Sidebar open={open} onClose={() => setOpen(false)} />

      <div className="lg:pl-64">
        {/* Mobile top navigation header */}
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-line bg-white/95 px-4 backdrop-blur-sm lg:hidden">
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              className="rounded-md p-2 text-ink hover:bg-slate-100 focus-ring"
              onClick={() => setOpen(true)}
              aria-label="Open navigation"
            >
              <Menu size={20} />
            </button>
            <div className="flex items-center gap-2">
              <span className="font-bold tracking-tight text-navy">AeroRoster</span>
              <span className="rounded bg-teal/10 px-1.5 py-0.5 text-[10px] font-semibold text-teal-dark">Operations</span>
            </div>
          </div>
          <div className="flex items-center gap-1.5 text-xs font-semibold text-muted">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            <span className="hidden sm:inline">Connected</span>
          </div>
        </header>

        {/* Desktop subtle context bar */}
        <header className="hidden h-14 items-center justify-between border-b border-line bg-white px-8 lg:flex">
          <div className="flex items-center gap-2 text-xs font-medium text-muted">
            <Plane size={15} className="text-teal" />
            <span className="font-semibold text-ink">AeroRoster Operations Portal</span>
            <span>/</span>
            <span>Local Instance (127.0.0.1)</span>
          </div>
          <div className="flex items-center gap-4 text-xs">
            <div className="flex items-center gap-2 rounded-full border border-line bg-slate-50 px-3 py-1 font-medium text-slate-700">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 ring-2 ring-emerald-500/20" />
              <span>PostgreSQL Test Cluster Active</span>
            </div>
            <span className="rounded bg-navy/5 px-2.5 py-1 font-mono font-semibold text-navy">UTC</span>
          </div>
        </header>

        {/* Main application viewport */}
        <main className="mx-auto max-w-[1600px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          {children}
        </main>
      </div>
    </div>
  )
}
