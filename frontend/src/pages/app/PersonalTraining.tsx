import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatDate, formatMoney, paiseToRupeesString, rupeesToPaise } from '../../lib/format'
import { Badge, Card, EmptyState, ErrorNote, Field, Loader, Modal, PageHeader, SuccessNote } from '../../components/ui'

type CommissionSlab = {
  upToPaise?: number | null
  rateBps: number
}

type CommissionRule = {
  type: 'FLAT' | 'PERCENT' | 'SLAB'
  flatBasis?: 'PER_PACKAGE' | 'PER_SESSION'
  flatAmountPaise?: number
  rateBps?: number
  slabs?: CommissionSlab[]
  effectiveFrom?: string
}

type Trainer = {
  id: string
  name: string
  phone: string
  active: boolean
  defaultCommissionRule?: CommissionRule
}

type PtPackage = {
  id: string
  name: string
  packageType: 'SESSION_BASED' | 'TIME_BASED'
  validityDays: number
  totalSessions?: number | null
  pricePaise: number
  sacCode: string
  active: boolean
}

type PtEnrolment = {
  id: string
  memberId: string
  memberName: string
  ptPackageId: string
  ptPackageName: string
  packageType: 'SESSION_BASED' | 'TIME_BASED'
  trainerId: string
  trainerName: string
  invoiceId: string
  pricePaise: number
  discountPaise: number
  totalSessions?: number | null
  deliveredSessions: number
  remainingSessions?: number | null
  startDate: string
  expiryDate: string
  status: string
}

type CommissionEntry = {
  id: string
  entryType: 'ACCRUAL' | 'REVERSAL' | 'ADJUSTMENT' | 'PAYOUT'
  sourceType?: string
  amountPaise: number
  taxableBasePaise?: number
  reason?: string
  payoutMode?: string
  payoutDate?: string
  ruleSnapshot?: CommissionRule
  createdAt: string
}

type StatementData = {
  trainerId: string
  trainerName: string
  trainerPhone: string
  month: string
  accruedPaise: number
  reversalsPaise: number
  adjustmentsPaise: number
  netEarnedPaise: number
  paidPaise: number
  balanceDuePaise: number
  whatsappShareText: string
  whatsappShareUrl: string
  entries: CommissionEntry[]
}

type Member = { id: string; name: string; phone: string }

function formatRule(rule?: CommissionRule): string {
  if (!rule) return 'Default'
  if (rule.type === 'FLAT') {
    const amt = formatMoney(rule.flatAmountPaise ?? 0)
    return rule.flatBasis === 'PER_SESSION' ? `Flat ${amt} / session` : `Flat ${amt} / package`
  }
  if (rule.type === 'PERCENT') {
    return `${((rule.rateBps ?? 0) / 100).toFixed(1)}% of taxable value`
  }
  if (rule.type === 'SLAB') {
    return `Progressive Slab (${(rule.slabs ?? []).length} tiers)`
  }
  return rule.type
}

export function PersonalTraining() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<'enrolments' | 'trainers' | 'packages' | 'statements'>('enrolments')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  // Modals
  const [showAddTrainer, setShowAddTrainer] = useState(false)
  const [showAddPackage, setShowAddPackage] = useState(false)
  const [showSellPt, setShowSellPt] = useState(false)
  const [payInvoiceId, setPayInvoiceId] = useState<string | null>(null)
  const [showPayout, setShowPayout] = useState(false)
  const [showAdjustment, setShowAdjustment] = useState(false)

  // Add Trainer state
  const [trainerName, setTrainerName] = useState('')
  const [trainerPhone, setTrainerPhone] = useState('')
  const [ruleType, setRuleType] = useState<'PERCENT' | 'FLAT' | 'SLAB'>('PERCENT')
  const [flatBasis, setFlatBasis] = useState<'PER_PACKAGE' | 'PER_SESSION'>('PER_PACKAGE')
  const [flatRupees, setFlatRupees] = useState('1500')
  const [percentRate, setPercentRate] = useState('20')

  // Add Package state
  const [pkgName, setPkgName] = useState('')
  const [pkgType, setPkgType] = useState<'SESSION_BASED' | 'TIME_BASED'>('SESSION_BASED')
  const [pkgValidity, setPkgValidity] = useState('60')
  const [pkgSessions, setPkgSessions] = useState('12')
  const [pkgPriceRupees, setPkgPriceRupees] = useState('10500')
  const [pkgSac, setPkgSac] = useState('999723')

  // Sell PT state
  const [sellMemberId, setSellMemberId] = useState('')
  const [sellPackageId, setSellPackageId] = useState('')
  const [sellTrainerId, setSellTrainerId] = useState('')
  const [sellDiscountRupees, setSellDiscountRupees] = useState('0')

  // Counter Payment on PT Invoice state
  const [payAmountRupees, setPayAmountRupees] = useState('')
  const [payMethod, setPayMethod] = useState<'CASH' | 'CARD' | 'UPI'>('CASH')

  // Statement filters & modals
  const currentMonth = new Date().toISOString().slice(0, 7)
  const [selectedTrainerId, setSelectedTrainerId] = useState('')
  const [selectedMonth, setSelectedMonth] = useState(currentMonth)
  const [payoutRupees, setPayoutRupees] = useState('')
  const [payoutMode, setPayoutMode] = useState('UPI')
  const [adjRupees, setAdjRupees] = useState('')
  const [adjReason, setAdjReason] = useState('')

  const trainers = useQuery({
    queryKey: ['trainers'],
    queryFn: () => api<Trainer[]>('/api/v1/trainers'),
  })

  const packages = useQuery({
    queryKey: ['pt-packages'],
    queryFn: () => api<PtPackage[]>('/api/v1/pt/packages'),
  })

  const enrolments = useQuery({
    queryKey: ['pt-enrolments'],
    queryFn: () => api<PtEnrolment[]>('/api/v1/pt/enrolments'),
  })

  const members = useQuery({
    queryKey: ['members', 'active'],
    queryFn: () => api<Member[]>('/api/v1/members?status=ACTIVE'),
  })

  useEffect(() => {
    if (!selectedTrainerId && trainers.data && trainers.data.length > 0) {
      setSelectedTrainerId(trainers.data[0].id)
    }
  }, [trainers.data, selectedTrainerId])

  const statement = useQuery({
    queryKey: ['commission-statement', selectedTrainerId, selectedMonth],
    queryFn: () =>
      api<StatementData>(
        `/api/v1/commissions/statement?trainerId=${encodeURIComponent(selectedTrainerId)}&month=${encodeURIComponent(selectedMonth)}`
      ),
    enabled: Boolean(selectedTrainerId),
  })

  function buildCommissionRule(): CommissionRule {
    if (ruleType === 'FLAT') {
      return {
        type: 'FLAT',
        flatBasis,
        flatAmountPaise: rupeesToPaise(flatRupees),
      }
    }
    if (ruleType === 'SLAB') {
      return {
        type: 'SLAB',
        slabs: [
          { upToPaise: 2_500_000, rateBps: 1500 },
          { upToPaise: 5_000_000, rateBps: 2000 },
          { upToPaise: null, rateBps: 2500 },
        ],
      }
    }
    return {
      type: 'PERCENT',
      rateBps: Math.round((Number(percentRate) || 0) * 100),
    }
  }

  const createTrainer = useMutation({
    mutationFn: () =>
      api<Trainer>('/api/v1/trainers', {
        method: 'POST',
        body: {
          name: trainerName,
          phone: trainerPhone,
          active: true,
          defaultCommissionRule: buildCommissionRule(),
        },
      }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['trainers'] })
      setSelectedTrainerId(created.id)
      setShowAddTrainer(false)
      setTrainerName('')
      setTrainerPhone('')
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const createPackage = useMutation({
    mutationFn: () =>
      api<PtPackage>('/api/v1/pt/packages', {
        method: 'POST',
        body: {
          name: pkgName,
          packageType: pkgType,
          validityDays: Number(pkgValidity) || 30,
          totalSessions: pkgType === 'SESSION_BASED' ? Number(pkgSessions) || 10 : null,
          pricePaise: rupeesToPaise(pkgPriceRupees),
          sacCode: pkgSac || '999723',
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['pt-packages'] })
      setShowAddPackage(false)
      setPkgName('')
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const sellPt = useMutation({
    mutationFn: () =>
      api<PtEnrolment>('/api/v1/pt/enrolments', {
        method: 'POST',
        body: {
          memberId: sellMemberId,
          ptPackageId: sellPackageId,
          trainerId: sellTrainerId,
          discountPaise: rupeesToPaise(sellDiscountRupees || '0'),
        },
      }),
    onSuccess: (enrolment) => {
      void queryClient.invalidateQueries({ queryKey: ['pt-enrolments'] })
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      setShowSellPt(false)
      setSuccess(`Sold ${enrolment.ptPackageName} to ${enrolment.memberName}. Invoice generated!`)
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const markSessionDone = useMutation({
    mutationFn: (enrolmentId: string) =>
      api<PtEnrolment>(`/api/v1/pt/enrolments/${enrolmentId}/session-done`, { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['pt-enrolments'] })
      void queryClient.invalidateQueries({ queryKey: ['commission-statement'] })
      setSuccess('PT session marked completed.')
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const recordPayment = useMutation({
    mutationFn: () =>
      api('/api/v1/payments/counter', {
        method: 'POST',
        body: {
          invoiceId: payInvoiceId,
          amountPaise: rupeesToPaise(payAmountRupees),
          method: payMethod,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['pt-enrolments'] })
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      void queryClient.invalidateQueries({ queryKey: ['commission-statement'] })
      setPayInvoiceId(null)
      setPayAmountRupees('')
      setSuccess('Payment recorded and trainer commission accrued proportionally.')
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const markPayout = useMutation({
    mutationFn: () =>
      api('/api/v1/commissions/payouts', {
        method: 'POST',
        body: {
          trainerId: selectedTrainerId,
          month: selectedMonth,
          amountPaise: rupeesToPaise(payoutRupees),
          payoutMode,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['commission-statement'] })
      setShowPayout(false)
      setPayoutRupees('')
      setSuccess('Trainer payout recorded.')
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const addAdjustment = useMutation({
    mutationFn: () =>
      api('/api/v1/commissions/adjustments', {
        method: 'POST',
        body: {
          trainerId: selectedTrainerId,
          month: selectedMonth,
          amountPaise: rupeesToPaise(adjRupees),
          reason: adjReason,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['commission-statement'] })
      setShowAdjustment(false)
      setAdjRupees('')
      setAdjReason('')
      setSuccess('Manual commission adjustment recorded.')
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  async function downloadFile(path: string, filename: string) {
    try {
      const res = await api<Response>(path, { raw: true })
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('common.error'))
    }
  }

  return (
    <div>
      <PageHeader
        title={t('pt.title')}
        action={
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              data-testid="add-trainer-btn"
              className="btn-secondary text-xs"
              onClick={() => setShowAddTrainer(true)}
            >
              + {t('pt.addTrainer')}
            </button>
            <button
              type="button"
              data-testid="add-pt-package-btn"
              className="btn-secondary text-xs"
              onClick={() => setShowAddPackage(true)}
            >
              + {t('pt.addPackage')}
            </button>
            <button
              type="button"
              data-testid="sell-pt-btn"
              className="btn-primary text-xs"
              onClick={() => setShowSellPt(true)}
            >
              + {t('pt.sellPt')}
            </button>
          </div>
        }
      />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}
      {success ? <div className="mb-3"><SuccessNote message={success} /></div> : null}

      <div className="flex gap-2 border-b border-slate-200 mb-4 overflow-x-auto">
        {(
          [
            ['enrolments', t('pt.enrolments')],
            ['statements', t('pt.statements')],
            ['trainers', t('pt.trainers')],
            ['packages', t('pt.packages')],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            data-testid={`pt-tab-${key}`}
            onClick={() => setTab(key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 whitespace-nowrap ${
              tab === key ? 'border-brand-700 text-brand-700' : 'border-transparent text-slate-500'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'enrolments' ? (
        enrolments.isLoading ? (
          <Loader />
        ) : (enrolments.data ?? []).length === 0 ? (
          <EmptyState title="No PT packages sold yet." />
        ) : (
          <ul className="space-y-2">
            {(enrolments.data ?? []).map((e) => (
              <li key={e.id} className="card" data-testid={`pt-enrolment-${e.id}`}>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold text-slate-900">
                      {e.memberName} · <span className="text-brand-800">{e.ptPackageName}</span>
                    </p>
                    <p className="text-xs text-slate-500">
                      Trainer: {e.trainerName} · Valid {formatDate(e.startDate)} to {formatDate(e.expiryDate)}
                    </p>
                    {e.packageType === 'SESSION_BASED' ? (
                      <p className="text-xs text-slate-700 mt-1 font-medium">
                        Sessions: {e.deliveredSessions} / {e.totalSessions} done ({e.remainingSessions} left)
                      </p>
                    ) : null}
                  </div>
                  <div className="text-right">
                    <p className="font-bold text-slate-900">{formatMoney(e.pricePaise - e.discountPaise)}</p>
                    <Badge tone={e.status === 'ACTIVE' ? 'green' : 'slate'}>{e.status}</Badge>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2 mt-3 pt-2 border-t border-slate-100">
                  <button
                    type="button"
                    data-testid={`pt-pay-btn-${e.id}`}
                    className="btn-secondary !px-3 !py-1.5 text-xs"
                    onClick={() => {
                      setPayInvoiceId(e.invoiceId)
                      setPayAmountRupees(paiseToRupeesString(e.pricePaise - e.discountPaise))
                    }}
                  >
                    Record Payment
                  </button>
                  {e.status === 'ACTIVE' && e.packageType === 'SESSION_BASED' && (e.remainingSessions ?? 0) > 0 ? (
                    <button
                      type="button"
                      data-testid={`pt-session-done-btn-${e.id}`}
                      className="btn-outline !px-3 !py-1.5 text-xs"
                      onClick={() => markSessionDone.mutate(e.id)}
                      disabled={markSessionDone.isPending}
                    >
                      {t('pt.sessionDone')} (-1)
                    </button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )
      ) : null}

      {tab === 'trainers' ? (
        trainers.isLoading ? (
          <Loader />
        ) : (trainers.data ?? []).length === 0 ? (
          <EmptyState title="No trainers added yet." />
        ) : (
          <ul className="space-y-2">
            {(trainers.data ?? []).map((tr) => (
              <li key={tr.id} className="card flex items-center justify-between gap-3">
                <div>
                  <p className="font-semibold text-slate-900">{tr.name}</p>
                  <p className="text-xs text-slate-500">
                    {tr.phone} · Rule: <span className="font-medium text-slate-700">{formatRule(tr.defaultCommissionRule)}</span>
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={tr.active ? 'green' : 'slate'}>{tr.active ? 'Active' : 'Inactive'}</Badge>
                  <button
                    type="button"
                    className="btn-outline !px-3 !py-1.5 text-xs"
                    onClick={() => {
                      setSelectedTrainerId(tr.id)
                      setTab('statements')
                    }}
                  >
                    Statement
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )
      ) : null}

      {tab === 'packages' ? (
        packages.isLoading ? (
          <Loader />
        ) : (packages.data ?? []).length === 0 ? (
          <EmptyState title="No PT packages defined yet." />
        ) : (
          <ul className="space-y-2">
            {(packages.data ?? []).map((p) => (
              <li key={p.id} className="card flex items-center justify-between gap-3">
                <div>
                  <p className="font-semibold text-slate-900">{p.name}</p>
                  <p className="text-xs text-slate-500">
                    {p.packageType === 'SESSION_BASED' ? `${p.totalSessions} sessions · ` : 'Time-based · '}
                    {p.validityDays} days · SAC {p.sacCode}
                  </p>
                </div>
                <p className="font-bold text-slate-900">{formatMoney(p.pricePaise)}</p>
              </li>
            ))}
          </ul>
        )
      ) : null}

      {tab === 'statements' ? (
        <div>
          <Card className="mb-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label="Trainer">
                <select
                  data-testid="stmt-trainer-select"
                  className="input"
                  value={selectedTrainerId}
                  onChange={(e) => setSelectedTrainerId(e.target.value)}
                >
                  <option value="">Select trainer</option>
                  {(trainers.data ?? []).map((tr) => (
                    <option key={tr.id} value={tr.id}>
                      {tr.name} ({formatRule(tr.defaultCommissionRule)})
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Month (YYYY-MM)">
                <input
                  type="month"
                  className="input"
                  value={selectedMonth}
                  onChange={(e) => setSelectedMonth(e.target.value)}
                />
              </Field>
            </div>

            {statement.data ? (
              <div className="mt-4">
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 mb-4">
                  <div className="rounded-lg bg-slate-50 p-3 border border-slate-200">
                    <p className="text-xs text-slate-500">Accrued</p>
                    <p className="text-base font-bold text-slate-900" data-testid="stmt-accrued">
                      {formatMoney(statement.data.accruedPaise)}
                    </p>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-3 border border-slate-200">
                    <p className="text-xs text-slate-500">Reversals</p>
                    <p className="text-base font-bold text-rose-700" data-testid="stmt-reversals">
                      {formatMoney(statement.data.reversalsPaise)}
                    </p>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-3 border border-slate-200">
                    <p className="text-xs text-slate-500">Adjustments</p>
                    <p className="text-base font-bold text-slate-900" data-testid="stmt-adjustments">
                      {formatMoney(statement.data.adjustmentsPaise)}
                    </p>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-3 border border-slate-200">
                    <p className="text-xs text-slate-500">Paid Out</p>
                    <p className="text-base font-bold text-emerald-700" data-testid="stmt-paid">
                      {formatMoney(statement.data.paidPaise)}
                    </p>
                  </div>
                  <div className="rounded-lg bg-brand-50 p-3 border border-brand-200">
                    <p className="text-xs text-brand-800 font-medium">Balance Due</p>
                    <p className="text-base font-bold text-brand-900" data-testid="stmt-balance-due">
                      {formatMoney(statement.data.balanceDuePaise)}
                    </p>
                  </div>
                </div>

                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="btn-primary text-xs"
                    onClick={() => {
                      setPayoutRupees(paiseToRupeesString(Math.max(0, statement.data.balanceDuePaise)))
                      setShowPayout(true)
                    }}
                  >
                    {t('pt.markPaid')}
                  </button>
                  <button
                    type="button"
                    className="btn-secondary text-xs"
                    onClick={() => setShowAdjustment(true)}
                  >
                    {t('pt.addAdjustment')}
                  </button>
                  <button
                    type="button"
                    className="btn-outline text-xs"
                    onClick={() =>
                      void downloadFile(
                        `/api/v1/commissions/statement.pdf?trainerId=${selectedTrainerId}&month=${selectedMonth}`,
                        `statement-${selectedTrainerId}-${selectedMonth}.pdf`
                      )
                    }
                  >
                    Export PDF
                  </button>
                  <button
                    type="button"
                    className="btn-outline text-xs"
                    onClick={() =>
                      void downloadFile(
                        `/api/v1/commissions/statement.csv?trainerId=${selectedTrainerId}&month=${selectedMonth}`,
                        `statement-${selectedTrainerId}-${selectedMonth}.csv`
                      )
                    }
                  >
                    Export CSV
                  </button>
                  {statement.data.whatsappShareUrl ? (
                    <a
                      href={statement.data.whatsappShareUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="btn-outline text-xs"
                    >
                      Share on WhatsApp
                    </a>
                  ) : null}
                </div>
              </div>
            ) : null}
          </Card>

          {statement.data ? (
            <Card>
              <h2 className="font-semibold text-slate-900 mb-3">Immutable Commission Ledger</h2>
              {statement.data.entries.length === 0 ? (
                <EmptyState title="No ledger entries for this month." />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {statement.data.entries.map((entry) => (
                    <li key={entry.id} className="py-2.5 flex items-center justify-between gap-2 text-sm">
                      <div>
                        <p className="font-medium text-slate-900">
                          {entry.entryType} {entry.sourceType ? `(${entry.sourceType})` : ''}
                        </p>
                        <p className="text-xs text-slate-500">
                          {entry.reason || (entry.payoutMode ? `Mode: ${entry.payoutMode}` : '')}
                          {entry.ruleSnapshot ? ` · Snapshot: ${formatRule(entry.ruleSnapshot)}` : ''}
                        </p>
                      </div>
                      <span
                        className={`font-semibold ${
                          entry.amountPaise < 0 ? 'text-rose-700' : 'text-slate-900'
                        }`}
                      >
                        {formatMoney(entry.amountPaise)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          ) : null}
        </div>
      ) : null}

      {/* Add Trainer Modal */}
      <Modal
        open={showAddTrainer}
        title={t('pt.addTrainer')}
        onClose={() => setShowAddTrainer(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowAddTrainer(false)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              data-testid="submit-trainer-btn"
              className="btn-primary"
              onClick={() => createTrainer.mutate()}
              disabled={!trainerName || !trainerPhone || createTrainer.isPending}
            >
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label={t('common.name')}>
          <input
            data-testid="trainer-name-input"
            className="input"
            value={trainerName}
            onChange={(e) => setTrainerName(e.target.value)}
          />
        </Field>
        <Field label={t('common.phone')}>
          <input
            data-testid="trainer-phone-input"
            className="input"
            value={trainerPhone}
            onChange={(e) => setTrainerPhone(e.target.value)}
          />
        </Field>
        <Field label="Default Commission Rule Type">
          <select
            data-testid="trainer-rule-type"
            className="input"
            value={ruleType}
            onChange={(e) => setRuleType(e.target.value as 'PERCENT' | 'FLAT' | 'SLAB')}
          >
            <option value="PERCENT">PERCENT (% of taxable value excluding GST)</option>
            <option value="FLAT">FLAT (Fixed Rs per package or per session)</option>
            <option value="SLAB">SLAB (Progressive monthly tiers)</option>
          </select>
        </Field>
        {ruleType === 'PERCENT' ? (
          <Field label="Commission Percentage (%)">
            <input
              data-testid="trainer-percent-input"
              type="number"
              className="input"
              value={percentRate}
              onChange={(e) => setPercentRate(e.target.value)}
            />
          </Field>
        ) : null}
        {ruleType === 'FLAT' ? (
          <>
            <Field label="Flat Basis">
              <select
                className="input"
                value={flatBasis}
                onChange={(e) => setFlatBasis(e.target.value as 'PER_PACKAGE' | 'PER_SESSION')}
              >
                <option value="PER_PACKAGE">Per Package</option>
                <option value="PER_SESSION">Per Session</option>
              </select>
            </Field>
            <Field label="Flat Amount (Rs)">
              <input
                type="number"
                className="input"
                value={flatRupees}
                onChange={(e) => setFlatRupees(e.target.value)}
              />
            </Field>
          </>
        ) : null}
      </Modal>

      {/* Add PT Package Modal */}
      <Modal
        open={showAddPackage}
        title={t('pt.addPackage')}
        onClose={() => setShowAddPackage(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowAddPackage(false)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              data-testid="submit-pt-package-btn"
              className="btn-primary"
              onClick={() => createPackage.mutate()}
              disabled={!pkgName || !pkgPriceRupees || createPackage.isPending}
            >
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label="Package Name">
          <input
            data-testid="pt-pkg-name-input"
            className="input"
            value={pkgName}
            onChange={(e) => setPkgName(e.target.value)}
          />
        </Field>
        <Field label="Package Type">
          <select
            data-testid="pt-pkg-type-select"
            className="input"
            value={pkgType}
            onChange={(e) => setPkgType(e.target.value as 'SESSION_BASED' | 'TIME_BASED')}
          >
            <option value="SESSION_BASED">Session-based (N sessions + validity days)</option>
            <option value="TIME_BASED">Time-based (Validity days only)</option>
          </select>
        </Field>
        {pkgType === 'SESSION_BASED' ? (
          <Field label="Total Sessions">
            <input
              data-testid="pt-pkg-sessions-input"
              type="number"
              className="input"
              value={pkgSessions}
              onChange={(e) => setPkgSessions(e.target.value)}
            />
          </Field>
        ) : null}
        <Field label="Validity (days)">
          <input
            data-testid="pt-pkg-validity-input"
            type="number"
            className="input"
            value={pkgValidity}
            onChange={(e) => setPkgValidity(e.target.value)}
          />
        </Field>
        <Field label="All-Inclusive Price (Rs)" hint="GST is split automatically using the SAC code">
          <input
            data-testid="pt-pkg-price-input"
            type="number"
            className="input"
            value={pkgPriceRupees}
            onChange={(e) => setPkgPriceRupees(e.target.value)}
          />
        </Field>
        <Field label="SAC Code">
          <input className="input font-mono" value={pkgSac} onChange={(e) => setPkgSac(e.target.value)} />
        </Field>
      </Modal>

      {/* Sell PT Package Modal */}
      <Modal
        open={showSellPt}
        title={t('pt.sellPt')}
        onClose={() => setShowSellPt(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowSellPt(false)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              data-testid="submit-sell-pt-btn"
              className="btn-primary"
              onClick={() => sellPt.mutate()}
              disabled={!sellMemberId || !sellPackageId || !sellTrainerId || sellPt.isPending}
            >
              {t('pt.sellPt')}
            </button>
          </>
        }
      >
        <Field label="Member">
          <select
            data-testid="sell-pt-member-select"
            className="input"
            value={sellMemberId}
            onChange={(e) => setSellMemberId(e.target.value)}
          >
            <option value="">Select member</option>
            {(members.data ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {m.name} · {m.phone}
              </option>
            ))}
          </select>
        </Field>
        <Field label="PT Package">
          <select
            data-testid="sell-pt-package-select"
            className="input"
            value={sellPackageId}
            onChange={(e) => setSellPackageId(e.target.value)}
          >
            <option value="">Select PT package</option>
            {(packages.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} · {formatMoney(p.pricePaise)}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Assigned Trainer">
          <select
            data-testid="sell-pt-trainer-select"
            className="input"
            value={sellTrainerId}
            onChange={(e) => setSellTrainerId(e.target.value)}
          >
            <option value="">Select trainer</option>
            {(trainers.data ?? []).map((tr) => (
              <option key={tr.id} value={tr.id}>
                {tr.name} ({formatRule(tr.defaultCommissionRule)})
              </option>
            ))}
          </select>
        </Field>
        <Field label="Discount (Rs, optional)">
          <input
            type="number"
            className="input"
            value={sellDiscountRupees}
            onChange={(e) => setSellDiscountRupees(e.target.value)}
          />
        </Field>
      </Modal>

      {/* Record Payment Modal */}
      <Modal
        open={Boolean(payInvoiceId)}
        title={t('billing.recordPayment')}
        onClose={() => setPayInvoiceId(null)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setPayInvoiceId(null)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              data-testid="submit-pt-payment-btn"
              className="btn-primary"
              onClick={() => recordPayment.mutate()}
              disabled={!payAmountRupees || recordPayment.isPending}
            >
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label={`${t('common.amount')} (Rs)`} hint="Supports partial or full payments">
          <input
            data-testid="pt-payment-amount-input"
            type="number"
            className="input"
            value={payAmountRupees}
            onChange={(e) => setPayAmountRupees(e.target.value)}
          />
        </Field>
        <Field label={t('billing.method')}>
          <select
            className="input"
            value={payMethod}
            onChange={(e) => setPayMethod(e.target.value as 'CASH' | 'CARD' | 'UPI')}
          >
            <option value="CASH">{t('billing.cash')}</option>
            <option value="CARD">{t('billing.card')}</option>
            <option value="UPI">{t('billing.upi')}</option>
          </select>
        </Field>
      </Modal>

      {/* Mark Payout Paid Modal */}
      <Modal
        open={showPayout}
        title={t('pt.markPaid')}
        onClose={() => setShowPayout(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowPayout(false)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn-primary"
              onClick={() => markPayout.mutate()}
              disabled={!payoutRupees || markPayout.isPending}
            >
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label="Payout Amount (Rs)">
          <input
            type="number"
            className="input"
            value={payoutRupees}
            onChange={(e) => setPayoutRupees(e.target.value)}
          />
        </Field>
        <Field label="Payout Mode">
          <select className="input" value={payoutMode} onChange={(e) => setPayoutMode(e.target.value)}>
            <option value="UPI">UPI</option>
            <option value="BANK_TRANSFER">Bank Transfer</option>
            <option value="CASH">Cash</option>
          </select>
        </Field>
      </Modal>

      {/* Manual Adjustment Modal */}
      <Modal
        open={showAdjustment}
        title={t('pt.addAdjustment')}
        onClose={() => setShowAdjustment(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowAdjustment(false)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn-primary"
              onClick={() => addAdjustment.mutate()}
              disabled={!adjRupees || !adjReason || addAdjustment.isPending}
            >
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label="Adjustment Amount (Rs, positive or negative)">
          <input
            type="number"
            className="input"
            value={adjRupees}
            onChange={(e) => setAdjRupees(e.target.value)}
          />
        </Field>
        <Field label="Reason">
          <input className="input" value={adjReason} onChange={(e) => setAdjReason(e.target.value)} />
        </Field>
      </Modal>
    </div>
  )
}
