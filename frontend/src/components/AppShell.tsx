import { NavLink, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { api } from '../lib/api'
import { LanguageToggle } from './LanguageToggle'

type GymInfo = {
  id: string
  name: string
  slug?: string
  settings?: {
    attendanceEnabled?: boolean
  }
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation()
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const gym = useQuery({
    queryKey: ['gym'],
    queryFn: () => api<GymInfo>('/api/v1/tenant/gym'),
    enabled: Boolean(user && user.role !== 'SUPER_ADMIN'),
  })

  const attendanceEnabled = Boolean(gym.data?.settings?.attendanceEnabled)

  const ownerLinks = [
    { to: '/app', label: 'home', end: true },
    { to: '/app/members', label: 'members' },
    { to: '/app/billing', label: 'billing' },
    { to: '/app/pt', label: 'pt' },
    ...(attendanceEnabled
      ? [{ to: '/app/checkins', label: 'checkins' }]
      : [{ to: '/app/messages', label: 'messages' }]),
  ]

  return (
    <div className="min-h-screen pb-20">
      <header className="bg-white border-b border-slate-200 sticky top-0 z-30">
        <div className="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 min-w-0">
            <span className="h-8 w-8 rounded-lg bg-brand-700 text-white grid place-items-center font-bold shrink-0">G</span>
            <div className="min-w-0">
              <p className="font-semibold leading-tight truncate">{t('app.name')}</p>
              <p className="text-xs text-slate-500 truncate">{user?.name}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <LanguageToggle />
            <button
              type="button"
              className="text-sm text-slate-600 hover:text-slate-900 px-2"
              onClick={() => {
                logout()
                navigate('/login')
              }}
            >
              {t('nav.logout')}
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-4">{children}</main>

      <nav className="fixed bottom-0 inset-x-0 bg-white border-t border-slate-200 safe-bottom z-30">
        <div className="max-w-5xl mx-auto grid grid-cols-6">
          {ownerLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) =>
                `flex flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${
                  isActive ? 'text-brand-700' : 'text-slate-500'
                }`
              }
            >
              <span className="text-lg leading-none">{icon(link.label)}</span>
              {t(`nav.${link.label}`)}
            </NavLink>
          ))}
          <NavLink
            to="/app/more"
            className={({ isActive }) =>
              `flex flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${
                isActive ? 'text-brand-700' : 'text-slate-500'
              }`
            }
          >
            <span className="text-lg leading-none">⋯</span>
            {t('nav.more')}
          </NavLink>
        </div>
      </nav>
    </div>
  )
}

function icon(label: string): string {
  switch (label) {
    case 'home':
      return '⌂'
    case 'members':
      return '👥'
    case 'billing':
      return '₹'
    case 'pt':
      return '🏋️'
    case 'checkins':
      return '✓'
    case 'messages':
      return '💬'
    default:
      return '•'
  }
}
