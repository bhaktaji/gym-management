/** Money is transported as integer paise and rendered with Indian grouping. */
export function formatMoney(paise: number | null | undefined, withSymbol = true): string {
  if (paise === null || paise === undefined) return '-'
  const negative = paise < 0
  const abs = Math.abs(paise)
  const rupees = Math.floor(abs / 100)
  const p = abs % 100
  const grouped = groupIndian(rupees)
  return `${negative ? '-' : ''}${withSymbol ? '₹' : ''}${grouped}.${String(p).padStart(2, '0')}`
}

function groupIndian(value: number): string {
  const s = String(value)
  if (s.length <= 3) return s
  const last3 = s.slice(-3)
  let head = s.slice(0, -3)
  const parts: string[] = []
  while (head.length > 2) {
    parts.unshift(head.slice(-2))
    head = head.slice(0, -2)
  }
  if (head.length) parts.unshift(head)
  return parts.join(',') + ',' + last3
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function toLocalDateInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
