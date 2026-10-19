import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatDate, formatMoney } from '../../lib/format'
import { Badge, Card, EmptyState, ErrorNote, Field, Loader, Modal, PageHeader, statusTone } from '../../components/ui'
import type { Member } from './Members'

type Membership = {
  id: string
  planName: string
  startDate: string
  expiryDate: string
  pricePaise: number
  status: string
}

type Invoice = {
  id: string
  invoiceNumber: string
  type: string
  totalPaise: number
  status: string
  createdAt: string
  payToken?: string
}

type Plan = { id: string; name: string; durationDays: number; pricePaise: number; active: boolean }

export function MemberDetail() {
  const { id } = useParams()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [error, setError] = useState('')
  const [renewPlanId, setRenewPlanId] = useState('')
  const [showRenew, setShowRenew] = useState(false)

  const member = useQuery({
    queryKey: ['member', id],
    queryFn: () => api<Member>(`/api/v1/members/${id}`),
  })

  const memberships = useQuery({
    queryKey: ['memberships', 'member', id],
    queryFn: () => api<Membership[]>(`/api/v1/memberships/member/${id}`),
  })

  const invoices = useQuery({
    queryKey: ['invoices', 'member', id],
    queryFn: () => api<Invoice[]>(`/api/v1/billing/invoices/member/${id}`),
  })

  const plans = useQuery({
    queryKey: ['plans'],
    queryFn: () => api<Plan[]>('/api/v1/plans'),
  })

  const enrol = useMutation({
    mutationFn: (planId: string) =>
      api<Membership>('/api/v1/memberships', { method: 'POST', body: { memberId: id, planId } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['memberships', 'member', id] })
      void queryClient.invalidateQueries({ queryKey: ['member', id] })
      setShowRenew(false)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const generateInvoice = useMutation({
    mutationFn: (membershipId: string) =>
      api<Invoice>('/api/v1/billing/invoices', { method: 'POST', body: { membershipId } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoices', 'member', id] })
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const erase = useMutation({
    mutationFn: () => api<Member>(`/api/v1/members/${id}/erase`, { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['member', id] })
      navigate('/app/members')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const exportData = async () => {
    const data = await api<unknown>(`/api/v1/members/${id}/export`)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `member-${id}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  if (member.isLoading) return <Loader />
  if (member.isError) return <ErrorNote message={(member.error as Error).message} />
  if (!member.data) return null

  const m = member.data
  const activeMembership = (memberships.data ?? []).find((x) => x.status === 'ACTIVE')

  return (
    <div>
      <Link to="/app/members" className="text-sm text-brand-800">
        ← {t('common.back')}
      </Link>
      <PageHeader
        title={m.name}
        subtitle={m.phone}
        action={<Badge tone={statusTone(m.status)}>{m.status}</Badge>}
      />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}

      <Card>
        <dl className="grid grid-cols-2 gap-y-3 text-sm">
          <div>
            <dt className="text-slate-500 text-xs">{t('members.joined')}</dt>
            <dd>{formatDate(m.joinedAt)}</dd>
          </div>
          <div>
            <dt className="text-slate-500 text-xs">{t('members.plan')}</dt>
            <dd>{activeMembership?.planName ?? '-'}</dd>
          </div>
          <div>
            <dt className="text-slate-500 text-xs">{t('members.expiry')}</dt>
            <dd>{activeMembership ? formatDate(activeMembership.expiryDate) : '-'}</dd>
          </div>
          <div>
            <dt className="text-slate-500 text-xs">WhatsApp</dt>
            <dd>{m.whatsappOptIn ? '✓' : '✗'}</dd>
          </div>
          {m.email ? (
            <div className="col-span-2">
              <dt className="text-slate-500 text-xs">{t('common.email')}</dt>
              <dd>{m.email}</dd>
            </div>
          ) : null}
          {m.address ? (
            <div className="col-span-2">
              <dt className="text-slate-500 text-xs">{t('members.address')}</dt>
              <dd>
                {m.address}
                {m.state ? `, ${m.state}` : ''}
              </dd>
            </div>
          ) : null}
        </dl>

        <div className="flex flex-wrap gap-2 mt-4">
          <button type="button" className="btn-primary" onClick={() => setShowRenew(true)}>
            {t('members.renew')}
          </button>
          <button type="button" className="btn-secondary" onClick={() => void exportData()}>
            {t('members.exportData')}
          </button>
          <button
            type="button"
            className="btn-danger"
            onClick={() => {
              if (window.confirm(t('members.eraseConfirm'))) erase.mutate()
            }}
          >
            {t('members.erase')}
          </button>
        </div>
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('billing.invoices')}</h2>
        {(invoices.data ?? []).length === 0 ? (
          <EmptyState title={t('billing.noInvoices')} />
        ) : (
          <ul className="space-y-2">
            {(invoices.data ?? []).map((inv) => (
              <li key={inv.id} className="flex items-center justify-between text-sm">
                <div>
                  <p className="font-mono text-slate-800">{inv.invoiceNumber}</p>
                  <p className="text-xs text-slate-500">{formatDate(inv.createdAt)}</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-medium">{formatMoney(inv.totalPaise)}</span>
                  <Badge tone={statusTone(inv.status)}>{inv.type}</Badge>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('plans.title')}</h2>
        {(memberships.data ?? []).length === 0 ? (
          <EmptyState title={t('plans.noPlans')} />
        ) : (
          <ul className="space-y-2">
            {(memberships.data ?? []).map((ms) => (
              <li key={ms.id} className="flex items-center justify-between text-sm border-b border-slate-100 pb-2">
                <div>
                  <p className="font-medium">{ms.planName}</p>
                  <p className="text-xs text-slate-500">
                    {formatDate(ms.startDate)} → {formatDate(ms.expiryDate)}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span>{formatMoney(ms.pricePaise)}</span>
                  <Badge tone={statusTone(ms.status)}>{ms.status}</Badge>
                  {ms.status === 'ACTIVE' ? (
                    <button
                      type="button"
                      className="btn-outline !px-2 !py-1 text-xs"
                      onClick={() => generateInvoice.mutate(ms.id)}
                    >
                      {t('dashboard.generateInvoice')}
                    </button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Modal
        open={showRenew}
        title={t('members.renew')}
        onClose={() => setShowRenew(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowRenew(false)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn-primary"
              disabled={!renewPlanId || enrol.isPending}
              onClick={() => enrol.mutate(renewPlanId)}
            >
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label={t('plans.title')}>
          <select className="input" value={renewPlanId} onChange={(e) => setRenewPlanId(e.target.value)}>
            <option value="">-</option>
            {(plans.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} - {p.durationDays}d - {formatMoney(p.pricePaise)}
              </option>
            ))}
          </select>
        </Field>
      </Modal>
    </div>
  )
}
