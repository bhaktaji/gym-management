import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatMoney } from '../../lib/format'
import { ErrorNote, Loader, SuccessNote } from '../../components/ui'

type PayInfo = {
  invoiceNumber: string
  amountPaise: number
  amount: string
  gymName: string
  upiVpa: string
  upiDeepLink: string
  status: string
}

export function PayPage() {
  const { token } = useParams()
  const { t } = useTranslation()
  const [utr, setUtr] = useState('')
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['pay', token],
    queryFn: () => api<PayInfo>(`/api/v1/pay/${token}`, { auth: false }),
    retry: false,
  })

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await api(`/api/v1/pay/${token}/utr`, {
        method: 'POST',
        body: { amountPaise: data?.amountPaise, utr: utr.trim() },
        auth: false,
      })
      setDone(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.error'))
    }
  }

  if (isLoading) {
    return (
      <div className="max-w-md mx-auto px-4 py-10">
        <Loader />
      </div>
    )
  }

  if (!data) {
    return (
      <div className="max-w-md mx-auto px-4 py-10">
        <ErrorNote message={t('pay.invalid')} />
      </div>
    )
  }

  const qrUrl = data.upiDeepLink ? `/api/v1/pay/${token}/qr` : ''

  return (
    <div className="max-w-md mx-auto px-4 py-8">
      <div className="card text-center">
        <p className="text-sm text-slate-500">{t('pay.gym')}</p>
        <h1 className="text-xl font-semibold text-slate-900">{data.gymName}</h1>
        <p className="text-sm text-slate-500 mt-3">{t('pay.invoice')}</p>
        <p className="font-mono text-slate-700">{data.invoiceNumber}</p>

        <p className="text-sm text-slate-500 mt-4">{t('pay.amountDue')}</p>
        <p className="text-3xl font-bold text-slate-900">{formatMoney(data.amountPaise)}</p>

        {data.upiDeepLink ? (
          <div className="mt-5">
            <p className="text-sm text-slate-600 mb-2">{t('pay.scan')}</p>
            <img
              src={qrUrl}
              alt="UPI QR"
              className="mx-auto h-48 w-48 rounded-xl border border-slate-200"
            />
            <p className="mt-2 text-sm text-slate-500">UPI ID: {data.upiVpa}</p>
            <a
              href={data.upiDeepLink}
              className="btn-primary inline-block mt-3 px-6"
              target="_blank"
              rel="noreferrer"
            >
              {t('pay.scan')}
            </a>
          </div>
        ) : (
          <p className="mt-4 text-sm text-slate-500">{t('pay.invalid')}</p>
        )}
      </div>

      <div className="card mt-4">
        {done ? (
          <SuccessNote message={t('pay.success')} />
        ) : (
          <form onSubmit={submit}>
            <p className="text-sm text-slate-600 mb-3">{t('pay.instructions')}</p>
            {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}
            <label className="label">{t('pay.utrLabel')}</label>
            <input
              className="input font-mono tracking-wider"
              inputMode="numeric"
              maxLength={12}
              value={utr}
              onChange={(e) => setUtr(e.target.value.replace(/\D/g, ''))}
              placeholder="123456789012"
            />
            <button type="submit" className="btn-primary w-full mt-3" disabled={utr.length !== 12}>
              {t('pay.submit')}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
