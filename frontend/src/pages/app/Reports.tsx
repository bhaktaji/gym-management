import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '../../lib/api'
import { formatDate } from '../../lib/format'
import { Card, EmptyState, Loader, PageHeader } from '../../components/ui'

type RevenuePoint = { month: string; revenuePaise: number }
type ExpiringRow = { memberId: string; memberName: string; planName: string; expiryDate: string; daysLeft: number }
type CheckinPoint = { date: string; count: number }
type PlanPoint = { planName: string; activeMembers: number }

export function Reports() {
  const { t } = useTranslation()

  const revenue = useQuery({
    queryKey: ['revenue'],
    queryFn: () => api<RevenuePoint[]>('/api/v1/reports/revenue'),
  })

  const expiring = useQuery({
    queryKey: ['expiring', 30],
    queryFn: () => api<ExpiringRow[]>('/api/v1/reports/expiring?days=30'),
  })

  const checkins = useQuery({
    queryKey: ['checkins-report', 30],
    queryFn: () => api<CheckinPoint[]>('/api/v1/reports/checkins?days=30'),
  })

  const plans = useQuery({
    queryKey: ['plans-report'],
    queryFn: () => api<PlanPoint[]>('/api/v1/reports/plans'),
  })

  if (revenue.isLoading) return <Loader />

  const revenueData = (revenue.data ?? []).map((r) => ({
    month: r.month,
    rupees: Math.round(r.revenuePaise / 100),
  }))

  const checkinData = (checkins.data ?? []).map((c) => ({
    date: c.date,
    count: c.count,
  }))

  return (
    <div>
      <PageHeader title={t('reports.title')} />

      <Card>
        <h2 className="font-semibold text-slate-900 mb-3">{t('reports.revenue')}</h2>
        {revenueData.length === 0 ? (
          <EmptyState title={t('reports.noData')} />
        ) : (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={revenueData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip formatter={(v) => [`₹${v}`, 'Revenue']} />
                <Legend />
                <Bar dataKey="rupees" name="Revenue" fill="#0f766e" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('reports.checkins')}</h2>
        {checkinData.length === 0 ? (
          <EmptyState title={t('reports.noData')} />
        ) : (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={checkinData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                <Tooltip />
                <Line type="monotone" dataKey="count" name="Check-ins" stroke="#0d9488" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('reports.plans')}</h2>
        {(plans.data ?? []).length === 0 ? (
          <EmptyState title={t('reports.noData')} />
        ) : (
          <ul className="space-y-2">
            {(plans.data ?? []).map((p) => (
              <li key={p.planName} className="flex justify-between text-sm">
                <span className="text-slate-600">{p.planName}</span>
                <span className="font-medium">{p.activeMembers}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('reports.expiring')}</h2>
        {(expiring.data ?? []).length === 0 ? (
          <EmptyState title={t('reports.noData')} />
        ) : (
          <ul className="space-y-2">
            {(expiring.data ?? []).map((r) => (
              <li key={r.memberId} className="flex justify-between text-sm">
                <span className="text-slate-700">
                  {r.memberName} <span className="text-slate-400">· {r.planName}</span>
                </span>
                <span className="text-slate-500">
                  {formatDate(r.expiryDate)} ({r.daysLeft}d)
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
