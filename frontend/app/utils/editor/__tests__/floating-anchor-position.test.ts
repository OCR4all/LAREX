import { describe, expect, it } from 'vitest'
import {
  clampFloatingPosition,
  computeFloatingDefaultPosition,
  resolveFloatingControlPosition,
  toFloatingControlOffset
} from '../floating-anchor-position'

describe('floating-anchor-position', () => {
  it('anchors sidebars beside the active canvas and centers them vertically', () => {
    const anchorRect = {
      left: 420,
      top: 80,
      right: 780,
      bottom: 980,
      width: 360,
      height: 900
    }

    expect(computeFloatingDefaultPosition({
      placement: 'left-sidebar',
      anchorRect,
      controlSize: { width: 48, height: 240 },
      viewport: { width: 1600, height: 1200 },
      gap: 16
    })).toEqual({ x: 356, y: 480 })

    expect(computeFloatingDefaultPosition({
      placement: 'right-sidebar',
      anchorRect,
      controlSize: { width: 48, height: 320 },
      viewport: { width: 1600, height: 1200 },
      gap: 16
    })).toEqual({ x: 796, y: 440 })
  })

  it('anchors the toolbar below the active canvas bounds', () => {
    const anchorRect = {
      left: 500,
      top: 100,
      right: 900,
      bottom: 860,
      width: 400,
      height: 760
    }

    expect(computeFloatingDefaultPosition({
      placement: 'toolbar',
      anchorRect,
      controlSize: { width: 360, height: 48 },
      viewport: { width: 1600, height: 1200 },
      gap: 40
    })).toEqual({ x: 520, y: 900 })
  })

  it('preserves a drag offset when the anchor moves', () => {
    const initialDefault = computeFloatingDefaultPosition({
      placement: 'right-sidebar',
      anchorRect: {
        left: 420,
        top: 80,
        right: 780,
        bottom: 980,
        width: 360,
        height: 900
      },
      controlSize: { width: 48, height: 320 },
      viewport: { width: 1600, height: 1200 },
      gap: 16
    })

    const draggedPosition = { x: initialDefault.x + 42, y: initialDefault.y + 96 }
    const offset = toFloatingControlOffset(draggedPosition, initialDefault)

    const movedDefault = computeFloatingDefaultPosition({
      placement: 'right-sidebar',
      anchorRect: {
        left: 520,
        top: 120,
        right: 880,
        bottom: 1020,
        width: 360,
        height: 900
      },
      controlSize: { width: 48, height: 320 },
      viewport: { width: 1800, height: 1400 },
      gap: 16
    })

    expect(resolveFloatingControlPosition({
      defaultPosition: movedDefault,
      controlSize: { width: 48, height: 320 },
      viewport: { width: 1800, height: 1400 },
      offset
    })).toEqual({
      x: movedDefault.x + 42,
      y: movedDefault.y + 96
    })
  })

  it('clamps resolved positions to the viewport edges', () => {
    expect(resolveFloatingControlPosition({
      defaultPosition: { x: 1180, y: 860 },
      controlSize: { width: 360, height: 48 },
      viewport: { width: 1200, height: 900 },
      offset: { dx: 40, dy: 32 }
    })).toEqual({ x: 832, y: 844 })
  })

  it('supports asymmetric viewport margins for initial placement', () => {
    expect(clampFloatingPosition({
      position: { x: -80, y: 860 },
      controlSize: { width: 48, height: 240 },
      viewport: { width: 1200, height: 900 },
      margin: { left: 24, right: 8, bottom: 8, top: 8 }
    })).toEqual({ x: 24, y: 652 })

    expect(clampFloatingPosition({
      position: { x: 1180, y: 860 },
      controlSize: { width: 360, height: 48 },
      viewport: { width: 1200, height: 900 },
      margin: { left: 8, right: 8, bottom: 56, top: 8 }
    })).toEqual({ x: 832, y: 796 })
  })

  it('falls back to viewport defaults without an anchor', () => {
    expect(computeFloatingDefaultPosition({
      placement: 'left-sidebar',
      anchorRect: null,
      controlSize: { width: 48, height: 240 },
      viewport: { width: 1440, height: 900 },
      gap: 24
    })).toEqual({ x: 24, y: 330 })

    expect(computeFloatingDefaultPosition({
      placement: 'right-sidebar',
      anchorRect: null,
      controlSize: { width: 48, height: 320 },
      viewport: { width: 1440, height: 900 },
      gap: 24
    })).toEqual({ x: 1368, y: 290 })

    expect(computeFloatingDefaultPosition({
      placement: 'toolbar',
      anchorRect: null,
      controlSize: { width: 360, height: 48 },
      viewport: { width: 1440, height: 900 },
      gap: 40
    })).toEqual({ x: 540, y: 812 })
  })
})
