import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Loader } from './components/ui'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { PublicShell } from './components/PublicShell'

const Landing = lazy(() => import('./pages/public/Landing').then((m) => ({ default: m.Landing })))
const Pricing = lazy(() => import('./pages/public/Pricing').then((m) => ({ default: m.Pricing })))
const Login = lazy(() => import('./pages/public/Login').then((m) => ({ default: m.Login })))
const Signup = lazy(() => import('./pages/public/Signup').then((m) => ({ default: m.Signup })))
const PayPage = lazy(() => import('./pages/public/PayPage').then((m) => ({ default: m.PayPage })))
const SelfCheckinPage = lazy(() => import('./pages/public/SelfCheckinPage').then((m) => ({ default: m.SelfCheckinPage })))
const QrScreenPage = lazy(() => import('./pages/public/QrScreenPage').then((m) => ({ default: m.QrScreenPage })))

const AdminGyms = lazy(() => import('./pages/admin/AdminGyms').then((m) => ({ default: m.AdminGyms })))

const Dashboard = lazy(() => import('./pages/app/Dashboard').then((m) => ({ default: m.Dashboard })))
const Members = lazy(() => import('./pages/app/Members').then((m) => ({ default: m.Members })))
const MemberDetail = lazy(() => import('./pages/app/MemberDetail').then((m) => ({ default: m.MemberDetail })))
const Plans = lazy(() => import('./pages/app/Plans').then((m) => ({ default: m.Plans })))
const Billing = lazy(() => import('./pages/app/Billing').then((m) => ({ default: m.Billing })))
const PersonalTraining = lazy(() => import('./pages/app/PersonalTraining').then((m) => ({ default: m.PersonalTraining })))
const Checkins = lazy(() => import('./pages/app/Checkins').then((m) => ({ default: m.Checkins })))
const Messaging = lazy(() => import('./pages/app/Messaging').then((m) => ({ default: m.Messaging })))
const Leads = lazy(() => import('./pages/app/Leads').then((m) => ({ default: m.Leads })))
const Reports = lazy(() => import('./pages/app/Reports').then((m) => ({ default: m.Reports })))
const ImportCsv = lazy(() => import('./pages/app/ImportCsv').then((m) => ({ default: m.ImportCsv })))
const Settings = lazy(() => import('./pages/app/Settings').then((m) => ({ default: m.Settings })))
const More = lazy(() => import('./pages/app/More').then((m) => ({ default: m.More })))

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
  const { t } = useTranslation()
  const pageFallback = <div className="p-8"><Loader label={t('common.loading')} /></div>

  return (
    <Suspense fallback={pageFallback}>
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
        <Route path="/g/:slug/in" element={<SelfCheckinPage />} />
        <Route path="/g/:slug/screen" element={<QrScreenPage />} />

        <Route
          path="/admin"
          element={
            <Protected role="SUPER_ADMIN">
              <AdminGyms />
            </Protected>
          }
        />

        <Route path="/app" element={<OwnerRoute><Dashboard /></OwnerRoute>} />
        <Route path="/app/members" element={<OwnerRoute><Members /></OwnerRoute>} />
        <Route path="/app/members/:id" element={<OwnerRoute><MemberDetail /></OwnerRoute>} />
        <Route path="/app/plans" element={<OwnerRoute><Plans /></OwnerRoute>} />
        <Route path="/app/pt" element={<OwnerRoute><PersonalTraining /></OwnerRoute>} />
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
    </Suspense>
  )
}

