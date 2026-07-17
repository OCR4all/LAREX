import { describe, expect, it } from 'vitest'
import { getVerticalVisibilityDirection } from '../vertical-visibility'

describe('vertical-visibility', () => {
  const viewport = { top: 100, bottom: 700 }

  it('keeps controls hidden when the item is within the viewport', () => {
    expect(getVerticalVisibilityDirection(
      { top: 180, bottom: 620 },
      viewport,
      8
    )).toBeNull()
  })

  it('points toward items above the viewport', () => {
    expect(getVerticalVisibilityDirection(
      { top: 80, bottom: 520 },
      viewport,
      8
    )).toBe('up')
  })

  it('points toward items below the viewport', () => {
    expect(getVerticalVisibilityDirection(
      { top: 300, bottom: 720 },
      viewport,
      8
    )).toBe('down')
  })
})
