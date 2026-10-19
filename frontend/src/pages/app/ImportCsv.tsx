import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { Card, ErrorNote, Field, PageHeader, SuccessNote } from '../../components/ui'

type ImportResult = { totalRows: number; success: number; errors: string[] }

export function ImportCsv() {
  const { t } = useTranslation()
  const [csv, setCsv] = useState('')
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState('')

  const runMembers = useMutation({
    mutationFn: () => api<ImportResult>('/api/v1/import/members', { method: 'POST', body: { csv } }),
    onSuccess: (data) => {
      setResult(data)
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const runPayments = useMutation({
    mutationFn: () => api<ImportResult>('/api/v1/import/payments', { method: 'POST', body: { csv } }),
    onSuccess: (data) => {
      setResult(data)
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  return (
    <div>
      <PageHeader title={t('import.title')} />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}

      <Card>
        <Field label={t('import.csv')} hint={t('import.format')}>
          <textarea
            className="input font-mono text-xs"
            rows={8}
            value={csv}
            onChange={(e) => setCsv(e.target.value)}
            placeholder={t('import.paste')}
          />
        </Field>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="btn-primary"
            onClick={() => runMembers.mutate()}
            disabled={!csv.trim() || runMembers.isPending}
          >
            {t('import.members')}
          </button>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => runPayments.mutate()}
            disabled={!csv.trim() || runPayments.isPending}
          >
            {t('import.payments')}
          </button>
        </div>
      </Card>

      {result ? (
        <Card className="mt-4">
          <h2 className="font-semibold text-slate-900 mb-2">{t('import.result')}</h2>
          <p className="text-sm text-slate-700">
            {result.success} {t('import.success')} / {result.totalRows} ({result.totalRows - result.success}{' '}
            {t('import.failed')})
          </p>
          {result.errors.length > 0 ? (
            <>
              <p className="text-sm font-medium text-slate-700 mt-3">{t('import.errors')}</p>
              <ul className="mt-1 space-y-1">
                {result.errors.map((e, i) => (
                  <li key={i} className="text-xs text-red-700">
                    {e}
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <div className="mt-3">
              <SuccessNote message={t('import.success')} />
            </div>
          )}
        </Card>
      ) : null}
    </div>
  )
}
