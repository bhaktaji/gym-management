import { describe, expect, it } from 'vitest'
import { formatMoney, formatDate, toLocalDateInput } from '../lib/format'

describe('formatMoney', () => {
  it('formats paise with Indian grouping', () => {
    expect(formatMoney(0)).toBe('₹0.00')
    expect(formatMoney(9900)).toBe('₹99.00')
    expect(formatMoney(12345600)).toBe('₹1,23,456.00')
    expect(formatMoney(10000000)).toBe('₹1,00,000.00')
    expect(formatMoney(12345678900)).toBe('₹12,34,56,789.00')
  })

  it('formats negatives', () => {
    expect(formatMoney(-123456)).toBe('-₹1,234.56')
  })

  it('handles missing values', () => {
    expect(formatMoney(null)).toBe('-')
    expect(formatMoney(undefined)).toBe('-')
  })

  it('can omit the rupee symbol', () => {
    expect(formatMoney(118000, false)).toBe('1,180.00')
  })
})

describe('formatDate', () => {
  it('formats an ISO date', () => {
    expect(formatDate('2026-10-19')).toContain('2026')
  })

  it('passes through unusable values', () => {
    expect(formatDate(null)).toBe('-')
    expect(formatDate('not-a-date')).toBe('not-a-date')
  })
})

describe('toLocalDateInput', () => {
  it('produces a yyyy-mm-dd string', () => {
    expect(toLocalDateInput(new Date(2026, 9, 19))).toBe('2026-10-19')
  })
})
