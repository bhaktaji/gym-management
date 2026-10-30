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
      return Promise.resolve([])
    }
    if (url.includes('/api/v1/pt/enrolments')) {
      return Promise.resolve([])
    }
    if (url.includes('/api/v1/commissions/statement')) {
      return Promise.resolve({
        trainerId: 'tr-1',
        trainerName: 'Kabir Coach',
        trainerPhone: '+919876543210',
        month: '2026-10',
        accruedPaise: 120000,
        reversalsPaise: 0,
        adjustmentsPaise: 50000,
        paidPaise: 30000,
        balanceDuePaise: 140000,
        netEarnedPaise: 170000,
        whatsappShareText: 'Coach Kabir Commission',
        whatsappShareUrl: 'https://wa.me',
        entries: [
          {
            id: 'entry-1',
            entryType: 'ACCRUAL',
            amountPaise: 120000,
            reason: 'PT payment accrual (IG-2627-0001)',
            createdAt: '2026-10-30T10:00:00Z',
          },
        ],
      })
    }
    return Promise.resolve([])
  }),
}))

describe('Trainer Commission Statement Component', () => {
  it('renders commission statement tab with accrued, adjustments, and balance due cards', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <PersonalTraining />
      </QueryClientProvider>
    )

    // Switch to Statements tab
    const stmtTab = await screen.findByTestId('pt-tab-statements')
    fireEvent.click(stmtTab)

    // Select trainer
    const trainerSelect = await screen.findByTestId('stmt-trainer-select')
    fireEvent.change(trainerSelect, { target: { value: 'tr-1' } })

    // Verify statement figures rendered
    expect(await screen.findByTestId('stmt-accrued')).toHaveTextContent('₹1,200.00')
    expect(screen.getByTestId('stmt-adjustments')).toHaveTextContent('₹500.00')
    expect(screen.getByTestId('stmt-paid')).toHaveTextContent('₹300.00')
    expect(screen.getByTestId('stmt-balance-due')).toHaveTextContent('₹1,400.00')
  })
})
