import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { Card, ErrorNote, Loader } from '../../components/ui'

type ScreenTokenData = {
  gymName: string
  gymSlug: string
  strictQrMode: boolean
  token: string
  expiresInSeconds: number
  checkinUrl: string
}

export function QrScreenPage() {
  const { slug } = useParams<{ slug: string }>()

  const screen = useQuery({
    queryKey: ['public-attendance-screen', slug],
    queryFn: () => api<ScreenTokenData>(`/api/v1/public/attendance/${slug}/screen`, { auth: false }),
    enabled: Boolean(slug),
    refetchInterval: 5000,
    retry: false,
  })

  if (screen.isLoading) return <Loader />

  if (screen.isError) {
    const msg = screen.error instanceof ApiError ? screen.error.message : 'Screen unavailable'
    return (
      <div className="min-h-screen bg-slate-900 text-white flex items-center justify-center p-6">
        <Card className="max-w-md w-full text-slate-900">
          <ErrorNote message={msg} />
        </Card>
      </div>
    )
  }

  const data = screen.data!

  return (
    <div className="min-h-screen bg-slate-900 text-white flex items-center justify-center p-6">
      <div className="max-w-lg w-full bg-slate-800 border border-slate-700 rounded-2xl p-8 text-center">
        <p className="text-xs uppercase tracking-widest text-brand-300 font-semibold">
          {data.gymName} · Self Check-In Screen
        </p>
        <h1 className="text-2xl font-bold mt-2">Scan or Enter Rotating Token</h1>
        <div
          data-testid="rotating-qr-token"
          className="my-6 py-6 px-4 rounded-xl bg-slate-950 border border-slate-700 font-mono text-5xl font-bold tracking-[0.25em] text-emerald-400"
        >
          {data.token}
        </div>
        <p className="text-sm text-slate-300">
          Rotates every 60 seconds · Refreshes in <span className="font-semibold text-white">{data.expiresInSeconds}s</span>
        </p>
        <p className="text-xs text-slate-400 mt-4 break-all font-mono">{data.checkinUrl}</p>
      </div>
    </div>
  )
}
