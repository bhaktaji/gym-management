import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { Badge, EmptyState, ErrorNote, Field, Loader, Modal, PageHeader, statusTone } from '../../components/ui'

type Lead = {
  id: string
  name: string
  phone: string
  source?: string
  status: string
  notes?: string
  convertedToMemberId?: string
}

export function Leads() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [source, setSource] = useState('Walk-in')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (params.get('new') === '1') {
      setShowForm(true)
      params.delete('new')
      setParams(params, { replace: true })
    }
  }, [params, setParams])

  const { data, isLoading } = useQuery({
    queryKey: ['leads'],
    queryFn: () => api<Lead[]>('/api/v1/leads'),
  })

  const create = useMutation({
    mutationFn: () =>
      api<Lead>('/api/v1/leads', {
        method: 'POST',
        body: { name, phone, source, notes: notes || null },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['leads'] })
      setShowForm(false)
      setName('')
      setPhone('')
      setNotes('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const setStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      api(`/api/v1/leads/${id}`, { method: 'PUT', body: { status } }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['leads'] }),
  })

  const convert = useMutation({
    mutationFn: (id: string) => api(`/api/v1/leads/${id}/convert`, { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['leads'] })
      void queryClient.invalidateQueries({ queryKey: ['members'] })
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  return (
    <div>
      <PageHeader
        title={t('leads.title')}
        action={
          <button type="button" className="btn-primary" onClick={() => setShowForm(true)}>
            + {t('leads.addTitle')}
          </button>
        }
      />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}

      {isLoading ? (
        <Loader />
      ) : (data ?? []).length === 0 ? (
        <EmptyState title={t('leads.noLeads')} />
      ) : (
        <ul className="space-y-2">
          {(data ?? []).map((lead) => (
            <li key={lead.id} className="card">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-medium text-slate-900">{lead.name}</p>
                  <p className="text-xs text-slate-500">
                    {lead.phone}
                    {lead.source ? ` · ${lead.source}` : ''}
                  </p>
                </div>
                <Badge tone={statusTone(lead.status)}>
                  {t(
                    `leads.${lead.status.toLowerCase()}`,
                    lead.status,
                  )}
                </Badge>
              </div>
              {lead.notes ? <p className="text-xs text-slate-500 mt-2">{lead.notes}</p> : null}
              <div className="flex flex-wrap gap-2 mt-3">
                {lead.status !== 'CONVERTED' ? (
                  <>
                    <button
                      type="button"
                      className="btn-primary !px-3 !py-1.5 text-xs"
                      onClick={() => convert.mutate(lead.id)}
                    >
                      {t('leads.convert')}
                    </button>
                    <button
                      type="button"
                      className="btn-outline !px-3 !py-1.5 text-xs"
                      onClick={() => setStatus.mutate({ id: lead.id, status: 'CONTACTED' })}
                    >
                      {t('leads.contacted')}
                    </button>
                    <button
                      type="button"
                      className="btn-outline !px-3 !py-1.5 text-xs"
                      onClick={() => setStatus.mutate({ id: lead.id, status: 'LOST' })}
                    >
                      {t('leads.lost')}
                    </button>
                  </>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal
        open={showForm}
        title={t('leads.addTitle')}
        onClose={() => setShowForm(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowForm(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => create.mutate()} disabled={create.isPending}>
              {t('common.save')}
            </button>
          </>
        }
      >
        <Field label={t('common.name')}>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label={t('common.phone')}>
          <input className="input" inputMode="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
        </Field>
        <Field label={t('leads.source')}>
          <select className="input" value={source} onChange={(e) => setSource(e.target.value)}>
            <option value="Walk-in">{t('leads.walkIn')}</option>
            <option value="Instagram">{t('leads.instagram')}</option>
            <option value="Referral">{t('leads.referral')}</option>
            <option value="Other">{t('leads.other')}</option>
          </select>
        </Field>
        <Field label={t('leads.notes')}>
          <textarea className="input" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </Modal>
    </div>
  )
}
