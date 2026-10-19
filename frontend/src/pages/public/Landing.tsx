import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { formatMoney } from '../../lib/format'

type PublicConfig = {
  name: string
  pricing: {
    monthlyPricePaise: number
    currency: string
    included: string[]
    notCharged: string[]
  }
}

export function usePublicConfig() {
  return useQuery({
    queryKey: ['public-config'],
    queryFn: () => api<PublicConfig>('/api/v1/config/public', { auth: false }),
    staleTime: 5 * 60_000,
  })
}

const FEATURES = [
  { key: 'featureMembers', icon: '👥' },
  { key: 'featureBilling', icon: '🧾' },
  { key: 'featureUpi', icon: '📲' },
  { key: 'featureWhatsapp', icon: '💬' },
  { key: 'featureCheckin', icon: '✓' },
  { key: 'featureReports', icon: '📊' },
]

export function Landing() {
  const { t } = useTranslation()
  const { data } = usePublicConfig()
  const price = data?.pricing.monthlyPricePaise ?? 99900

  return (
    <div>
      <section className="bg-gradient-to-b from-brand-50 to-white">
        <div className="max-w-5xl mx-auto px-4 py-12 sm:py-20 text-center">
          <h1 className="text-3xl sm:text-5xl font-bold text-slate-900 leading-tight">{t('public.landingTagline')}</h1>
          <p className="mt-4 text-slate-600 max-w-2xl mx-auto">{t('public.landingSub')}</p>
          <div className="mt-7 flex flex-col sm:flex-row gap-3 justify-center">
            <Link to="/signup" className="btn-primary text-base px-6 py-3">
              {t('public.ctaStart')}
            </Link>
            <Link to="/pricing" className="btn-outline text-base px-6 py-3">
              {t('public.ctaPricing')}
            </Link>
          </div>
          <p className="mt-4 text-sm text-slate-500">
            {formatMoney(price)} / {t('public.perMonth')}
          </p>
        </div>
      </section>

      <section className="max-w-5xl mx-auto px-4 py-10">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => (
            <div key={f.key} className="card">
              <div className="text-2xl mb-2">{f.icon}</div>
              <h3 className="font-semibold text-slate-900">{t(`public.${f.key}`)}</h3>
              <p className="text-sm text-slate-600 mt-1">{t(`public.${f.key}Desc`)}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="bg-brand-700 text-white">
        <div className="max-w-5xl mx-auto px-4 py-10 text-center">
          <p className="text-lg font-medium">{t('public.gstNote')}</p>
          <Link to="/signup" className="inline-block mt-4 btn bg-white text-brand-800 hover:bg-brand-50">
            {t('public.ctaGetStarted')}
          </Link>
        </div>
      </section>
    </div>
  )
}
