import type { ReactNode } from 'react'

export function DataTable({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-line bg-white shadow-card">
      <table className="min-w-full divide-y divide-line text-left text-sm">
        {children}
      </table>
    </div>
  )
}

export function TableHead({ children }: { children: ReactNode }) {
  return (
    <thead className="border-b border-line bg-slate-50/80 text-xs font-semibold uppercase tracking-wider text-muted">
      <tr>{children}</tr>
    </thead>
  )
}

export function EmptyState({
  title,
  detail,
}: {
  title: string
  detail: string
}) {
  return (
    <div className="rounded-lg border-2 border-dashed border-slate-300 bg-white px-6 py-12 text-center shadow-xs">
      <p className="text-base font-semibold text-ink">{title}</p>
      <p className="mt-1.5 text-sm text-muted">{detail}</p>
    </div>
  )
}
