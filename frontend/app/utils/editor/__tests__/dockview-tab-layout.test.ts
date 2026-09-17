import { describe, expect, it } from 'vitest'
import { getScrollLeftToRevealTab, isDetachedTabHeader } from '../dockview-tab-layout'

describe('isDetachedTabHeader', () => {
  it('only mirrors the parent tab for split groups', () => {
    const parent = { left: 16, top: 16, bottom: 56 }

    expect(isDetachedTabHeader({ left: 16, top: 56, bottom: 96 }, parent)).toBe(false)
    expect(isDetachedTabHeader({ left: 640, top: 56, bottom: 96 }, parent)).toBe(true)
  })

  it('keeps the active tab inside the visible strip', () => {
    expect(getScrollLeftToRevealTab({ scrollLeft: 0, viewportWidth: 300, tabLeft: 360, tabWidth: 100 })).toBe(160)
    expect(getScrollLeftToRevealTab({ scrollLeft: 200, viewportWidth: 300, tabLeft: 80, tabWidth: 100 })).toBe(80)
  })
})
