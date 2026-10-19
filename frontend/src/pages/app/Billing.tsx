import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatDate, formatMoney } from '../../lib/format'
import { Badge, Card, EmptyState, ErrorNote, Field, Loader, Modal, PageHeader, statusTone } from '../../components/ui'

type Invoice = {
  id: string
  invoiceNumber: string
  type: string
  totalPaise: number
  status: string
  createdAt: string
  memberId?: string
  payToken?: string
  lines?: { description: string; sac?: string; taxablePaise: number; cgstPaise: number; sgstPaise: number; igstPaise: number; gstRate: number; totalPaise: number }[]
  amountInWords?: string
  gstRegistered?: boolean
  gstin?: string
}

type Payment = {
  id: string
  amountPaise: number
  method: string
  status: string
  utr?: string
  createdAt: string
}

type Membership = { id: string; planName: string; expiryDate: string; memberId: string; pricePaise: number }
type PayLink = { amountPaise: number; upiDeepLink: string; upiVpa: string; publicPayUrl: string }

export function Billing() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<Invoice | null>(null)
  const [payAmount, setPayAmount] = useState('')
  const [payMethod, setPayMethod] = useState('CASH')
  const [showPay, setShowPay] = useState(false)
  const [showGenerate, setShowGenerate] = useState(false)
  const [membershipId, setMembershipId] = useState('')
  const [voidReason, setVoidReason] = useState('')
  const [showVoid, setShowVoid] = useState(false)
  const [voidTarget, setVoidTarget] = useState<Invoice | null>(null)
  const [creditReason, setCreditReason] = useState('')
  const [showCredit, setShowCredit] = useState(false)
  const [creditTarget, setCreditTarget] = useState<Invoice | null>(null)

  const invoices = useQuery({
    queryKey: ['invoices'],
    queryFn: () => api<Invoice[]>('/api/v1/billing/invoices'),
  })

  const pending = useQuery({
    queryKey: ['pending-payments'],
    queryFn: () => api<Payment[]>('/api/v1/payments/pending'),
  })

  const memberships = useQuery({
    queryKey: ['memberships', 'active'],
    queryFn: () => api<Membership[]>('/api/v1/memberships/active'),
  })

  const payments = useQuery({
    queryKey: ['invoice-payments', selected?.id],
    queryFn: () => api<Payment[]>(`/api/v1/billing/invoices/${selected!.id}/payments`),
    enabled: !!selected,
  })

  const balance = useQuery({
    queryKey: ['invoice-balance', selected?.id],
    queryFn: () => api<{ balancePaise: number }>(`/api/v1/billing/invoices/${selected!.id}/balance`),
    enabled: !!selected,
  })

  const payLink = useQuery({
    queryKey: ['invoice-paylink', selected?.id],
    queryFn: () => api<PayLink>(`/api/v1/billing/invoices/${selected!.id}/pay-link`),
    enabled: !!selected,
  })

  const generate = useMutation({
    mutationFn: () => api('/api/v1/billing/invoices', { method: 'POST', body: { membershipId } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      setShowGenerate(false)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const pay = useMutation({
    mutationFn: () =>
      api('/api/v1/payments/counter', {
        method: 'POST',
        body: { invoiceId: selected!.id, amountPaise: Math.round(Number(payAmount) * 100), method: payMethod },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      void queryClient.invalidateQueries({ queryKey: ['invoice-payments', selected?.id] })
      void queryClient.invalidateQueries({ queryKey: ['invoice-balance', selected?.id] })
      setShowPay(false)
      setPayAmount('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const confirm = useMutation({
    mutationFn: (id: string) => api(`/api/v1/payments/${id}/confirm`, { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['pending-payments'] })
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
  })

  const voidInvoice = useMutation({
    mutationFn: () => api(`/api/v1/billing/invoices/${voidTarget!.id}/void`, { method: 'POST', body: { reason: voidReason } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      setShowVoid(false)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const creditNote = useMutation({
    mutationFn: () =>
      api(`/api/v1/billing/invoices/${creditTarget!.id}/credit-note`, { method: 'POST', body: { reason: creditReason } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      setShowCredit(false)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const downloadPdf = async (invoice: Invoice) => {
    const blob = await api<Blob>(`/api/v1/billing/invoices/${invoice.id}/pdf`, { raw: true })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${invoice.invoiceNumber}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  }

  const openPay = (inv: Invoice) => {
    setSelected(inv)
    setPayAmount('')
    setShowPay(true)
  }

  return (
    <div>
      <PageHeader
        title={t('billing.title')}
        action={
          <button type="button" className="btn-primary" onClick={() => setShowGenerate(true)}>
            {t('billing.generate')}
          </button>
        }
      />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}

      {(pending.data ?? []).length > 0 ? (
        <Card className="mb-4 border-amber-300">
          <h2 className="font-semibold text-slate-900 mb-2">{t('billing.pendingVerification')}</h2>
          <ul className="space-y-2">
            {(pending.data ?? []).map((p) => (
              <li key={p.id} className="flex items-center justify-between text-sm">
                <div>
                  <p className="font-mono">{p.utr}</p>
                  <p className="text-xs text-slate-500">{formatMoney(p.amountPaise)}</p>
                </div>
                <button type="button" className="btn-primary !px-3 !py-1.5 text-xs" onClick={() => confirm.mutate(p.id)}>
                  {t('billing.confirm')}
                </button>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      {invoices.isLoading ? (
        <Loader />
      ) : (invoices.data ?? []).length === 0 ? (
        <EmptyState title={t('billing.noInvoices')} />
      ) : (
        <ul className="space-y-2">
          {(invoices.data ?? []).map((inv) => (
            <li key={inv.id} className="card">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-mono text-slate-900">{inv.invoiceNumber}</p>
                  <p className="text-xs text-slate-500">{formatDate(inv.createdAt)}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="font-semibold text-slate-900">{formatMoney(inv.totalPaise)}</p>
                  <Badge tone={statusTone(inv.status)}>{inv.type}</Badge>
                </div>
              </div>
              <div className="flex flex-wrap gap-2 mt-3">
                <button type="button" className="btn-outline !px-2 !py-1 text-xs" onClick={() => setSelected(inv)}>
                  {t('common.viewAll')}
                </button>
                {inv.status === 'ISSUED' ? (
                  <>
                    <button type="button" className="btn-outline !px-2 !py-1 text-xs" onClick={() => openPay(inv)}>
                      {t('billing.recordPayment')}
                    </button>
                    <button type="button" className="btn-outline !px-2 !py-1 text-xs" onClick={() => void downloadPdf(inv)}>
                      {t('billing.downloadPdf')}
                    </button>
                    <button
                      type="button"
                      className="btn-outline !px-2 !py-1 text-xs"
                      onClick={() => {
                        setVoidTarget(inv)
                        setVoidReason('')
                        setShowVoid(true)
                      }}
                    >
                      {t('billing.void')}
                    </button>
                    <button
                      type="button"
                      className="btn-outline !px-2 !py-1 text-xs"
                      onClick={() => {
                        setCreditTarget(inv)
                        setCreditReason('')
                        setShowCredit(true)
                      }}
                    >
                      {t('billing.creditNote')}
                    </button>
                  </>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal open={!!selected} title={selected?.invoiceNumber ?? ''} onClose={() => setSelected(null)}>
        {selected ? (
          <div>
            <div className="flex justify-between text-sm mb-2">
              <span className="text-slate-500">{t('billing.total')}</span>
              <span className="font-semibold">{formatMoney(selected.totalPaise)}</span>
            </div>
            <div className="flex justify-between text-sm mb-3">
              <span className="text-slate-500">{t('billing.balance')}</span>
              <span className="font-semibold">{formatMoney(balance.data?.balancePaise ?? 0)}</span>
            </div>
            {selected.amountInWords ? (
              <p className="text-xs text-slate-500 mb-3">{selected.amountInWords}</p>
            ) : null}

            {selected.gstRegistered ? (
              <div className="border-t border-slate-200 pt-3 mb-3">
                <p className="text-xs text-slate-500 mb-2">
                  {t('settings.gstin')}: {selected.gstin ?? '-'}
                </p>
                {(selected.lines ?? []).map((l, i) => (
                  <div key={i} className="text-xs text-slate-600 flex justify-between py-0.5">
                    <span>
                      {l.description} {l.sac ? `· SAC ${l.sac}` : ''} @ {l.gstRate}%
                    </span>
                    <span>{formatMoney(l.totalPaise)}</span>
                  </div>
                ))}
              </div>
            ) : null}

            {payLink.data?.upiDeepLink ? (
              <div className="border-t border-slate-200 pt-3 mb-3 text-center">
                <p className="text-xs text-slate-500 mb-1">{t('billing.scanToPay')}</p>
                <p className="text-xs text-slate-500 mb-2">UPI: {payLink.data.upiVpa}</p>
                <a href={payLink.data.upiDeepLink} className="btn-secondary text-xs" target="_blank" rel="noreferrer">
                  {t('billing.upi')}
                </a>
              </div>
            ) : null}

            <h3 className="font-semibold text-slate-900 text-sm mb-2">{t('billing.status')}</h3>
            {(payments.data ?? []).length === 0 ? (
              <p className="text-sm text-slate-500">{t('common.none')}</p>
            ) : (
              <ul className="space-y-1">
                {(payments.data ?? []).map((p) => (
                  <li key={p.id} className="flex justify-between text-sm">
                    <span>
                      {p.method} {p.utr ? `· ${p.utr}` : ''}
                    </span>
                    <span className="flex items-center gap-2">
                      {formatMoney(p.amountPaise)}
                      <Badge tone={statusTone(p.status)}>{p.status}</Badge>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ) : null}
      </Modal>

      <Modal
        open={showPay}
        title={t('billing.recordPayment')}
        onClose={() => setShowPay(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowPay(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => pay.mutate()} disabled={pay.isPending || !payAmount}>
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label={t('billing.method')}>
          <select className="input" value={payMethod} onChange={(e) => setPayMethod(e.target.value)}>
            <option value="CASH">{t('billing.cash')}</option>
            <option value="CARD">{t('billing.card')}</option>
          </select>
        </Field>
        <Field label={t('billing.amount')}>
          <input
            className="input"
            type="number"
            min={0}
            step="0.01"
            value={payAmount}
            onChange={(e) => setPayAmount(e.target.value)}
          />
        </Field>
      </Modal>

      <Modal
        open={showGenerate}
        title={t('billing.generateFor')}
        onClose={() => setShowGenerate(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowGenerate(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => generate.mutate()} disabled={!membershipId}>
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label={t('billing.selectMembership')}>
          <select className="input" value={membershipId} onChange={(e) => setMembershipId(e.target.value)}>
            <option value="">-</option>
            {(memberships.data ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {m.planName} · {formatDate(m.expiryDate)} · {formatMoney(m.pricePaise)}
              </option>
            ))}
          </select>
        </Field>
      </Modal>

      <Modal
        open={showVoid}
        title={t('billing.voidTitle')}
        onClose={() => setShowVoid(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowVoid(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-danger" onClick={() => voidInvoice.mutate()}>
              {t('common.confirm')}
            </button>
          </>
        }
      >
        <Field label={t('billing.voidReason')}>
          <input className="input" value={voidReason} onChange={(e) => setVoidReason(e.target.value)} />
        </Field>
      </Modal>

      <Modal
        open={showCredit}
        title={t('billing.creditNoteTitle')}
        onClose={() => setShowCredit(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowCredit(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => creditNote.mutate()}>
              {t('common.confirm')}
            </button>
          </>
        }
      >
        <Field label={t('billing.creditNoteReason')}>
          <input className="input" value={creditReason} onChange={(e) => setCreditReason(e.target.value)} />
        </Field>
      </Modal>
    </div>
  )
}
