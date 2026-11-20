import { Search } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

export function SearchField({ placeholder }: { placeholder: string }) {
  return (
    <label className="relative block">
      <span className="sr-only">Search</span>
      <Search
        aria-hidden="true"
        className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted"
      />
      <input className="control pl-9 text-ink" placeholder={placeholder} />
    </label>
  )
}

export function SelectControl({
  label,
  children,
}: {
  label: string
  children: ReactNode
}) {
  return (
    <label className="block text-sm font-medium text-ink">
      <span className="sr-only">{label}</span>
      <select className="control">{children}</select>
    </label>
  )
}

export function Button({
  children,
  secondary = false,
  type = 'button',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { secondary?: boolean }) {
  return (
    <button
      type={type}
      className={`${secondary ? 'btn-secondary' : 'btn-primary'} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
