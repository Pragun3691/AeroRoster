import type { ReactNode } from 'react'

export function PageHeader({
  eyebrow = 'Operational workspace',
  title,
  description,
  action,
}: {
  eyebrow?: string
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <header className="flex flex-col gap-4 border-b border-line pb-6 sm:flex-row sm:items-end sm:justify-between">
      <div className="space-y-1">
        <div className="inline-flex items-center gap-1.5">
          <span className="text-[11px] font-bold uppercase tracking-[0.16em] text-teal">
            {eyebrow}
          </span>
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-ink sm:text-3xl">
          {title}
        </h1>
        <p className="max-w-2xl text-sm leading-relaxed text-muted">
          {description}
        </p>
      </div>
      {action && (
        <div className="flex flex-wrap items-center gap-3 pt-2 sm:pt-0">
          {action}
        </div>
      )}
    </header>
  )
}
