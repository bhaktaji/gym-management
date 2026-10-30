import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatDate, formatDateTime } from '../../lib/format'
import { Badge, Card, EmptyState, ErrorNote, Field, Loader, Modal, PageHeader, SuccessNote } from '../../components/ui'

type Checkin = {
  id: string
  memberId: string
  memberName: string
  checkInAt: string
  checkOutAt?: string
  autoClosedAt?: string
  date: string
  method?: string
  withinGeofence?: boolean | null
}

type PeakHour = { hour: number; count: number }
type NotSeenMember = {
  memberId: string
  memberName: string
  phone: string
  whatsappOptIn: boolean
  lastSeenDate?: string | null
  daysAbsent: number
  expiryDate: string
}

type AnalyticsData = {
  todayVisits: number
  peakHours: PeakHour[]
  notSeenDays: number
  notSeenMembers: NotSeenMember[]
}

type GymInfo = {
  slug?: string
  code?: string
  settings?: {
    attendanceEnabled?: boolean
  }
}

type Member = { id: string; name: string; phone: string; status: string }

export function Checkins() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [showCheckin, setShowCheckin] = useState(false)
  const [memberId, setMemberId] = useState('')
  const [notSeenDays, setNotSeenDays] = useState(14)
  const [error, setError] = useState('')
  const [nudgeSuccess, setNudgeSuccess] = useState('')

  const gym = useQuery({
    queryKey: ['gym'],
    queryFn: () => api<GymInfo>('/api/v1/tenant/gym'),
  })

  const attendanceEnabled = Boolean(gym.data?.settings?.attendanceEnabled)

  const analytics = useQuery({
    queryKey: ['checkins', 'analytics', notSeenDays],
    queryFn: () => api<AnalyticsData>(`/api/v1/checkins/analytics?notSeenDays=${notSeenDays}`),
    enabled: attendanceEnabled,
  })

  const today = useQuery({
    queryKey: ['checkins', 'today'],
    queryFn: () => api<Checkin[]>('/api/v1/checkins/today'),
    enabled: attendanceEnabled,
  })

  const members = useQuery({
    queryKey: ['members', 'active'],
    queryFn: () => api<Member[]>('/api/v1/members?status=ACTIVE'),
    enabled: attendanceEnabled && showCheckin,
  })

  const checkin = useMutation({
    mutationFn: () => api<Checkin>('/api/v1/checkins', { method: 'POST', body: { memberId } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['checkins'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setShowCheckin(false)
      setMemberId('')
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const nudge = useMutation({
    mutationFn: (targetMemberId: string) =>
      api(`/api/v1/checkins/nudge/${targetMemberId}`, { method: 'POST' }),
    onSuccess: () => {
      setNudgeSuccess(t('checkins.nudged'))
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  if (gym.isLoading) return <Loader />

  if (!attendanceEnabled) {
    return <Navigate to="/app" replace />
  }

  const maxPeak = Math.max(1, ...(analytics.data?.peakHours.map((p) => p.count) ?? [1]))

  return (
    <div>
      <PageHeader
        title={t('checkins.title')}
        action={
          <button type="button" className="btn-secondary text-xs" onClick={() => setShowCheckin(true)}>
            + {t('checkins.manualCheckin')}
          </button>
        }
      />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}
      {nudgeSuccess ? <div className="mb-3"><SuccessNote message={nudgeSuccess} /></div> : null}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
        <Card>
          <p className="text-xs text-slate-500">{t('checkins.todayVisits')}</p>
          <p className="text-3xl font-bold text-slate-900 mt-1" data-testid="today-visits-count">
            {analytics.data?.todayVisits ?? 0}
          </p>
        </Card>
        <Card className="sm:col-span-2">
          <div className="flex items-center justify-between mb-2">
            <h2 className="font-semibold text-slate-900 text-sm">{t('checkins.peakHours')}</h2>
            {gym.data?.slug ? (
              <Link to={`/g/${gym.data.slug}/in`} className="text-xs text-brand-700 font-medium">
                /g/{gym.data.slug}/in
              </Link>
            ) : null}
          </div>
          <div className="flex items-end gap-1 h-24 pt-2">
            {(analytics.data?.peakHours ?? [])
              .filter((p) => p.hour >= 5 && p.hour <= 22)
              .map((p) => {
                const heightPct = Math.max(6, Math.round((p.count / maxPeak) * 100))
                return (
                  <div key={p.hour} className="flex-1 flex flex-col items-center gap-1">
                    <div
                      className="w-full bg-brand-600 rounded-t"
                      style={{ height: `${heightPct}%` }}
                      title={`${p.hour}:00 - ${p.count} visits`}
                    />
                    <span className="text-[10px] text-slate-500">{p.hour}</span>
                  </div>
                )
              })}
          </div>
        </Card>
      </div>

      <Card className="mb-4">
        <div className="flex items-center justify-between gap-2 mb-3">
          <h2 className="font-semibold text-slate-900">{t('checkins.notSeen')}</h2>
          <div className="flex items-center gap-2 text-xs">
            <span className="text-slate-500">Days:</span>
            <input
              type="number"
              min={1}
              max={90}
              className="input !w-16 !py-1 !px-2 text-xs"
              value={notSeenDays}
              onChange={(e) => setNotSeenDays(Number(e.target.value) || 14)}
            />
          </div>
        </div>
        {(analytics.data?.notSeenMembers ?? []).length === 0 ? (
          <EmptyState title="All active members have visited recently." />
        ) : (
          <ul className="divide-y divide-slate-100">
            {(analytics.data?.notSeenMembers ?? []).map((m) => (
              <li key={m.memberId} className="py-2.5 flex items-center justify-between gap-3">
                <div>
                  <Link to={`/app/members/${m.memberId}`} className="font-medium text-brand-800 text-sm">
                    {m.memberName}
                  </Link>
                  <p className="text-xs text-slate-500">
                    {m.lastSeenDate ? `Last seen ${formatDate(m.lastSeenDate)} (${m.daysAbsent}d ago)` : 'Never checked in'} · Valid till {formatDate(m.expiryDate)}
                  </p>
                </div>
                {m.whatsappOptIn ? (
                  <button
                    type="button"
                    className="btn-outline !px-3 !py-1.5 text-xs shrink-0"
                    onClick={() => nudge.mutate(m.memberId)}
                    disabled={nudge.isPending}
                  >
                    {t('checkins.nudge')}
                  </button>
                ) : (
                  <Badge tone="slate">Opted out</Badge>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <h2 className="font-semibold text-slate-900 mb-3">{t('checkins.history')}</h2>
        {today.isLoading ? (
          <Loader />
        ) : (today.data ?? []).length === 0 ? (
          <EmptyState title={t('checkins.noCheckins')} />
        ) : (
          <ul className="divide-y divide-slate-100">
            {(today.data ?? []).map((c) => (
              <li key={c.id} className="py-2.5 flex items-center justify-between gap-3">
                <div>
                  <p className="font-medium text-slate-900 text-sm">{c.memberName}</p>
                  <p className="text-xs text-slate-500">
                    {formatDateTime(c.checkInAt)} · {c.method || 'MANUAL'}
                    {c.withinGeofence === false ? ' · Outside geofence' : ''}
                  </p>
                </div>
                <span className="text-xs text-slate-500">
                  {c.checkOutAt
                    ? `Out ${formatDateTime(c.checkOutAt)}`
                    : c.autoClosedAt
                      ? `Auto-closes ${formatDateTime(c.autoClosedAt)}`
                      : t('checkins.stillInside')}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Modal
        open={showCheckin}
        title={t('checkins.manualCheckin')}
        onClose={() => setShowCheckin(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowCheckin(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => checkin.mutate()} disabled={!memberId}>
              {t('checkins.checkIn')}
            </button>
          </>
        }
      >
        <Field label={t('checkins.selectMember')}>
          <select className="input" value={memberId} onChange={(e) => setMemberId(e.target.value)}>
            <option value="">-</option>
            {(members.data ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {m.name} · {m.phone}
              </option>
            ))}
          </select>
        </Field>
      </Modal>
    </div>
  )
}
