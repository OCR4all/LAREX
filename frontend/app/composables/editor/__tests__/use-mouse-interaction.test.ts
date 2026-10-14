import { afterEach, describe, expect, it, vi } from 'vitest'
import { useMouseInteraction } from '../use-mouse-interaction'
import { getWorldCoordsFromEvent } from '@/utils/editor/coordinates'
import { ZOOM } from '@/utils/editor/editor-constants'

const canvas = {
  clientWidth: 800,
  clientHeight: 600,
  getBoundingClientRect: () => ({ left: 10, top: 20 })
} as HTMLCanvasElement
const scale = { scaleX: 1, scaleY: 1 }

function wheel(deltaY: number, overrides: Partial<WheelEvent> = {}): WheelEvent {
  return {
    clientX: 610,
    clientY: 170,
    deltaY,
    deltaMode: 0,
    preventDefault: vi.fn(),
    ...overrides
  } as WheelEvent
}

function mouse(clientX: number, clientY: number): MouseEvent {
  return { clientX, clientY } as MouseEvent
}

const interactions: ReturnType<typeof useMouseInteraction>[] = []
function createInteraction() {
  const interaction = useMouseInteraction()
  interactions.push(interaction)
  return interaction
}

afterEach(() => {
  interactions.splice(0).forEach(interaction => interaction.cleanup())
  vi.useRealTimers()
})

describe('continuous canvas navigation', () => {
  it('responds proportionally to small wheel movements rather than taking fixed zoom steps', () => {
    const fine = createInteraction()
    const coarse = createInteraction()
    fine.handleWheel(wheel(-1), canvas, scale)
    coarse.handleWheel(wheel(-100), canvas, scale)

    expect(fine.view.zoom).toBeGreaterThan(1)
    expect(fine.view.zoom).toBeLessThan(1.01)
    expect(coarse.view.zoom).toBeGreaterThan(fine.view.zoom)
  })

  it('does not zoom for a horizontal-only wheel event', () => {
    const interaction = createInteraction()
    interaction.handleWheel(wheel(0, { deltaX: 20 }), canvas, scale)
    expect(interaction.view).toEqual({ zoom: 1, offsetX: 0, offsetY: 0 })
  })

  it('gives the same result for the same gesture split across more events', () => {
    const single = createInteraction()
    const multiple = createInteraction()
    single.handleWheel(wheel(-100), canvas, scale)
    for (let i = 0; i < 100; i++) multiple.handleWheel(wheel(-1), canvas, scale)

    expect(multiple.view.zoom).toBeCloseTo(single.view.zoom, 12)
    expect(multiple.view.offsetX).toBeCloseTo(single.view.offsetX, 12)
    expect(multiple.view.offsetY).toBeCloseTo(single.view.offsetY, 12)
  })

  it('returns to the same view after equal and opposite wheel movements', () => {
    const interaction = createInteraction()
    interaction.handleWheel(wheel(-100), canvas, scale)
    interaction.handleWheel(wheel(100), canvas, scale)
    expect(interaction.view.zoom).toBeCloseTo(1, 12)
    expect(interaction.view.offsetX).toBeCloseTo(0, 12)
    expect(interaction.view.offsetY).toBeCloseTo(0, 12)
  })

  it('includes the initial pointer movement when a drag becomes a pan', () => {
    const interaction = createInteraction()
    interaction.handleMouseDown(mouse(200, 200))
    interaction.startPanning(mouse(212, 206))
    interaction.updatePanning(mouse(212, 206), canvas, scale)
    expect(interaction.view.offsetX).toBeCloseTo(12 / 800 * 2)
    expect(interaction.view.offsetY).toBeCloseTo(-6 / 600 * 2)
  })

  it.each([
    scale,
    { scaleX: 0.75, scaleY: 1, rotationCos: 0, rotationSin: 1, rotationAspect: 4 / 3 }
  ])('keeps the image point under the cursor anchored, including rotated views', (aspect) => {
    const interaction = createInteraction()
    interaction.setView({ zoom: 2, offsetX: 0.2, offsetY: -0.3 })
    const event = wheel(-35)
    const before = getWorldCoordsFromEvent(event, canvas, interaction.view, aspect)
    interaction.handleWheel(event, canvas, aspect)
    const after = getWorldCoordsFromEvent(event, canvas, interaction.view, aspect)
    expect(after.x).toBeCloseTo(before.x, 12)
    expect(after.y).toBeCloseTo(before.y, 12)
  })

  it.each([[1, -2, -32], [2, -0.5, -300]])('normalizes delta mode %i', (deltaMode, deltaY, pixels) => {
    const normalized = createInteraction()
    const pixel = createInteraction()
    normalized.handleWheel(wheel(deltaY, { deltaMode }), canvas, scale)
    pixel.handleWheel(wheel(pixels), canvas, scale)
    expect(normalized.view).toEqual(pixel.view)
  })

  it('supports the smaller wheel deltas emitted by trackpad pinch gestures', () => {
    const pinch = createInteraction()
    const scroll = createInteraction()
    pinch.handleWheel(wheel(-10, { ctrlKey: true }), canvas, scale)
    scroll.handleWheel(wheel(-100), canvas, scale)
    expect(pinch.view).toEqual(scroll.view)
  })

  it('respects zoom limits without moving the image further at the limit', () => {
    const interaction = createInteraction()
    for (const [delta, limit] of [[-100000, ZOOM.MAX], [100000, ZOOM.MIN]] as const) {
      interaction.handleWheel(wheel(delta), canvas, scale)
      expect(interaction.view.zoom).toBe(limit)
      const atLimit = { ...interaction.view }
      interaction.handleWheel(wheel(delta), canvas, scale)
      expect(interaction.view).toEqual(atLimit)
    }
  })

  it('suppresses remaining wheel momentum after resetting the view', () => {
    vi.useFakeTimers()
    const interaction = createInteraction()
    interaction.handleWheel(wheel(-100), canvas, scale)
    interaction.resetView()
    interaction.handleWheel(wheel(-100), canvas, scale)
    expect(interaction.view).toEqual({ zoom: 1, offsetX: 0, offsetY: 0 })
    vi.advanceTimersByTime(300)
    interaction.handleWheel(wheel(-100), canvas, scale)
    expect(interaction.view.zoom).toBeGreaterThan(1)
  })
})
