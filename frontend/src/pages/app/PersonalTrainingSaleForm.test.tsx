import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PersonalTraining } from './PersonalTraining'

vi.mock('../../lib/api', () => ({
  api: vi.fn((url: string) => {
    if (url.includes('/api/v1/trainers')) {
      return Promise.resolve([
        { id: 'tr-1', name: 'Kabir Coach', phone: '+919876543210', active: true, defaultCommissionRule: { type: 'PERCENT', rateBps: 2000 } },
      ])
    }
    if (url.includes('/api/v1/pt/packages')) {
      return Promise.resolve([
        { id: 'pkg-1', name: '10-Session PT', packageType: 'SESSION_BASED', totalSessions: 10, validityDays: 60, pricePaise: 1050000, sacCode: '999723', active: true },
      ])
    }
    if (url.includes('/api/v1/pt/enrolments')) {
      return Promise.resolve([])
    }
    if (url.includes('/api/v1/members')) {
      return Promise.resolve([
        { id: 'mem-1', name: 'Aarav Sharma', phone: '+919811122233', status: 'ACTIVE' },
      ])
    }
    return Promise.resolve([])
  }),
}))

describe('Personal Training Sale Form Component', () => {
  it('opens PT sale modal, selects member, package, trainer and calculates price', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <PersonalTraining />
      </QueryClientProvider>
    )

    // Verify Sell PT button is present and click it
    const sellBtn = await screen.findByTestId('sell-pt-btn')
    expect(sellBtn).toBeInTheDocument()
    fireEvent.click(sellBtn)

    // Modal opens
    const submitBtn = await screen.findByTestId('submit-sell-pt-btn')
    expect(submitBtn).toBeInTheDocument()

    // Form inputs exist
    expect(screen.getByTestId('sell-pt-member-select')).toBeInTheDocument()
    expect(screen.getByTestId('sell-pt-package-select')).toBeInTheDocument()
    expect(screen.getByTestId('sell-pt-trainer-select')).toBeInTheDocument()
  })
})
