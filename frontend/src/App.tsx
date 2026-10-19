import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Loader } from './components/ui'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { PublicShell } from './components/PublicShell'
import { Landing } from './pages/public/Landing'
import { Pricing } from './pages/public/Pricing'
import { Login } from './pages/public/Login'
import { Signup } from './pages/public/Signup'
import { PayPage } from './pages/public/PayPage'
import { Dashboard } from './pages/app/Dashboard'
import { Members } from './pages/app/Members'
import { MemberDetail } from './pages/app/MemberDetail'
import { Plans } from './pages/app/Plans'
import { Billing } from './pages/app/Billing'
import { Checkins } from './pages/app/Checkins'
import { Messaging } from './pages/app/Messaging'
import { Leads } from './pages/app/Leads'
import { Reports } from './pages/app/Reports'
import { ImportCsv } from './pages/app/ImportCsv'
import { Settings } from './pages/app/Settings'
import { More } from './pages/app/More'
import { AdminGyms } from './pages/admin/AdminGyms'

function Protected({ children, role }: { children: React.ReactNode; role?: 'SUPER_ADMIN' | 'GYM_OWNER' }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  const { t } = useTranslation()

  if (loading) return <Loader label={t('common.loading')} />
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  if (role && user.role !== role) {
    return <Navigate to={user.role === 'SUPER_ADMIN' ? '/admin' : '/app'} replace />
  }
  return <>{children}</>
}

function OwnerRoute({ children }: { children: React.ReactNode }) {
  return (
    <Protected role="GYM_OWNER">
      <AppShell>{children}</AppShell>
    </Protected>
  )
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <PublicShell>
            <Landing />
          </PublicShell>
        }
      />
      <Route
        path="/pricing"
        element={
          <PublicShell>
            <Pricing />
          </PublicShell>
        }
      />
      <Route
        path="/login"
        element={
          <PublicShell>
            <Login />
          </PublicShell>
        }
      />
      <Route
        path="/signup"
        element={
          <PublicShell>
            <Signup />
          </PublicShell>
        }
      />
      <Route path="/pay/:token" element={<PayPage />} />

      <Route
        path="/admin"
        element={
          <Protected role="SUPER_ADMIN">
            <AppShell>
              <AdminGyms />
            </AppShell>
          </Protected>
        }
      />

      <Route path="/app" element={<OwnerRoute><Dashboard /></OwnerRoute>} />
      <Route path="/app/members" element={<OwnerRoute><Members /></OwnerRoute>} />
      <Route path="/app/members/:id" element={<OwnerRoute><MemberDetail /></OwnerRoute>} />
      <Route path="/app/plans" element={<OwnerRoute><Plans /></OwnerRoute>} />
      <Route path="/app/billing" element={<OwnerRoute><Billing /></OwnerRoute>} />
      <Route path="/app/checkins" element={<OwnerRoute><Checkins /></OwnerRoute>} />
      <Route path="/app/messages" element={<OwnerRoute><Messaging /></OwnerRoute>} />
      <Route path="/app/leads" element={<OwnerRoute><Leads /></OwnerRoute>} />
      <Route path="/app/reports" element={<OwnerRoute><Reports /></OwnerRoute>} />
      <Route path="/app/import" element={<OwnerRoute><ImportCsv /></OwnerRoute>} />
      <Route path="/app/settings" element={<OwnerRoute><Settings /></OwnerRoute>} />
      <Route path="/app/more" element={<OwnerRoute><More /></OwnerRoute>} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
