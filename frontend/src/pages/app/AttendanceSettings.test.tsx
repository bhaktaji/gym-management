import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { Settings } from './Settings'

vi.mock('../../lib/api', () => ({
  api: vi.fn((url: string) => {
    if (url === '/api/v1/tenant/gym') {
      return Promise.resolve({
        id: 'gym-1',
        name: 'Iron Gym',
        phone: '+919876543210',
        email: 'iron@gym.in',
        address: '123 MG Road',
        city: 'Indore',
        state: 'Madhya Pradesh',
        gstRegistered: true,
        gstin: '23AAACI5678G1Z2',
        invoicePrefix: 'IG',
        slug: 'iron-gym',
        settings: {
          attendanceEnabled: true,
          strictQrMode: false,
          selfCheckoutEnabled: true,
          autoCloseDurationMinutes: 120,
          closingTimeLocal: '22:00',
          geofenceLat: 22.7196,
          geofenceLng: 75.8577,
          geofenceRadiusMeters: 150,
          lateRenewalPolicy: 'FROM_TODAY',
          commissionTrigger: 'ON_PAYMENT',
          membershipReferralCommissionEnabled: false,
          ptSacCode: '999723',
        },
      })
    }
    if (url.includes('/api/v1/tenant/plan-usage')) {
      return Promise.resolve({
        pricePaise: 99900,
        subscriptionStatus: 'ACTIVE',
        subscriptionValidUntil: '2027-10-30',
        usage: { members: 5, plans: 3, trainers: 2 },
      })
    }
    return Promise.resolve({})
  }),
}))

describe('Attendance & QR Settings Component', () => {
  it('renders attendance settings, QR poster button and toggles', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Settings />
        </BrowserRouter>
      </QueryClientProvider>
    )

    // Verify toggle for attendance enabled and strict QR mode
    const attendanceCheckbox = await screen.findByTestId('attendance-enabled-checkbox')
    expect(attendanceCheckbox).toBeInTheDocument()

    const strictCheckbox = screen.getByTestId('strict-qr-checkbox')
    expect(strictCheckbox).not.toBeChecked()

    fireEvent.click(strictCheckbox)
    expect(strictCheckbox).toBeChecked()

    // Verify Download A4 Poster button is available
    expect(screen.getByTestId('download-qr-poster-btn')).toBeInTheDocument()
  })
})
