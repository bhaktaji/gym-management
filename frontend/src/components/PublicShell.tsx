import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { LanguageToggle } from './LanguageToggle'

export function PublicShell({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation()
  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white/80 backdrop-blur sticky top-0 z-30">
        <div className="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2">
            <span className="h-8 w-8 rounded-lg bg-brand-700 text-white grid place-items-center font-bold">G</span>
            <span className="font-semibold">{t('app.name')}</span>
          </Link>
          <div className="flex items-center gap-3">
            <LanguageToggle />
            <Link to="/login" className="text-sm font-medium text-brand-800 hover:underline">
              {t('public.ctaLogin')}
            </Link>
          </div>
        </div>
      </header>
      <main>{children}</main>
      <footer className="border-t border-slate-200 py-8 text-center text-sm text-slate-500">
        <p>
          {t('app.name')} · {t('app.tagline')}
        </p>
      </footer>
    </div>
  )
}
