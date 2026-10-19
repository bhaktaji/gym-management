import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { formatMoney } from '../../lib/format'
import { Card, EmptyState, ErrorNote, Loader, PageHeader } from '../../components/ui'

type DashboardData = {
  activeMembers: number
  activeMemberships: number
  pendingPayments: number
  todayCheckins: number
  expiringThisWeek: number
}

type RevenuePoint = { month: string; revenuePaise: number }
type ExpiringRow = { memberId: string; memberName: string; planName: string; expiryDate: string; daysLeft: number }

export function Dashboard() {
  const { t } = useTranslation()

  const dashboard = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api<DashboardData>('/api/v1/reports/dashboard'),
  })

  const revenue = useQuery({
    queryKey: ['revenue'],
    queryFn: () => api<RevenuePoint[]>('/api/v1/reports/revenue'),
  })

  const expiring = useQuery({
    queryKey: ['expiring', 7],
    queryFn: () => api<ExpiringRow[]>('/api/v1/reports/expiring?days=7'),
  })

  if (dashboard.isLoading) return <Loader label={t('common.loading')} />
  if (dashboard.isError) return <ErrorNote message={(dashboard.error as Error).message} />

  const stats = dashboard.data
  const cards = [
    { label: t('dashboard.activeMembers'), value: stats?.activeMembers ?? 0, to: '/app/members' },
    { label: t('dashboard.activeMemberships'), value: stats?.activeMemberships ?? 0, to: '/app/members' },
    { label: t('dashboard.pendingPayments'), value: stats?.pendingPayments ?? 0, to: '/app/billing' },
    { label: t('dashboard.todayCheckins'), value: stats?.todayCheckins ?? 0, to: '/app/checkins' },
    { label: t('dashboard.expiringThisWeek'), value: stats?.expiringThisWeek ?? 0, to: '/app/members' },
  ]

  return (
    <div>
      <PageHeader title={t('dashboard.title')} />

      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        {cards.map((c) => (
          <Link key={c.label} to={c.to} className="card hover:border-brand-300">
            <p className="text-2xl font-bold text-slate-900">{c.value}</p>
            <p className="text-xs text-slate-500 mt-0.5">{c.label}</p>
          </Link>
        ))}
      </div>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('dashboard.quickActions')}</h2>
        <div className="grid grid-cols-2 gap-2">
          <Link to="/app/members?new=1" className="btn-secondary">
            {t('dashboard.newMember')}
          </Link>
          <Link to="/app/leads?new=1" className="btn-secondary">
            {t('dashboard.newLead')}
          </Link>
          <Link to="/app/checkins" className="btn-secondary">
            {t('dashboard.checkin')}
          </Link>
          <Link to="/app/billing" className="btn-secondary">
            {t('dashboard.recordPayment')}
          </Link>
        </div>
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('dashboard.revenue')}</h2>
        {revenue.data && revenue.data.length > 0 ? (
          <ul className="space-y-2">
            {revenue.data.map((r) => (
              <li key={r.month} className="flex justify-between text-sm">
                <span className="text-slate-600">{r.month}</span>
                <span className="font-medium text-slate-900">{formatMoney(r.revenuePaise)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState title={t('reports.noData')} />
        )}
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900 mb-3">{t('dashboard.expiring')}</h2>
        {expiring.data && expiring.data.length > 0 ? (
          <ul className="space-y-2">
            {expiring.data.map((r) => (
              <li key={r.memberId} className="flex items-center justify-between text-sm">
                <Link to={`/app/members/${r.memberId}`} className="text-brand-800 font-medium truncate">
                  {r.memberName}
                </Link>
                <span className="text-slate-500 shrink-0 ml-2">
                  {r.planName} · {r.daysLeft}d
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState title={t('reports.noData')} />
        )}
      </Card>
    </div>
  )
}
