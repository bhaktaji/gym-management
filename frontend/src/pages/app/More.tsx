import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Card, PageHeader } from '../../components/ui'

export function More() {
  const { t } = useTranslation()
  const links = [
    { to: '/app/plans', label: t('plans.title'), icon: '📋' },
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
