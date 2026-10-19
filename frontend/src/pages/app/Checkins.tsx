import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import { EmptyState, ErrorNote, Field, Loader, Modal, PageHeader } from '../../components/ui'

type Checkin = {
  id: string
  memberId: string
  memberName: string
  checkInAt: string
  checkOutAt?: string
  date: string
}

type Member = { id: string; name: string; phone: string; status: string }

export function Checkins() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [showCheckin, setShowCheckin] = useState(false)
  const [memberId, setMemberId] = useState('')
  const [error, setError] = useState('')

  const today = useQuery({
    queryKey: ['checkins', 'today'],
    queryFn: () => api<Checkin[]>('/api/v1/checkins/today'),
  })

  const members = useQuery({
    queryKey: ['members', 'active'],
    queryFn: () => api<Member[]>('/api/v1/members?status=ACTIVE'),
  })

  const checkin = useMutation({
    mutationFn: () => api<Checkin>('/api/v1/checkins', { method: 'POST', body: { memberId } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['checkins'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setShowCheckin(false)
      setMemberId('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const checkout = useMutation({
    mutationFn: (id: string) => api(`/api/v1/checkins/${id}/checkout`, { method: 'POST' }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['checkins'] }),
  })

  return (
    <div>
      <PageHeader
        title={t('checkins.title')}
        action={
          <button type="button" className="btn-primary" onClick={() => setShowCheckin(true)}>
            + {t('checkins.checkIn')}
          </button>
        }
      />

      {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}

      {today.isLoading ? (
        <Loader />
      ) : (today.data ?? []).length === 0 ? (
        <EmptyState title={t('checkins.noCheckins')} />
      ) : (
        <ul className="space-y-2">
          {(today.data ?? []).map((c) => (
            <li key={c.id} className="card flex items-center justify-between gap-3">
              <div>
                <p className="font-medium text-slate-900">{c.memberName}</p>
                <p className="text-xs text-slate-500">{formatDateTime(c.checkInAt)}</p>
              </div>
              {c.checkOutAt ? (
                <span className="text-xs text-slate-500">{formatDateTime(c.checkOutAt)}</span>
              ) : (
                <button
                  type="button"
                  className="btn-outline !px-3 !py-1.5 text-xs"
                  onClick={() => checkout.mutate(c.id)}
                >
                  {t('checkins.checkOut')}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <Modal
        open={showCheckin}
        title={t('checkins.checkIn')}
        onClose={() => setShowCheckin(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowCheckin(false)}>
              {t('common.cancel')}
            </button>
            <button type="button" className="btn-primary" onClick={() => checkin.mutate()} disabled={!memberId}>
              {t('checkins.checkIn')}
            </button>
          </>
        }
      >
        <Field label={t('checkins.selectMember')}>
          <select className="input" value={memberId} onChange={(e) => setMemberId(e.target.value)}>
            <option value="">-</option>
            {(members.data ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {m.name} · {m.phone}
              </option>
            ))}
          </select>
        </Field>
      </Modal>
    </div>
  )
}
