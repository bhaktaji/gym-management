import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatMoney } from '../../lib/format'
import { Badge, EmptyState, ErrorNote, Field, Loader, Modal, PageHeader } from '../../components/ui'

type Plan = {
  id: string
  name: string
  durationDays: number
  pricePaise: number
  active: boolean
}

export function Plans() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Plan | null>(null)
  const [error, setError] = useState('')
  const [name, setName] = useState('')
  const [duration, setDuration] = useState('30')
  const [price, setPrice] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['plans'],
    queryFn: () => api<Plan[]>('/api/v1/plans'),
  })

  const openAdd = () => {
    setEditing(null)
    setName('')
    setDuration('30')
    setPrice('')
    setShowForm(true)
  }

  const openEdit = (p: Plan) => {
    setEditing(p)
    setName(p.name)
    setDuration(String(p.durationDays))
    setPrice(String(p.pricePaise / 100))
    setShowForm(true)
  }

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name,
        durationDays: Number(duration),
        pricePaise: Math.round(Number(price) * 100),
      }
      return editing
        ? api<Plan>(`/api/v1/plans/${editing.id}`, { method: 'PUT', body: { ...body, active: editing.active } })
        : api<Plan>('/api/v1/plans', { method: 'POST', body })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['plans'] })
      setShowForm(false)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const toggle = useMutation({
    mutationFn: (p: Plan) => api<Plan>(`/api/v1/plans/${p.id}`, { method: 'PUT', body: { active: !p.active } }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['plans'] }),
  })

  return (
    <div>
      <PageHeader
        title={t('plans.title')}
        action={
          <button type="button" className="btn-primary" onClick={openAdd}>
            + {t('plans.addTitle')}
          </button>
        }
      />

      {isLoading ? (
        <Loader />
      ) : (data ?? []).length === 0 ? (
        <EmptyState title={t('plans.noPlans')} />
      ) : (
        <ul className="space-y-2">
          {(data ?? []).map((p) => (
            <li key={p.id} className="card flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="font-medium text-slate-900">{p.name}</p>
                <p className="text-sm text-slate-500">
                  {p.durationDays} {t('plans.duration').split(' ').pop()} · {formatMoney(p.pricePaise)}
                </p>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <Badge tone={p.active ? 'green' : 'slate'}>{p.active ? t('plans.active') : t('common.no')}</Badge>
                <button type="button" className="btn-outline !px-2 !py-1 text-xs" onClick={() => openEdit(p)}>
                  {t('common.edit')}
                </button>
                <button
                  type="button"
                  className="btn-outline !px-2 !py-1 text-xs"
                  onClick={() => toggle.mutate(p)}
                >
                  {p.active ? t('common.no') : t('common.yes')}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal
        open={showForm}
        title={editing ? t('plans.editTitle') : t('plans.addTitle')}
        onClose={() => setShowForm(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowForm(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => save.mutate()} disabled={save.isPending}>
              {t('common.save')}
            </button>
          </>
        }
      >
        {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}
        <Field label={t('plans.name')}>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label={t('plans.duration')}>
          <input
            className="input"
            type="number"
            min={1}
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
          />
        </Field>
        <Field label={t('plans.price')} hint={t('plans.priceNote')}>
          <input
            className="input"
            type="number"
            min={0}
            step="0.01"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
          />
        </Field>
      </Modal>
    </div>
  )
}
