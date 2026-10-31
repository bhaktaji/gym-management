import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatDate, formatMoney } from '../../lib/format'
import { Badge, Card, ErrorNote, Field, Loader, PageHeader, SuccessNote } from '../../components/ui'

type GstItemRule = {
  sac?: string
  gstRate?: number
  effectiveFrom?: string
  itcStatus?: string
}

type Gym = {
  id: string
  code?: string
  slug?: string
  name: string
  phone?: string
  email?: string
  address?: string
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
    attendanceEnabled?: boolean
    strictQrMode?: boolean
    selfCheckoutEnabled?: boolean
    autoCloseDurationMinutes?: number
    closingTimeLocal?: string
    minCheckinGapMinutes?: number
    notSeenDaysThreshold?: number
    geofenceLat?: number | null
    geofenceLng?: number | null
    geofenceRadiusMeters?: number | null
    lateRenewalPolicy?: string
    commissionTrigger?: string
    membershipReferralCommissionEnabled?: boolean
    ptSacCode?: string
    gstRules?: Record<string, GstItemRule>
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

const DEFAULT_GST_RULES: Record<string, GstItemRule> = {
  MEMBERSHIP: { sac: '999723', gstRate: 5, effectiveFrom: '2025-09-22', itcStatus: 'NO_ITC' },
  PT: { sac: '999723', gstRate: 5, effectiveFrom: '2025-09-22', itcStatus: 'NO_ITC' },
  ADMISSION_FEE: { sac: '999723', gstRate: 5, effectiveFrom: '2025-09-22', itcStatus: 'NO_ITC' },
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

  // Profile fields
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [address, setAddress] = useState('')
  const [city, setCity] = useState('')
  const [state, setState] = useState('')
  const [gstRegistered, setGstRegistered] = useState(false)
  const [gstin, setGstin] = useState('')
  const [invoicePrefix, setInvoicePrefix] = useState('')

  // Settings fields
  const [upiVpa, setUpiVpa] = useState('')
  const [upiName, setUpiName] = useState('')
  const [quietHours, setQuietHours] = useState(true)
  const [templateEn, setTemplateEn] = useState('')
  const [templateHi, setTemplateHi] = useState('')

  // Attendance & QR settings
  const [attendanceEnabled, setAttendanceEnabled] = useState(false)
  const [strictQrMode, setStrictQrMode] = useState(false)
  const [selfCheckoutEnabled, setSelfCheckoutEnabled] = useState(false)
  const [autoCloseDurationMinutes, setAutoCloseDurationMinutes] = useState('120')
  const [closingTimeLocal, setClosingTimeLocal] = useState('22:00')
  const [geofenceLat, setGeofenceLat] = useState('')
  const [geofenceLng, setGeofenceLng] = useState('')
  const [geofenceRadiusMeters, setGeofenceRadiusMeters] = useState('')

  // Membership, PT & Commission settings
  const [lateRenewalPolicy, setLateRenewalPolicy] = useState('FROM_TODAY')
  const [commissionTrigger, setCommissionTrigger] = useState('ON_PAYMENT')
  const [membershipReferralCommissionEnabled, setMembershipReferralCommissionEnabled] = useState(false)
  const [ptSacCode, setPtSacCode] = useState('999723')
  const [gstRules, setGstRules] = useState<Record<string, GstItemRule>>(DEFAULT_GST_RULES)

  useEffect(() => {
    if (gym.data) {
      setName(gym.data.name ?? '')
      setPhone(gym.data.phone ?? '')
      setEmail(gym.data.email ?? '')
      setAddress(gym.data.address ?? '')
      setCity(gym.data.city ?? '')
      setState(gym.data.state ?? '')
      setGstRegistered(gym.data.gstRegistered)
      setGstin(gym.data.gstin ?? '')
      setInvoicePrefix(gym.data.invoicePrefix ?? 'GK')

      const s = gym.data.settings
      setUpiVpa(s?.upiVpa ?? '')
      setUpiName(s?.upiName ?? '')
      setQuietHours(s?.quietHoursEnabled ?? true)
      setTemplateEn(s?.templatesEn?.renewal ?? '')
      setTemplateHi(s?.templatesHi?.renewal ?? '')
      setAttendanceEnabled(Boolean(s?.attendanceEnabled))
      setStrictQrMode(Boolean(s?.strictQrMode))
      setSelfCheckoutEnabled(Boolean(s?.selfCheckoutEnabled))
      setAutoCloseDurationMinutes(String(s?.autoCloseDurationMinutes ?? 120))
      setClosingTimeLocal(s?.closingTimeLocal ?? '22:00')
      setGeofenceLat(s?.geofenceLat != null ? String(s.geofenceLat) : '')
      setGeofenceLng(s?.geofenceLng != null ? String(s.geofenceLng) : '')
      setGeofenceRadiusMeters(s?.geofenceRadiusMeters != null ? String(s.geofenceRadiusMeters) : '')
      setLateRenewalPolicy(s?.lateRenewalPolicy ?? 'FROM_TODAY')
      setCommissionTrigger(s?.commissionTrigger ?? 'ON_PAYMENT')
      setMembershipReferralCommissionEnabled(Boolean(s?.membershipReferralCommissionEnabled))
      setPtSacCode(s?.ptSacCode ?? '999723')
      setGstRules({
        MEMBERSHIP: { ...DEFAULT_GST_RULES.MEMBERSHIP, ...(s?.gstRules?.MEMBERSHIP ?? {}) },
        PT: { ...DEFAULT_GST_RULES.PT, sac: s?.ptSacCode ?? '999723', ...(s?.gstRules?.PT ?? {}) },
        ADMISSION_FEE: { ...DEFAULT_GST_RULES.ADMISSION_FEE, ...(s?.gstRules?.ADMISSION_FEE ?? {}) },
      })
    }
  }, [gym.data])

  const updateGstRule = (itemType: string, patch: Partial<GstItemRule>) => {
    setGstRules((prev) => ({
      ...prev,
      [itemType]: { ...prev[itemType], ...patch },
    }))
  }

  const saveProfile = useMutation({
    mutationFn: () =>
      api<Gym>('/api/v1/tenant/profile', {
        method: 'PUT',
        body: { name, phone, email, address, city, state, gstin, gstRegistered, invoicePrefix },
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
          attendanceEnabled,
          strictQrMode,
          selfCheckoutEnabled,
          autoCloseDurationMinutes: Number(autoCloseDurationMinutes) || 120,
          closingTimeLocal: closingTimeLocal || '22:00',
          geofenceLat: geofenceLat.trim() ? Number(geofenceLat) : null,
          geofenceLng: geofenceLng.trim() ? Number(geofenceLng) : null,
          geofenceRadiusMeters: geofenceRadiusMeters.trim() ? Number(geofenceRadiusMeters) : null,
          lateRenewalPolicy,
          commissionTrigger,
          membershipReferralCommissionEnabled,
          ptSacCode: gstRules.PT?.sac || ptSacCode || '999723',
          gstRules,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['gym'] })
      setSaved(true)
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  async function downloadQrPoster() {
    try {
      const res = await api<Response>('/api/v1/checkins/poster.pdf', { raw: true })
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `qr-poster-${gym.data?.slug || gym.data?.code || 'gym'}.pdf`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('common.error'))
    }
  }

  if (gym.isLoading) return <Loader />

  const slug = gym.data?.slug || gym.data?.code || ''

  return (
    <div>
      <PageHeader title={t('settings.title')} />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}
      {saved ? <div className="mb-3"><SuccessNote message={t('settings.saved')} /></div> : null}

      <Card>
        <h2 className="font-semibold text-slate-900 mb-3">{t('settings.gymProfile')}</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Field label={t('auth.gymName')}>
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label={t('common.phone')}>
            <input className="input" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </Field>
          <Field label={t('common.email')}>
            <input className="input" value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label={t('members.address')}>
            <input className="input" value={address} onChange={(e) => setAddress(e.target.value)} />
          </Field>
          <Field label={t('auth.city')}>
            <input className="input" value={city} onChange={(e) => setCity(e.target.value)} />
          </Field>
          <Field label={t('auth.state')}>
            <input className="input" value={state} onChange={(e) => setState(e.target.value)} />
          </Field>
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700 mb-3 mt-1">
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
        <h2 className="font-semibold text-slate-900 mb-2">{t('settings.attendance')}</h2>
        <p className="text-xs text-slate-500 mb-3">
          Optional entry tracking module (OFF by default). Core membership status always derives from dates and payments.
        </p>

        <label className="flex items-center gap-2 text-sm text-slate-800 font-medium mb-3">
          <input
            type="checkbox"
            data-testid="attendance-enabled-checkbox"
            className="h-4 w-4"
            checked={attendanceEnabled}
            onChange={(e) => setAttendanceEnabled(e.target.checked)}
          />
          {t('settings.attendanceEnabled')}
        </label>

        {attendanceEnabled ? (
          <div className="space-y-3 border-t border-slate-100 pt-3">
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                data-testid="strict-qr-checkbox"
                className="h-4 w-4"
                checked={strictQrMode}
                onChange={(e) => setStrictQrMode(e.target.checked)}
              />
              {t('settings.strictQrMode')}
            </label>

            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                className="h-4 w-4"
                checked={selfCheckoutEnabled}
                onChange={(e) => setSelfCheckoutEnabled(e.target.checked)}
              />
              {t('settings.selfCheckoutEnabled')}
            </label>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label={t('settings.autoCloseMinutes')}>
                <input
                  type="number"
                  className="input"
                  value={autoCloseDurationMinutes}
                  onChange={(e) => setAutoCloseDurationMinutes(e.target.value)}
                />
              </Field>
              <Field label="Closing time (HH:mm)">
                <input
                  className="input"
                  value={closingTimeLocal}
                  onChange={(e) => setClosingTimeLocal(e.target.value)}
                />
              </Field>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Field label="Geofence Lat (optional)">
                <input className="input" value={geofenceLat} onChange={(e) => setGeofenceLat(e.target.value)} />
              </Field>
              <Field label="Geofence Lng (optional)">
                <input className="input" value={geofenceLng} onChange={(e) => setGeofenceLng(e.target.value)} />
              </Field>
              <Field label="Radius meters (optional)">
                <input className="input" value={geofenceRadiusMeters} onChange={(e) => setGeofenceRadiusMeters(e.target.value)} />
              </Field>
            </div>

            {slug ? (
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <button
                  type="button"
                  data-testid="download-qr-poster-btn"
                  className="btn-secondary text-xs"
                  onClick={() => void downloadQrPoster()}
                >
                  {t('settings.downloadPoster')}
                </button>
                <Link to={`/g/${slug}/in`} className="btn-outline text-xs">
                  Open Self Check-In (/g/{slug}/in)
                </Link>
                <Link to={`/g/${slug}/screen`} className="btn-outline text-xs">
                  Open Rotating QR Screen (/g/{slug}/screen)
                </Link>
              </div>
            ) : null}
          </div>
        ) : null}

        <h2 className="font-semibold text-slate-900 mt-5 mb-3">{t('settings.membershipAndPt')}</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Field label={t('settings.lateRenewalPolicy')}>
            <select className="input" value={lateRenewalPolicy} onChange={(e) => setLateRenewalPolicy(e.target.value)}>
              <option value="FROM_TODAY">Start from today (renewal date)</option>
              <option value="FROM_EXPIRY">Start from old expiry date (continuous)</option>
            </select>
          </Field>
          <Field label={t('settings.commissionTrigger')}>
            <select className="input" value={commissionTrigger} onChange={(e) => setCommissionTrigger(e.target.value)}>
              <option value="ON_PAYMENT">ON_PAYMENT (Proportional to payment collected)</option>
              <option value="ON_SESSION_DELIVERED">ON_SESSION_DELIVERED (Per PT session completed)</option>
            </select>
          </Field>
        </div>

        <div className="border-t border-slate-100 pt-3 mt-2 mb-4" data-testid="gst-item-rules-section">
          <h3 className="text-sm font-semibold text-slate-900">
            GST Rate, SAC &amp; Effective Date per Item Type
          </h3>
          <p className="text-xs text-slate-500 mb-3">
            Defaults: SAC 999723, 5% (No ITC) effective 2025-09-22 (18% With ITC prior) — confirm with your Chartered Accountant (CA).
          </p>
          <div className="space-y-3">
            {(['MEMBERSHIP', 'PT', 'ADMISSION_FEE'] as const).map((itemType) => {
              const rule = gstRules[itemType] ?? DEFAULT_GST_RULES[itemType]
              return (
                <div
                  key={itemType}
                  data-testid={`gst-rule-${itemType}`}
                  className="rounded-lg border border-slate-200 p-3 bg-slate-50/60"
                >
                  <p className="text-xs font-semibold text-slate-800 mb-2">{itemType}</p>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    <Field label="SAC Code">
                      <input
                        className="input font-mono text-xs"
                        value={rule.sac ?? '999723'}
                        onChange={(e) => {
                          updateGstRule(itemType, { sac: e.target.value })
                          if (itemType === 'PT') setPtSacCode(e.target.value)
                        }}
                      />
                    </Field>
                    <Field label="GST Rate (%)">
                      <input
                        type="number"
                        className="input text-xs"
                        min={0}
                        max={28}
                        value={rule.gstRate ?? 5}
                        onChange={(e) => updateGstRule(itemType, { gstRate: Number(e.target.value) })}
                      />
                    </Field>
                    <Field label="Effective Date">
                      <input
                        type="date"
                        className="input text-xs"
                        value={rule.effectiveFrom ?? '2025-09-22'}
                        onChange={(e) => updateGstRule(itemType, { effectiveFrom: e.target.value })}
                      />
                    </Field>
                    <Field label="ITC Status">
                      <select
                        className="input text-xs"
                        value={rule.itcStatus ?? 'No ITC'}
                        onChange={(e) => updateGstRule(itemType, { itcStatus: e.target.value })}
                      >
                        <option value="No ITC">No ITC</option>
                        <option value="With ITC">With ITC</option>
                      </select>
                    </Field>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700 mb-4">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={membershipReferralCommissionEnabled}
            onChange={(e) => setMembershipReferralCommissionEnabled(e.target.checked)}
          />
          {t('settings.referralCommission')}
        </label>

        <h2 className="font-semibold text-slate-900 mt-4 mb-3">{t('settings.upi')}</h2>
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

        <button
          type="button"
          data-testid="save-settings-btn"
          className="btn-primary"
          onClick={() => saveSettings.mutate()}
          disabled={saveSettings.isPending}
        >
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
        <h2 className="font-semibold text-slate-900 mb-3">{t('settings.integrations')}</h2>
        <div className="flex items-center justify-between text-sm py-2 border-b border-slate-100">
          <span className="text-slate-700">{t('settings.whatsappCloud')}</span>
          <Badge tone="slate">{t('settings.comingSoon')}</Badge>
        </div>
        <div className="flex items-center justify-between text-sm py-2">
          <span className="text-slate-700">{t('settings.razorpay')}</span>
          <Badge tone="slate">{t('settings.comingSoon')}</Badge>
        </div>
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-1">{t('settings.privacy')}</h2>
        <p className="text-sm text-slate-600">{t('settings.privacyNote')}</p>
      </Card>
    </div>
  )
}
