import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatDate, formatDateTime } from '../../lib/format'
import { Badge, EmptyState, ErrorNote, Field, Loader, Modal, PageHeader, statusTone } from '../../components/ui'

type GymSummary = {
  id: string
  name: string
  code: string
  ownerName: string
  ownerPhone: string
  city?: string
  state?: string
  gstRegistered: boolean
  activeMembers: number
  status: string
  subscriptionValidUntil?: string
  lastLoginAt?: string
}

export function AdminGyms() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [showOwner, setShowOwner] = useState(false)
  const [showSubscription, setShowSubscription] = useState(false)
  const [target, setTarget] = useState<GymSummary | null>(null)

  const [gymName, setGymName] = useState('')
  const [ownerName, setOwnerName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [city, setCity] = useState('')
  const [state, setState] = useState('')
  const [gstin, setGstin] = useState('')
  const [gstRegistered, setGstRegistered] = useState(false)
  const [password, setPassword] = useState('')
  const [ownerPhone, setOwnerPhone] = useState('')
  const [ownerEmail, setOwnerEmail] = useState('')
  const [validUntil, setValidUntil] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['admin-gyms'],
    queryFn: () => api<GymSummary[]>('/api/v1/admin/gyms'),
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['admin-gyms'] })

  const create = useMutation({
    mutationFn: () =>
      api('/api/v1/admin/gyms', {
        method: 'POST',
        body: {
          gymName,
          ownerName,
          phone,
          email: email || null,
          city: city || null,
          state: state || null,
          gstin: gstin || null,
          gstRegistered,
          password,
        },
      }),
    onSuccess: () => {
      invalidate()
      setShowCreate(false)
      setSuccess(t('admin.gymCreated'))
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const setStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      api(`/api/v1/admin/gyms/${id}/status`, { method: 'POST', body: { status } }),
    onSuccess: invalidate,
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const updateOwner = useMutation({
    mutationFn: () =>
      api(`/api/v1/admin/gyms/${target!.id}/owner`, {
        method: 'PUT',
        body: { phone: ownerPhone || null, email: ownerEmail || null },
      }),
    onSuccess: () => {
      invalidate()
      setShowOwner(false)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const resetPassword = useMutation({
    mutationFn: (id: string) => api<{ resetToken: string }>(`/api/v1/admin/gyms/${id}/password-reset`, { method: 'POST' }),
    onSuccess: (res) => {
      setSuccess(`${t('admin.resetTokenIssued')}: ${res.resetToken}`)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const setSubscription = useMutation({
    mutationFn: () =>
      api(`/api/v1/admin/gyms/${target!.id}/subscription`, { method: 'PUT', body: { validUntil } }),
    onSuccess: () => {
      invalidate()
      setShowSubscription(false)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const openOwner = (g: GymSummary) => {
    setTarget(g)
    setOwnerPhone('')
    setOwnerEmail('')
    setShowOwner(true)
  }

  const openSubscription = (g: GymSummary) => {
    setTarget(g)
    setValidUntil(g.subscriptionValidUntil ?? '')
    setShowSubscription(true)
  }

  return (
    <div>
      <PageHeader
        title={t('admin.title')}
        subtitle={t('admin.gyms')}
        action={
          <button type="button" className="btn-primary" onClick={() => setShowCreate(true)}>
            + {t('admin.createGym')}
          </button>
        }
      />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}
      {success ? <div className="mb-3"><div className="rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 px-3 py-2 text-sm break-all">{success}</div></div> : null}

      {isLoading ? (
        <Loader />
      ) : (data ?? []).length === 0 ? (
        <EmptyState title={t('admin.noGyms')} />
      ) : (
        <ul className="space-y-3">
          {(data ?? []).map((g) => (
            <li key={g.id} className="card">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-semibold text-slate-900">{g.name}</p>
                  <p className="text-sm text-slate-600">
                    {g.ownerName} · {g.ownerPhone}
                  </p>
                  <p className="text-xs text-slate-500">
                    {g.code}
                    {g.city ? ` · ${g.city}` : ''}
                    {g.state ? `, ${g.state}` : ''}
                  </p>
                </div>
                <div className="text-right shrink-0">
                  <Badge tone={statusTone(g.status)}>{g.status}</Badge>
                  <p className="text-xs text-slate-500 mt-1">{g.activeMembers} {t('admin.members')}</p>
                  <p className="text-xs text-slate-500">{g.gstRegistered ? t('admin.gstRegistered') : '-'}</p>
                </div>
              </div>

              <dl className="grid grid-cols-2 gap-y-1 text-xs text-slate-600 mt-3">
                <div>
                  <dt className="text-slate-400">{t('admin.subscriptionUntil')}</dt>
                  <dd>{formatDate(g.subscriptionValidUntil)}</dd>
                </div>
                <div>
                  <dt className="text-slate-400">{t('admin.lastLogin')}</dt>
                  <dd>{g.lastLoginAt ? formatDateTime(g.lastLoginAt) : '-'}</dd>
                </div>
              </dl>

              <div className="flex flex-wrap gap-2 mt-3">
                <button
                  type="button"
                  className="btn-outline !px-2 !py-1 text-xs"
                  onClick={() =>
                    setStatus.mutate({ id: g.id, status: g.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED' })
                  }
                >
                  {g.status === 'SUSPENDED' ? t('admin.reactivate') : t('admin.suspend')}
                </button>
                <button type="button" className="btn-outline !px-2 !py-1 text-xs" onClick={() => openOwner(g)}>
                  {t('admin.updateOwner')}
                </button>
                <button type="button" className="btn-outline !px-2 !py-1 text-xs" onClick={() => openSubscription(g)}>
                  {t('admin.updateSubscription')}
                </button>
                <button type="button" className="btn-outline !px-2 !py-1 text-xs" onClick={() => resetPassword.mutate(g.id)}>
                  {t('admin.resetPassword')}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal
        open={showCreate}
        title={t('admin.createGym')}
        onClose={() => setShowCreate(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowCreate(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => create.mutate()} disabled={create.isPending}>
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label={t('auth.gymName')}>
          <input className="input" value={gymName} onChange={(e) => setGymName(e.target.value)} />
        </Field>
        <Field label={t('auth.ownerName')}>
          <input className="input" value={ownerName} onChange={(e) => setOwnerName(e.target.value)} />
        </Field>
        <Field label={t('common.phone')}>
          <input className="input" inputMode="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
        </Field>
        <Field label={`${t('common.email')} (${t('common.optional')})`}>
          <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('auth.city')}>
            <input className="input" value={city} onChange={(e) => setCity(e.target.value)} />
          </Field>
          <Field label={t('auth.state')}>
            <input className="input" value={state} onChange={(e) => setState(e.target.value)} />
          </Field>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700 mb-3">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={gstRegistered}
            onChange={(e) => setGstRegistered(e.target.checked)}
          />
          {t('admin.gstRegistered')}
        </label>
        {gstRegistered ? (
          <Field label={t('admin.gstin')}>
            <input className="input font-mono" value={gstin} onChange={(e) => setGstin(e.target.value.toUpperCase())} />
          </Field>
        ) : null}
        <Field label={t('auth.password')}>
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
      </Modal>

      <Modal
        open={showOwner}
        title={t('admin.updateOwner')}
        onClose={() => setShowOwner(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowOwner(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => updateOwner.mutate()}>
              {t('common.save')}
            </button>
          </>
        }
      >
        <p className="text-xs text-slate-500 mb-3">
          {target?.name} · {target?.ownerName} · {target?.ownerPhone}
        </p>
        <Field label={t('common.phone')}>
          <input className="input" inputMode="tel" value={ownerPhone} onChange={(e) => setOwnerPhone(e.target.value)} />
        </Field>
        <Field label={t('common.email')}>
          <input className="input" type="email" value={ownerEmail} onChange={(e) => setOwnerEmail(e.target.value)} />
        </Field>
      </Modal>

      <Modal
        open={showSubscription}
        title={t('admin.subscriptionUntil')}
        onClose={() => setShowSubscription(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowSubscription(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => setSubscription.mutate()}>
              {t('admin.updateSubscription')}
            </button>
          </>
        }
      >
        <Field label={t('admin.subscriptionUntil')}>
          <input className="input" type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
        </Field>
      </Modal>
    </div>
  )
}
