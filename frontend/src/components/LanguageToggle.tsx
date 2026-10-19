import { useTranslation } from 'react-i18next'
import { LANGUAGES, setLanguage, currentLanguage } from '../i18n'

export function LanguageToggle() {
  const { t } = useTranslation()
  const active = currentLanguage()
  return (
    <div className="inline-flex rounded-lg border border-slate-300 overflow-hidden text-xs">
      {LANGUAGES.map((lang) => (
        <button
          key={lang.code}
          type="button"
          onClick={() => setLanguage(lang.code)}
          className={`px-2.5 py-1.5 font-medium ${
            active === lang.code ? 'bg-brand-700 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'
          }`}
          aria-label={lang.label}
        >
          {lang.label}
        </button>
      ))}
      <span className="sr-only">{t('common.language')}</span>
    </div>
  )
}
