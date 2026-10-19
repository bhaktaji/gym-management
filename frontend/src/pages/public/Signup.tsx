import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { api, ApiError } from '../../lib/api'
import { ErrorNote, Field, SuccessNote } from '../../components/ui'

const schema = z
  .object({
    gymName: z.string().min(2, 'Gym name is required'),
    ownerName: z.string().min(2, 'Your name is required'),
    phone: z.string().min(10, 'Enter a valid 10-digit mobile number'),
    email: z.string().email('Enter a valid email').optional().or(z.literal('')),
    city: z.string().optional(),
    state: z.string().optional(),
    password: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  })

type Form = z.infer<typeof schema>

export function Signup() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', city: '', state: '' },
  })

  const onSubmit = async (values: Form) => {
    setError('')
    try {
      await api('/api/v1/auth/signup', {
        method: 'POST',
        body: {
          gymName: values.gymName,
          ownerName: values.ownerName,
          phone: values.phone,
          email: values.email || null,
          city: values.city || null,
          state: values.state || null,
          password: values.password,
        },
        auth: false,
      })
      setDone(true)
      setTimeout(() => navigate('/login'), 2500)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('common.error'))
    }
  }

  if (done) {
    return (
      <div className="max-w-md mx-auto px-4 py-10">
        <div className="card">
          <SuccessNote message={t('auth.pendingApproval')} />
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-md mx-auto px-4 py-10">
      <h1 className="text-2xl font-bold text-slate-900">{t('auth.signupTitle')}</h1>
      <p className="text-slate-600 mt-1 mb-6">{t('auth.signupSub')}</p>

      {error ? <div className="mb-4"><ErrorNote message={error} /></div> : null}

      <form onSubmit={handleSubmit(onSubmit)} className="card">
        <Field label={t('auth.gymName')} error={errors.gymName?.message}>
          <input className="input" {...register('gymName')} />
        </Field>
        <Field label={t('auth.ownerName')} error={errors.ownerName?.message}>
          <input className="input" {...register('ownerName')} />
        </Field>
        <Field label={t('common.phone')} error={errors.phone?.message}>
          <input className="input" inputMode="tel" placeholder="9876543210" {...register('phone')} />
        </Field>
        <Field label={`${t('common.email')} (${t('common.optional')})`} error={errors.email?.message}>
          <input className="input" type="email" {...register('email')} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('auth.city')} error={errors.city?.message}>
            <input className="input" {...register('city')} />
          </Field>
          <Field label={t('auth.state')} error={errors.state?.message}>
            <input className="input" {...register('state')} />
          </Field>
        </div>
        <Field label={t('auth.password')} error={errors.password?.message}>
          <input className="input" type="password" {...register('password')} />
        </Field>
        <Field label={t('auth.confirmPassword')} error={errors.confirmPassword?.message}>
          <input className="input" type="password" {...register('confirmPassword')} />
        </Field>
        <button type="submit" className="btn-primary w-full" disabled={isSubmitting}>
          {isSubmitting ? t('common.saving') : t('auth.signup')}
        </button>
      </form>

      <p className="text-sm text-slate-600 mt-4 text-center">
        {t('auth.haveAccount')}{' '}
        <Link to="/login" className="text-brand-800 font-medium">
          {t('auth.login')}
        </Link>
      </p>
    </div>
  )
}
