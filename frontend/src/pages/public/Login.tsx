import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useAuth } from '../../auth/AuthContext'
import { api, ApiError } from '../../lib/api'
import { ErrorNote, Field, Modal, SuccessNote } from '../../components/ui'

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
  const [showForgot, setShowForgot] = useState(false)
  const [forgotPhone, setForgotPhone] = useState('')
  const [resetTokenInput, setResetTokenInput] = useState('')
  const [newPasswordInput, setNewPasswordInput] = useState('')
  const [forgotMode, setForgotMode] = useState<'request' | 'confirm'>('request')

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

  const submitResetRequest = async () => {
    if (!forgotPhone.trim()) return
    setError('')
    try {
      const res = await api<{ success: boolean; resetToken?: string }>('/api/v1/auth/password-reset/request', {
        method: 'POST',
        body: { phone: forgotPhone.trim() },
        auth: false,
      })
      if (res?.resetToken) {
        setResetTokenInput(res.resetToken)
      }
      setResetDone(t('auth.resetRequested'))
      setForgotMode('confirm')
    } catch {
      setResetDone(t('auth.resetRequested'))
      setForgotMode('confirm')
    }
  }

  const submitResetConfirm = async () => {
    if (!resetTokenInput.trim() || newPasswordInput.length < 8) return
    setError('')
    try {
      await api('/api/v1/auth/password-reset/confirm', {
        method: 'POST',
        body: {
          token: resetTokenInput.trim(),
          newPassword: newPasswordInput,
          phone: forgotPhone.trim() || undefined,
        },
        auth: false,
      })
      setResetDone(t('auth.passwordResetDone'))
      setShowForgot(false)
      setResetTokenInput('')
      setNewPasswordInput('')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('common.error'))
    }
  }

  return (
    <div className="max-w-md mx-auto px-4 py-10">
      <h1 className="text-2xl font-bold text-slate-900">{t('auth.loginTitle')}</h1>
      <p className="text-slate-600 mt-1 mb-6">{t('auth.loginSub')}</p>

      {error ? <div className="mb-4"><ErrorNote message={error} /></div> : null}
      {resetDone ? <div className="mb-4"><SuccessNote message={resetDone} /></div> : null}

      <form onSubmit={handleSubmit(onSubmit)} className="card">
        <Field label={t('common.phone')} error={errors.phone?.message}>
          <input className="input" placeholder="9876543210" inputMode="tel" {...register('phone')} />
        </Field>
        <Field label={t('auth.password')} error={errors.password?.message}>
          <input className="input" type="password" {...register('password')} />
        </Field>
        <button type="submit" className="btn-primary w-full" disabled={isSubmitting}>
          {isSubmitting ? t('common.saving') : t('auth.login')}
        </button>
        <button
          type="button"
          onClick={() => {
            setForgotMode('request')
            setShowForgot(true)
          }}
          className="w-full text-center text-sm text-brand-800 mt-3"
        >
          {t('auth.forgotPassword')}
        </button>
      </form>

      <Modal
        open={showForgot}
        title={forgotMode === 'request' ? t('auth.forgotPassword') : t('auth.resetConfirm')}
        onClose={() => setShowForgot(false)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setShowForgot(false)}>
              {t('common.cancel')}
            </button>
            {forgotMode === 'request' ? (
              <button type="button" className="btn-primary" onClick={() => void submitResetRequest()}>
                {t('common.confirm')}
              </button>
            ) : (
              <button
                type="button"
                className="btn-primary"
                disabled={!resetTokenInput.trim() || newPasswordInput.length < 8}
                onClick={() => void submitResetConfirm()}
              >
                {t('common.save')}
              </button>
            )}
          </>
        }
      >
        {forgotMode === 'request' ? (
          <div>
            <Field label={t('common.phone')}>
              <input
                className="input"
                placeholder="9876543210"
                inputMode="tel"
                value={forgotPhone}
                onChange={(e) => setForgotPhone(e.target.value)}
              />
            </Field>
            <button
              type="button"
              className="text-xs text-brand-800 underline mt-1"
              onClick={() => setForgotMode('confirm')}
            >
              {t('auth.enterResetToken')}
            </button>
          </div>
        ) : (
          <div>
            <Field label={t('auth.resetTokenLabel')}>
              <input
                className="input font-mono text-xs"
                value={resetTokenInput}
                onChange={(e) => setResetTokenInput(e.target.value)}
              />
            </Field>
            <Field label={t('auth.newPassword')}>
              <input
                className="input"
                type="password"
                minLength={8}
                value={newPasswordInput}
                onChange={(e) => setNewPasswordInput(e.target.value)}
              />
            </Field>
          </div>
        )}
      </Modal>

      <p className="text-sm text-slate-600 mt-4 text-center">
        {t('auth.noAccount')}{' '}
        <Link to="/signup" className="text-brand-800 font-medium">
          {t('auth.signup')}
        </Link>
      </p>
    </div>
  )
}
