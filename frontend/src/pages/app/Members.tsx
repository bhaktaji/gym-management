import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { api, ApiError } from '../../lib/api'
import { Badge, EmptyState, ErrorNote, Field, Loader, Modal, PageHeader, statusTone } from '../../components/ui'

export type Member = {
  id: string
  name: string
  phone: string
  email?: string
  gender?: string
  dob?: string
  address?: string
  state?: string
  status: string
  whatsappOptIn: boolean
  joinedAt?: string
  notes?: string
  pinFailedAttempts?: number
  pinLockedUntil?: string
  unboundPinBudgetExhausted?: boolean
  boundDevices?: { deviceId: string; userAgent: string; boundAt: string }[]
  pendingDeviceRequests?: { requestId: string; userAgent: string; clientIp: string; requestedAt: string }[]
}

const schema = z.object({
  name: z.string().min(2, 'Name is required'),
  phone: z.string().min(10, 'Enter a valid 10-digit mobile number'),
  email: z.string().email('Enter a valid email').optional().or(z.literal('')),
  gender: z.string().optional(),
  dob: z.string().optional(),
  address: z.string().optional(),
  state: z.string().optional(),
  whatsappOptIn: z.boolean(),
  notes: z.string().optional(),
})

type Form = z.infer<typeof schema>

export function Members() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Member | null>(null)
  const [showForm, setShowForm] = useState(params.get('new') === '1')
  const [error, setError] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['members'],
    queryFn: () => api<Member[]>('/api/v1/members'),
  })

  useEffect(() => {
    if (params.get('new') === '1') {
      setShowForm(true)
      params.delete('new')
      setParams(params, { replace: true })
    }
  }, [params, setParams])

  const create = useMutation({
    mutationFn: (values: Form) =>
      api<Member>('/api/v1/members', {
        method: 'POST',
        body: {
          name: values.name,
          phone: values.phone,
          email: values.email || null,
          gender: values.gender || null,
          dob: values.dob || null,
          address: values.address || null,
          state: values.state || null,
          whatsappOptIn: values.whatsappOptIn,
          notes: values.notes || null,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['members'] })
      setShowForm(false)
      setError('')
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const update = useMutation({
    mutationFn: (values: Form) =>
      api<Member>(`/api/v1/members/${editing?.id}`, {
        method: 'PUT',
        body: {
          name: values.name,
          phone: values.phone,
          email: values.email || null,
          gender: values.gender || null,
          dob: values.dob || null,
          address: values.address || null,
          state: values.state || null,
          whatsappOptIn: values.whatsappOptIn,
          notes: values.notes || null,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['members'] })
      setShowForm(false)
      setEditing(null)
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('common.error')),
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', phone: '', email: '', gender: '', dob: '', address: '', state: '', notes: '', whatsappOptIn: true },
  })

  const filtered = (data ?? []).filter((m) => {
    if (!search.trim()) return true
    const q = search.toLowerCase()
    return m.name.toLowerCase().includes(q) || m.phone.includes(q)
  })

  return (
    <div>
      <PageHeader
        title={t('members.title')}
        action={
          <button
            type="button"
            className="btn-primary"
            onClick={() => {
              setEditing(null)
              reset({
                name: '', phone: '', email: '', gender: '', dob: '', address: '', state: '', notes: '',
                whatsappOptIn: true,
              })
              setShowForm(true)
            }}
          >
            + {t('members.addTitle')}
          </button>
        }
      />

      <input
        className="input mb-3"
        placeholder={t('members.searchPlaceholder')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />

      {isLoading ? (
        <Loader />
      ) : filtered.length === 0 ? (
        <EmptyState title={t('members.noMembers')} />
      ) : (
        <ul className="space-y-2">
          {filtered.map((m) => (
            <li key={m.id}>
              <Link to={`/app/members/${m.id}`} className="card flex items-center justify-between gap-3 hover:border-brand-300">
                <div className="min-w-0">
                  <p className="font-medium text-slate-900 truncate">{m.name}</p>
                  <p className="text-sm text-slate-500">{m.phone}</p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {m.whatsappOptIn ? <span className="text-xs text-slate-400">💬</span> : null}
                  <Badge tone={statusTone(m.status)}>{m.status}</Badge>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Modal
        open={showForm}
        title={editing ? t('members.editTitle') : t('members.addTitle')}
        onClose={() => {
          setShowForm(false)
          setEditing(null)
        }}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowForm(false)}>
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              form="member-form"
              className="btn-primary"
              disabled={isSubmitting || create.isPending || update.isPending}
            >
              {t('common.save')}
            </button>
          </>
        }
      >
        {error ? <div className="mb-3"><ErrorNote message={error} /></div> : null}
        <form
          id="member-form"
          onSubmit={handleSubmit((values) => (editing ? update.mutate(values) : create.mutate(values)))}
        >
          <Field label={t('members.name')} error={errors.name?.message}>
            <input className="input" {...register('name')} />
          </Field>
          <Field label={t('members.phone')} error={errors.phone?.message}>
            <input className="input" inputMode="tel" {...register('phone')} />
          </Field>
          <Field label={`${t('common.email')} (${t('common.optional')})`} error={errors.email?.message}>
            <input className="input" type="email" {...register('email')} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('members.gender')} error={errors.gender?.message}>
              <select className="input" {...register('gender')}>
                <option value="">-</option>
                <option value="Male">Male</option>
                <option value="Female">Female</option>
                <option value="Other">Other</option>
              </select>
            </Field>
            <Field label={t('members.dob')} error={errors.dob?.message}>
              <input className="input" type="date" {...register('dob')} />
            </Field>
          </div>
          <Field label={t('members.address')} error={errors.address?.message}>
            <input className="input" {...register('address')} />
          </Field>
          <Field label={t('members.state')} error={errors.state?.message}>
            <input className="input" {...register('state')} />
          </Field>
          <label className="flex items-center gap-2 text-sm text-slate-700 mb-3">
            <input type="checkbox" className="h-4 w-4" {...register('whatsappOptIn')} />
            {t('members.whatsappOptIn')}
          </label>
          <p className="text-xs text-slate-500 -mt-2 mb-3">{t('members.whatsappOptInNote')}</p>
          <Field label={t('members.notes')} error={errors.notes?.message}>
            <textarea className="input" rows={2} {...register('notes')} />
          </Field>
        </form>
      </Modal>
    </div>
  )
}
