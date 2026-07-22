import { describe, expect, it } from 'vitest'
import { getVerticalScrollDirection, getVerticalVisibilityDirection } from '../vertical-visibility'

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

  it('points toward the center of an item that is taller than the viewport', () => {
    expect(getVerticalVisibilityDirection(
      { top: 80, bottom: 760 },
      viewport,
      8
    )).toBe('down')

    expect(getVerticalVisibilityDirection(
      { top: 40, bottom: 720 },
      viewport,
      8
    )).toBe('up')
  })

  it('keeps controls hidden when an oversized item is centered', () => {
    expect(getVerticalVisibilityDirection(
      { top: 60, bottom: 740 },
      viewport,
      8
    )).toBeNull()
  })
})

describe('vertical-scroll-direction', () => {
  it('points toward the target scroll offset', () => {
    expect(getVerticalScrollDirection(500, 300)).toBe('up')
    expect(getVerticalScrollDirection(500, 700)).toBe('down')
  })

  it('returns no direction when already at the target', () => {
    expect(getVerticalScrollDirection(500, 500)).toBeNull()
    expect(getVerticalScrollDirection(500, 500.5)).toBeNull()
  })
})
