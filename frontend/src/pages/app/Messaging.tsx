import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { Badge, Card, EmptyState, Loader, PageHeader, statusTone } from '../../components/ui'

type OutboxMessage = {
  id: string
  memberId: string
  memberName: string
  phone: string
  waLink: string
  status: string
  variables?: Record<string, string>
}

export function Messaging() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const outbox = useQuery({
    queryKey: ['outbox'],
    queryFn: () => api<OutboxMessage[]>('/api/v1/messaging/outbox'),
  })

  const generate = useMutation({
    mutationFn: () => api('/api/v1/messaging/outbox/generate', { method: 'POST' }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['outbox'] }),
  })

  const markSent = useMutation({
    mutationFn: (id: string) => api(`/api/v1/messaging/outbox/${id}/sent`, { method: 'POST' }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['outbox'] }),
  })

  const messages = outbox.data ?? []

  return (
    <div>
      <PageHeader
        title={t('messaging.title')}
        subtitle={t('messaging.sub')}
        action={
          <button type="button" className="btn-primary" onClick={() => generate.mutate()} disabled={generate.isPending}>
            {t('messaging.generate')}
          </button>
        }
      />

      {outbox.isLoading ? (
        <Loader />
      ) : messages.length === 0 ? (
        <EmptyState title={t('messaging.noMessages')} />
      ) : (
        <ul className="space-y-2">
          {messages.map((m) => (
            <li key={m.id} className="card">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-medium text-slate-900">{m.memberName}</p>
                  <p className="text-xs text-slate-500">{m.phone}</p>
                </div>
                <Badge tone={statusTone(m.status)}>
                  {m.status === 'QUIET_HOURS' ? t('messaging.quietHours') : m.status === 'SENT' ? t('messaging.sent') : t('messaging.pending')}
                </Badge>
              </div>
              {m.variables ? (
                <p className="text-xs text-slate-500 mt-2">
                  {m.variables.planName} · {m.variables.expiryDate} · {m.variables.amountDue}
                </p>
              ) : null}
              <div className="flex flex-wrap gap-2 mt-3">
                {m.status !== 'SENT' ? (
                  <>
                    <a href={m.waLink} target="_blank" rel="noreferrer" className="btn-primary !px-3 !py-1.5 text-xs">
                      {t('messaging.send')}
                    </a>
                    <button
                      type="button"
                      className="btn-outline !px-3 !py-1.5 text-xs"
                      onClick={() => markSent.mutate(m.id)}
                    >
                      {t('messaging.markSent')}
                    </button>
                  </>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}

      <Card className="mt-4">
        <p className="text-xs text-slate-500">{t('messaging.optedOut')}</p>
      </Card>
    </div>
  )
}
