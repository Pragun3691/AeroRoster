import type { ReactNode } from 'react'

type Tone = 'success' | 'warning' | 'error' | 'info' | 'neutral'

const tones: Record<Tone, string> = {
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20 border-emerald-200/50',
  warning: 'bg-amber-50 text-amber-700 ring-amber-600/20 border-amber-200/50',
  error: 'bg-red-50 text-red-700 ring-red-600/20 border-red-200/50',
  info: 'bg-sky-50 text-sky-700 ring-sky-600/20 border-sky-200/50',
  neutral: 'bg-slate-100 text-slate-600 ring-slate-500/20 border-slate-200/50',
}

export function StatusBadge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode
  tone?: Tone
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset shadow-2xs ${tones[tone]}`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true" />
      {children}
    </span>
  )
}
