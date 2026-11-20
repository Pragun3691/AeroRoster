import { CalendarOff, ClipboardList, Gauge, GraduationCap, Plane, PlaneTakeoff, ShieldCheck, Users, X } from 'lucide-react'
import { NavLink } from 'react-router-dom'

const links = [
  { to: '/dashboard', label: 'Dashboard', icon: Gauge },
  { to: '/crew', label: 'Crew', icon: Users },
  { to: '/aircraft', label: 'Aircraft', icon: Plane },
  { to: '/flights', label: 'Flights', icon: PlaneTakeoff },
  { to: '/qualifications', label: 'Qualifications', icon: GraduationCap },
  { to: '/assignments', label: 'Assignments', icon: ClipboardList },
  { to: '/unavailability', label: 'Unavailability', icon: CalendarOff },
]

export function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <>
      {/* Mobile backdrop */}
      <div
        className={`fixed inset-0 z-30 bg-navy-dark/60 backdrop-blur-xs transition-opacity lg:hidden ${
          open ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Sidebar container */}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-64 -translate-x-full flex-col border-r border-white/10 bg-navy-dark text-slate-200 shadow-xl transition-transform duration-200 ease-in-out lg:translate-x-0 ${
          open ? 'translate-x-0' : ''
        }`}
      >
        {/* Brand header */}
        <div className="flex h-20 items-center border-b border-white/10 px-5">
          <div className="grid h-10 w-10 place-items-center rounded-lg bg-gradient-to-br from-teal to-teal-dark text-base font-extrabold tracking-wider text-white shadow-xs">
            AR
          </div>
          <div className="ml-3.5">
            <div className="flex items-center gap-1.5">
              <span className="font-bold tracking-tight text-white">AeroRoster</span>
              <span className="rounded bg-teal/20 px-1.5 py-0.2 text-[10px] font-semibold text-teal-light">v1.1</span>
            </div>
            <p className="text-xs font-medium text-slate-400">Flight Operations</p>
          </div>
          <button
            type="button"
            className="ml-auto rounded-md p-1.5 text-slate-400 transition hover:bg-white/10 hover:text-white focus-ring lg:hidden"
            onClick={onClose}
            aria-label="Close navigation"
          >
            <X size={20} />
          </button>
        </div>

        {/* Primary navigation */}
        <nav className="flex-1 space-y-1.5 overflow-y-auto px-3 py-6" aria-label="Primary navigation">
          <div className="mb-2 px-3 text-[11px] font-bold uppercase tracking-wider text-slate-400">
            Operations
          </div>
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={onClose}
              className={({ isActive }) =>
                `group flex items-center gap-3 rounded-lg border-l-2 px-3.5 py-2.5 text-sm font-medium transition-all duration-150 ${
                  isActive
                    ? 'border-teal bg-white/10 text-white font-semibold shadow-xs'
                    : 'border-transparent text-slate-300 hover:bg-white/5 hover:text-white'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon
                    size={18}
                    className={`transition-colors duration-150 ${
                      isActive ? 'text-teal' : 'text-slate-400 group-hover:text-slate-200'
                    }`}
                    aria-hidden="true"
                  />
                  <span>{label}</span>
                </>
              )}
            </NavLink>
          ))}
        </nav>

        {/* System status footer */}
        <div className="border-t border-white/10 bg-black/20 p-4">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-300">
            <span className="h-2 w-2 rounded-full bg-emerald-400 ring-2 ring-emerald-400/20" />
            <span>API Online · Same-Origin</span>
          </div>
          <div className="mt-2 text-[11px] leading-4 text-slate-400">
            <div className="flex items-center gap-1 font-medium text-slate-300">
              <ShieldCheck size={13} className="text-teal" />
              <span>DGCA CAR Compliant</span>
            </div>
            <p className="mt-1">Local development workspace<br />Same-origin local API workspace.</p>
          </div>
        </div>
      </aside>
    </>
  )
}
