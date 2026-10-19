import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { usePublicConfig } from './Landing'
import { formatMoney } from '../../lib/format'

export function Pricing() {
  const { t } = useTranslation()
  const { data } = usePublicConfig()
  const pricing = data?.pricing

  return (
    <div className="max-w-3xl mx-auto px-4 py-10">
      <div className="text-center">
        <h1 className="text-3xl font-bold text-slate-900">{t('public.pricingTitle')}</h1>
        <p className="mt-2 text-slate-600">{t('public.pricingSub')}</p>
      </div>

      <div className="card mt-8">
        <div className="text-center py-4">
          <p className="text-4xl font-bold text-slate-900">{formatMoney(pricing?.monthlyPricePaise ?? 99900)}</p>
          <p className="text-slate-500 mt-1">{t('public.perMonth')}</p>
        </div>

        <div className="grid sm:grid-cols-2 gap-6 mt-4">
          <div>
            <h2 className="font-semibold text-slate-900 mb-2">{t('public.priceIncludes')}</h2>
            <ul className="space-y-2">
              {(pricing?.included ?? []).map((item) => (
                <li key={item} className="flex gap-2 text-sm text-slate-700">
                  <span className="text-emerald-600">✓</span>
                  {item}
                </li>
              ))}
            </ul>
          </div>
          <div>
            <h2 className="font-semibold text-slate-900 mb-2">{t('public.priceExcludes')}</h2>
            <ul className="space-y-2">
              {(pricing?.notCharged ?? []).map((item) => (
                <li key={item} className="flex gap-2 text-sm text-slate-700">
                  <span className="text-brand-600">✓</span>
                  {item}
                </li>
              ))}
            </ul>
          </div>
        </div>

        <p className="text-xs text-slate-500 mt-6 text-center">{t('public.gstNote')}</p>

        <div className="mt-6 flex justify-center">
          <Link to="/signup" className="btn-primary px-6 py-3">
            {t('public.ctaGetStarted')}
          </Link>
        </div>
      </div>
    </div>
  )
}
