import type { HTMLAttributes, ReactNode } from 'react'

export function Card({ children, className = '', ...rest }: HTMLAttributes<HTMLDivElement> & { children: ReactNode; className?: string }) {
  return <div className={`card ${className}`} {...rest}>{children}</div>
}

export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string
  subtitle?: string
  action?: ReactNode
}) {
  return (
    <div className="flex items-start justify-between gap-3 mb-4">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
        {subtitle ? <p className="text-sm text-slate-500 mt-0.5">{subtitle}</p> : null}
      </div>
      {action}
    </div>
  )
}

export function Badge({ tone = 'slate', children }: { tone?: 'slate' | 'green' | 'red' | 'amber' | 'blue'; children: ReactNode }) {
  const tones: Record<string, string> = {
    slate: 'bg-slate-100 text-slate-700',
    green: 'bg-emerald-100 text-emerald-800',
    red: 'bg-red-100 text-red-700',
    amber: 'bg-amber-100 text-amber-800',
    blue: 'bg-blue-100 text-blue-700',
  }
  return <span className={`badge ${tones[tone]}`}>{children}</span>
}

export function statusTone(status: string | null | undefined): 'slate' | 'green' | 'red' | 'amber' | 'blue' {
  switch ((status ?? '').toUpperCase()) {
    case 'ACTIVE':
    case 'CONFIRMED':
    case 'ISSUED':
    case 'SENT':
      return 'green'
    case 'SUSPENDED':
    case 'VOID':
    case 'FAILED':
    case 'EXPIRED':
    case 'LOST':
      return 'red'
    case 'PENDING':
    case 'PENDING_VERIFICATION':
    case 'QUIET_HOURS':
    case 'NEW':
    case 'CONTACTED':
      return 'amber'
    case 'CONVERTED':
    case 'PAID':
      return 'blue'
    default:
      return 'slate'
  }
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="text-center py-10 px-4">
      <p className="text-slate-700 font-medium">{title}</p>
      {hint ? <p className="text-sm text-slate-500 mt-1">{hint}</p> : null}
    </div>
  )
}

export function Loader({ label = 'Loading...' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center py-10 text-slate-500 text-sm gap-2">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-brand-700" />
      {label}
    </div>
  )
}

export function ErrorNote({ message }: { message: string }) {
  return (
    <div className="rounded-xl bg-red-50 border border-red-200 text-red-800 px-3 py-2 text-sm">{message}</div>
  )
}

export function SuccessNote({ message }: { message: string }) {
  return (
    <div className="rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 px-3 py-2 text-sm">
      {message}
    </div>
  )
}

export function Field({
  label,
  children,
  hint,
  error,
}: {
  label: string
  children: ReactNode
  hint?: string
  error?: string
}) {
  return (
    <div className="mb-3">
      <label className="label">{label}</label>
      {children}
      {hint && !error ? <p className="text-xs text-slate-500 mt-1">{hint}</p> : null}
      {error ? <p className="text-xs text-red-600 mt-1">{error}</p> : null}
    </div>
  )
}

export function Modal({
  open,
  title,
  onClose,
  children,
  footer,
}: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
}) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-slate-900/40 p-0 sm:p-4">
      <div className="bg-white w-full sm:max-w-lg sm:rounded-2xl rounded-t-2xl max-h-[92vh] overflow-y-auto shadow-xl">
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 sticky top-0 bg-white rounded-t-2xl">
          <h2 className="font-semibold text-slate-900">{title}</h2>
          <button type="button" onClick={onClose} className="text-slate-500 hover:text-slate-800 text-xl leading-none px-1">
            ×
          </button>
        </div>
        <div className="px-4 py-3">{children}</div>
        {footer ? <div className="px-4 py-3 border-t border-slate-200 flex justify-end gap-2 sticky bottom-0 bg-white">{footer}</div> : null}
      </div>
    </div>
  )
}
