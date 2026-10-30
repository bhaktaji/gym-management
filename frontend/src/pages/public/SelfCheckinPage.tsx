import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { Card, ErrorNote, Field, Loader, SuccessNote } from '../../components/ui'

type PublicAttendanceInfo = {
  gymId: string
  gymName: string
  gymSlug: string
  gymPhone: string
  attendanceEnabled: boolean
  strictQrMode: boolean
  selfCheckoutEnabled: boolean
  deviceBound: boolean
  boundMemberId?: string
  boundMemberName?: string
}

type PtEnrolmentSummary = {
  id: string
  ptPackageName: string
  trainerName: string
  packageType: string
  remainingSessions?: number | null
  totalSessions?: number | null
  expiryDate?: string
}

type SelfCheckinResult = {
  checkinId: string
  memberId: string
  memberName: string
  gymName: string
  gymPhone: string
  badge: 'GREEN' | 'AMBER' | 'RED'
  badgeColor: 'GREEN' | 'AMBER' | 'RED'
  statusMessage: string
  expiryDate?: string | null
  daysLeft: number
  payUrl?: string
  payLink?: string
  upiDeepLink?: string
  selfCheckoutEnabled: boolean
  activePtEnrolments?: PtEnrolmentSummary[]
  deviceToken?: string
}

export function SelfCheckinPage() {
  const { slug } = useParams<{ slug: string }>()
  const [searchParams] = useSearchParams()
  const urlToken = searchParams.get('token') ?? ''

  const [phone, setPhone] = useState('')
  const [pin, setPin] = useState('')
  const [rotatingToken, setRotatingToken] = useState(urlToken)
  const [result, setResult] = useState<SelfCheckinResult | null>(null)
  const [checkedOutMsg, setCheckedOutMsg] = useState('')
  const [ptDoneMsg, setPtDoneMsg] = useState('')
  const [error, setError] = useState('')
  const [storedDeviceToken, setStoredDeviceToken] = useState<string | null>(() =>
    slug ? localStorage.getItem(`gk_device_${slug}`) : null
  )

  const info = useQuery({
    queryKey: ['public-attendance-info', slug, storedDeviceToken],
    queryFn: () =>
      api<PublicAttendanceInfo>(`/api/v1/public/attendance/${slug}/info`, {
        auth: false,
        headers: storedDeviceToken ? { 'X-Device-Token': storedDeviceToken } : {},
      }),
    enabled: Boolean(slug),
    retry: false,
  })

  const checkInMutation = useMutation({
    mutationFn: () =>
      api<SelfCheckinResult>(`/api/v1/public/attendance/${slug}/checkin`, {
        method: 'POST',
        auth: false,
        headers: storedDeviceToken ? { 'X-Device-Token': storedDeviceToken } : {},
        body: {
          phone: phone.trim() || undefined,
          pin: pin.trim() || undefined,
          deviceToken: storedDeviceToken || undefined,
          token: rotatingToken.trim() || undefined,
        },
      }),
    onSuccess: (data) => {
      if (data.deviceToken && slug) {
        localStorage.setItem(`gk_device_${slug}`, data.deviceToken)
        setStoredDeviceToken(data.deviceToken)
      }
      setResult(data)
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Check-in failed'),
  })

  const checkOutMutation = useMutation({
    mutationFn: () =>
      api(`/api/v1/public/attendance/${slug}/checkout`, {
        method: 'POST',
        auth: false,
        headers: storedDeviceToken ? { 'X-Device-Token': storedDeviceToken } : {},
      }),
    onSuccess: () => {
      setCheckedOutMsg('Checked out! See you next time.')
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not check out'),
  })

  const ptSessionDoneMutation = useMutation({
    mutationFn: () =>
      api<PtEnrolmentSummary>(`/api/v1/public/attendance/${slug}/pt-session-done`, {
        method: 'POST',
        auth: false,
        headers: storedDeviceToken ? { 'X-Device-Token': storedDeviceToken } : {},
      }),
    onSuccess: (updated) => {
      setPtDoneMsg(
        `PT session recorded for ${updated.ptPackageName}! Remaining sessions: ${updated.remainingSessions ?? 0}`
      )
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not mark PT session'),
  })

  if (info.isLoading) return <Loader />

  if (info.isError) {
    const errMsg = info.error instanceof ApiError ? info.error.message : 'Attendance check-in is not available'
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <Card className="max-w-md w-full text-center">
          <h1 className="text-lg font-bold text-slate-900 mb-2">Self Check-In Unavailable</h1>
          <ErrorNote message={errMsg} />
        </Card>
      </div>
    )
  }

  const gymInfo = info.data!

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="max-w-md w-full space-y-4">
        <Card>
          <div className="text-center mb-4">
            <span className="h-10 w-10 rounded-xl bg-brand-700 text-white inline-grid place-items-center font-bold text-lg mb-2">
              G
            </span>
            <h1 className="text-xl font-bold text-slate-900">{gymInfo.gymName}</h1>
            <p className="text-xs text-slate-500">Member Self Check-In</p>
          </div>

          {error ? (
            <div className="mb-3" data-testid="self-checkin-error">
              <ErrorNote message={error} />
            </div>
          ) : null}
          {checkedOutMsg ? <div className="mb-3"><SuccessNote message={checkedOutMsg} /></div> : null}
          {ptDoneMsg ? <div className="mb-3"><SuccessNote message={ptDoneMsg} /></div> : null}

          {result ? (
            <div
              data-testid="self-checkin-result"
              data-badge={result.badge}
              className={`rounded-xl p-4 border ${
                result.badge === 'GREEN'
                  ? 'bg-emerald-50 border-emerald-300 text-emerald-950'
                  : result.badge === 'AMBER'
                    ? 'bg-amber-50 border-amber-300 text-amber-950'
                    : 'bg-rose-50 border-rose-300 text-rose-950'
              }`}
            >
              <p className="text-xs uppercase font-bold tracking-wider opacity-75">
                Welcome, {result.memberName}
              </p>
              <p className="text-xl font-bold mt-1" data-testid="self-checkin-status-message">
                {result.statusMessage}
              </p>

              {(result.badge === 'AMBER' || result.badge === 'RED') && (result.payUrl || result.upiDeepLink) ? (
                <div className="mt-4 space-y-2">
                  {result.payUrl ? (
                    <Link
                      to={result.payUrl}
                      data-testid="self-checkin-pay-link"
                      className="btn-primary w-full block text-center"
                    >
                      Pay Renewal via UPI
                    </Link>
                  ) : result.upiDeepLink ? (
                    <a
                      href={result.upiDeepLink}
                      data-testid="self-checkin-pay-link"
                      className="btn-primary w-full block text-center"
                    >
                      Pay Renewal via UPI App
                    </a>
                  ) : null}
                </div>
              ) : null}

              {result.badge === 'RED' && result.gymPhone ? (
                <p className="text-xs mt-3 font-medium" data-testid="self-checkin-gym-contact">
                  Gym contact: {result.gymPhone}
                </p>
              ) : null}

              <div className="mt-4 pt-3 border-t border-black/10 flex flex-col gap-2">
                {result.activePtEnrolments && result.activePtEnrolments.length > 0 ? (
                  <button
                    type="button"
                    data-testid="self-pt-session-done-btn"
                    className="btn-secondary w-full text-sm"
                    onClick={() => ptSessionDoneMutation.mutate()}
                    disabled={ptSessionDoneMutation.isPending}
                  >
                    🏋️ PT session done ({result.activePtEnrolments[0].ptPackageName})
                  </button>
                ) : null}

                {result.selfCheckoutEnabled ? (
                  <button
                    type="button"
                    data-testid="self-checkout-btn"
                    className="btn-outline w-full text-sm"
                    onClick={() => checkOutMutation.mutate()}
                    disabled={checkOutMutation.isPending}
                  >
                    I&apos;m leaving
                  </button>
                ) : null}
              </div>
            </div>
          ) : (
            <div>
              {gymInfo.strictQrMode ? (
                <Field label="6-digit Screen QR Token">
                  <input
                    data-testid="self-checkin-qr-token"
                    className="input font-mono text-center tracking-widest"
                    placeholder="000000"
                    value={rotatingToken}
                    onChange={(e) => setRotatingToken(e.target.value)}
                  />
                </Field>
              ) : null}

              {gymInfo.deviceBound ? (
                <div className="space-y-3">
                  <div className="rounded-lg bg-slate-100 p-3 text-center">
                    <p className="text-xs text-slate-500">Bound device for</p>
                    <p className="font-semibold text-slate-900">{gymInfo.boundMemberName}</p>
                  </div>
                  <button
                    type="button"
                    data-testid="self-checkin-one-tap-btn"
                    className="btn-primary w-full py-3 text-base"
                    onClick={() => checkInMutation.mutate()}
                    disabled={checkInMutation.isPending}
                  >
                    {checkInMutation.isPending ? 'Checking in...' : 'One-Tap Check In'}
                  </button>
                  <button
                    type="button"
                    className="text-xs text-slate-500 underline w-full text-center"
                    onClick={() => {
                      if (slug) localStorage.removeItem(`gk_device_${slug}`)
                      setStoredDeviceToken(null)
                    }}
                  >
                    Use a different member phone &amp; PIN
                  </button>
                </div>
              ) : (
                <form
                  onSubmit={(e) => {
                    e.preventDefault()
                    checkInMutation.mutate()
                  }}
                >
                  <Field label="Registered Mobile Number">
                    <input
                      data-testid="self-checkin-phone"
                      type="tel"
                      className="input"
                      placeholder="9876543210"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      required
                    />
                  </Field>
                  <Field label="4-Digit Member PIN" hint="Shared in your welcome WhatsApp message">
                    <input
                      data-testid="self-checkin-pin"
                      type="password"
                      inputMode="numeric"
                      maxLength={4}
                      className="input font-mono tracking-widest text-center text-lg"
                      placeholder="••••"
                      value={pin}
                      onChange={(e) => setPin(e.target.value)}
                      required
                    />
                  </Field>
                  <button
                    type="submit"
                    data-testid="self-checkin-submit"
                    className="btn-primary w-full py-2.5 mt-1"
                    disabled={checkInMutation.isPending}
                  >
                    {checkInMutation.isPending ? 'Checking in...' : 'Check In'}
                  </button>
                </form>
              )}
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}
