import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { Card, PageHeader } from '../../components/ui'

type GymInfo = {
  settings?: {
    attendanceEnabled?: boolean
  }
}

export function More() {
  const { t } = useTranslation()
  const gym = useQuery({
    queryKey: ['gym'],
    queryFn: () => api<GymInfo>('/api/v1/tenant/gym'),
  })

  const attendanceEnabled = Boolean(gym.data?.settings?.attendanceEnabled)

  const links = [
    { to: '/app/pt', label: t('pt.title'), icon: '🏋️' },
    { to: '/app/plans', label: t('plans.title'), icon: '📋' },
    ...(attendanceEnabled ? [{ to: '/app/checkins', label: t('checkins.title'), icon: '✓' }] : []),
    { to: '/app/messages', label: t('messaging.title'), icon: '💬' },
    { to: '/app/leads', label: t('leads.title'), icon: '🧲' },
    { to: '/app/reports', label: t('reports.title'), icon: '📊' },
    { to: '/app/import', label: t('import.title'), icon: '📥' },
    { to: '/app/settings', label: t('settings.title'), icon: '⚙️' },
  ]

  return (
    <div>
      <PageHeader title={t('nav.more')} />
      <Card>
        <ul className="divide-y divide-slate-100">
          {links.map((l) => (
            <li key={l.to}>
              <Link to={l.to} className="flex items-center gap-3 py-3 text-slate-800">
                <span className="text-xl">{l.icon}</span>
                <span className="font-medium">{l.label}</span>
              </Link>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  )
}
