import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatDate, formatMoney } from '../../lib/format'
import { Card, ErrorNote, Field, Loader, PageHeader, SuccessNote } from '../../components/ui'

type Gym = {
  id: string
  name: string
  city?: string
  state?: string
  gstin?: string
  gstRegistered: boolean
  invoicePrefix: string
  status: string
  subscriptionValidUntil?: string
  planPricePaise: number
  settings?: {
    upiVpa?: string
    upiName?: string
    quietHoursEnabled?: boolean
    templatesEn?: Record<string, string>
    templatesHi?: Record<string, string>
  }
}

type PlanUsage = {
  pricePaise: number
  subscriptionStatus: string
  subscriptionValidUntil?: string
  perMemberFee: number
  perMessageFee: number
  usage: { members: number; plans: number }
}

export function Settings() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)

  const gym = useQuery({
    queryKey: ['gym'],
    queryFn: () => api<Gym>('/api/v1/tenant/gym'),
  })

  const usage = useQuery({
    queryKey: ['plan-usage'],
    queryFn: () => api<PlanUsage>('/api/v1/tenant/plan-usage'),
  })

  const [gstRegistered, setGstRegistered] = useState(false)
  const [gstin, setGstin] = useState('')
  const [invoicePrefix, setInvoicePrefix] = useState('')
  const [upiVpa, setUpiVpa] = useState('')
  const [upiName, setUpiName] = useState('')
  const [quietHours, setQuietHours] = useState(true)
  const [templateEn, setTemplateEn] = useState('')
  const [templateHi, setTemplateHi] = useState('')

  useEffect(() => {
    if (gym.data) {
      setGstRegistered(gym.data.gstRegistered)
      setGstin(gym.data.gstin ?? '')
      setInvoicePrefix(gym.data.invoicePrefix ?? 'GK')
      setUpiVpa(gym.data.settings?.upiVpa ?? '')
      setUpiName(gym.data.settings?.upiName ?? '')
      setQuietHours(gym.data.settings?.quietHoursEnabled ?? true)
      setTemplateEn(gym.data.settings?.templatesEn?.renewal ?? '')
      setTemplateHi(gym.data.settings?.templatesHi?.renewal ?? '')
    }
  }, [gym.data])

  const saveProfile = useMutation({
    mutationFn: () =>
      api<Gym>('/api/v1/tenant/profile', {
        method: 'PUT',
        body: { gstin, gstRegistered, invoicePrefix },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['gym'] })
      setSaved(true)
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const saveSettings = useMutation({
    mutationFn: () =>
      api<Gym>('/api/v1/tenant/settings', {
        method: 'PUT',
        body: {
          upiVpa,
          upiName,
          quietHoursEnabled: quietHours,
          templatesEn: templateEn ? { renewal: templateEn } : {},
          templatesHi: templateHi ? { renewal: templateHi } : {},
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['gym'] })
      setSaved(true)
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  if (gym.isLoading) return <Loader />

  return (
    <div>
      <PageHeader title={t('settings.title')} />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}
      {saved ? <div className="mb-3"><SuccessNote message={t('settings.saved')} /></div> : null}

      <Card>
        <h2 className="font-semibold text-slate-900 mb-1">{t('settings.gymProfile')}</h2>
        <p className="text-sm text-slate-500 mb-3">
          {gym.data?.name}
          {gym.data?.city ? ` · ${gym.data.city}` : ''}
        </p>

        <label className="flex items-center gap-2 text-sm text-slate-700 mb-3">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={gstRegistered}
            onChange={(e) => setGstRegistered(e.target.checked)}
          />
          {t('settings.gstRegistered')}
        </label>

        {gstRegistered ? (
          <Field label={t('settings.gstin')} hint={t('settings.gstNote')}>
            <input className="input font-mono" value={gstin} onChange={(e) => setGstin(e.target.value.toUpperCase())} />
          </Field>
        ) : null}

        <Field label={t('settings.invoicePrefix')} hint={t('settings.invoicePrefixNote')}>
          <input
            className="input font-mono uppercase"
            maxLength={3}
            value={invoicePrefix}
            onChange={(e) => setInvoicePrefix(e.target.value.toUpperCase())}
          />
        </Field>

        <button type="button" className="btn-primary" onClick={() => saveProfile.mutate()} disabled={saveProfile.isPending}>
          {t('common.save')}
        </button>
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('settings.upi')}</h2>
        <Field label={t('settings.upiVpa')} hint="e.g. mygym@okhdfcbank">
          <input className="input" value={upiVpa} onChange={(e) => setUpiVpa(e.target.value)} />
        </Field>
        <Field label={t('settings.upiName')}>
          <input className="input" value={upiName} onChange={(e) => setUpiName(e.target.value)} />
        </Field>

        <h2 className="font-semibold text-slate-900 mt-4 mb-1">{t('settings.templates')}</h2>
        <p className="text-xs text-slate-500 mb-3">{t('settings.templateNote')}</p>
        <Field label={t('settings.templateEn')}>
          <textarea className="input text-sm" rows={3} value={templateEn} onChange={(e) => setTemplateEn(e.target.value)} />
        </Field>
        <Field label={t('settings.templateHi')}>
          <textarea className="input text-sm" rows={3} value={templateHi} onChange={(e) => setTemplateHi(e.target.value)} />
        </Field>

        <label className="flex items-center gap-2 text-sm text-slate-700 mb-3">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={quietHours}
            onChange={(e) => setQuietHours(e.target.checked)}
          />
          {t('settings.quietHours')}
        </label>

        <button type="button" className="btn-primary" onClick={() => saveSettings.mutate()} disabled={saveSettings.isPending}>
          {t('common.save')}
        </button>
      </Card>

      {usage.data ? (
        <Card className="mt-4">
          <h2 className="font-semibold text-slate-900 mb-3">{t('planUsage.title')}</h2>
          <div className="flex justify-between text-sm py-1">
            <span className="text-slate-500">{t('planUsage.price')}</span>
            <span className="font-medium">{formatMoney(usage.data.pricePaise)}</span>
          </div>
          <div className="flex justify-between text-sm py-1">
            <span className="text-slate-500">{t('planUsage.subscription')}</span>
            <span className="font-medium">
              {usage.data.subscriptionStatus === 'ACTIVE' ? t('planUsage.active') : t('planUsage.expired')}
            </span>
          </div>
          <div className="flex justify-between text-sm py-1">
            <span className="text-slate-500">{t('planUsage.validUntil')}</span>
            <span>{formatDate(usage.data.subscriptionValidUntil)}</span>
          </div>
          <div className="flex justify-between text-sm py-1">
            <span className="text-slate-500">{t('planUsage.members')}</span>
            <span>{usage.data.usage.members}</span>
          </div>
          <div className="flex justify-between text-sm py-1">
            <span className="text-slate-500">{t('planUsage.plans')}</span>
            <span>{usage.data.usage.plans}</span>
          </div>
          <p className="text-xs text-slate-500 mt-3">{t('planUsage.noPerMember')} · {t('planUsage.noPerMessage')}</p>
        </Card>
      ) : null}

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-1">{t('settings.privacy')}</h2>
        <p className="text-sm text-slate-600">{t('settings.privacyNote')}</p>
      </Card>
    </div>
  )
}
