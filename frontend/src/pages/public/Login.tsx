import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useAuth } from '../../auth/AuthContext'
import { ApiError } from '../../lib/api'
import { ErrorNote, Field, SuccessNote } from '../../components/ui'

const schema = z.object({
  phone: z.string().min(10, 'Enter a valid 10-digit mobile number'),
  password: z.string().min(1, 'Password is required'),
})

type Form = z.infer<typeof schema>

export function Login() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [resetDone, setResetDone] = useState('')

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Form>({ resolver: zodResolver(schema) })

  const onSubmit = async (values: Form) => {
    setError('')
    try {
      const user = await login(values.phone, values.password)
      navigate(user.role === 'SUPER_ADMIN' ? '/admin' : '/app')
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 'ACCOUNT_SUSPENDED') setError(t('auth.suspended'))
        else if (e.code === 'INVALID_CREDENTIALS') setError(t('auth.invalidCredentials'))
        else setError(e.message)
      } else {
        setError(t('common.error'))
      }
    }
  }

  const onForgot = async () => {
    const phone = window.prompt('Mobile number')
    if (!phone) return
    try {
      const res = await fetch('/api/v1/auth/password-reset/request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone }),
      })
      const data = await res.json()
      if (data.resetToken) {
        const newPassword = window.prompt('New password (min 8 characters)')
        if (newPassword) {
          await fetch('/api/v1/auth/password-reset/confirm', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token: data.resetToken, newPassword }),
          })
          setResetDone(t('auth.passwordResetDone'))
          return
        }
      }
      setResetDone(t('auth.resetRequested'))
    } catch {
      setResetDone(t('auth.resetRequested'))
    }
  }

  return (
    <div className="max-w-md mx-auto px-4 py-10">
      <h1 className="text-2xl font-bold text-slate-900">{t('auth.loginTitle')}</h1>
      <p className="text-slate-600 mt-1 mb-6">{t('auth.loginSub')}</p>

      {error ? <div className="mb-4"><ErrorNote message={error} /></div> : null}
      {resetDone ? <div className="mb-4"><SuccessNote message={resetDone} /></div> : null}

      <form onSubmit={handleSubmit(onSubmit)} className="card">
        <Field label={`${t('auth.phone')} (${t('common.phone')})`} error={errors.phone?.message}>
          <input className="input" placeholder="9876543210" inputMode="tel" {...register('phone')} />
        </Field>
        <Field label={t('auth.password')} error={errors.password?.message}>
          <input className="input" type="password" {...register('password')} />
        </Field>
        <button type="submit" className="btn-primary w-full" disabled={isSubmitting}>
          {isSubmitting ? t('common.saving') : t('auth.login')}
        </button>
        <button type="button" onClick={onForgot} className="w-full text-center text-sm text-brand-800 mt-3">
          {t('auth.forgotPassword')}
        </button>
      </form>

      <p className="text-sm text-slate-600 mt-4 text-center">
        {t('auth.noAccount')}{' '}
        <Link to="/signup" className="text-brand-800 font-medium">
          {t('auth.signup')}
        </Link>
      </p>
    </div>
  )
}
