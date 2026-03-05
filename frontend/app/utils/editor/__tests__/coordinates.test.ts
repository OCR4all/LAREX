import { describe, expect, it } from 'vitest'
import {
  clipToWorldCoords,
  getWorldCoordsFromEvent,
  worldToClipCoords
} from '../coordinates'

describe('coordinates rotation transforms', () => {
  it('preserves previous behavior for identity rotation', () => {
    const world = { x: -0.25, y: 0.4 }
    const view = { zoom: 1.4, offsetX: 0.2, offsetY: -0.3 }
    const scale = { scaleX: 0.75, scaleY: 0.5, rotationCos: 1, rotationSin: 0 }

    const clip = worldToClipCoords(world, view, scale)

    expect(clip.x).toBeCloseTo(((world.x * view.zoom) + view.offsetX) * scale.scaleX, 8)
    expect(clip.y).toBeCloseTo(((world.y * view.zoom) + view.offsetY) * scale.scaleY, 8)

    const roundTrip = clipToWorldCoords(clip, view, scale)
    expect(roundTrip.x).toBeCloseTo(world.x, 8)
    expect(roundTrip.y).toBeCloseTo(world.y, 8)
  })

  it('maps right-edge click to top world position for +90 PAGE orientation', () => {
    const canvas = {
      clientWidth: 100,
      clientHeight: 100,
      getBoundingClientRect: () => ({ left: 0, top: 0 })
    } as unknown as HTMLCanvasElement

    const event = {
      clientX: 100,
      clientY: 50
    } as MouseEvent

    const world = getWorldCoordsFromEvent(
      event,
      canvas,
      { zoom: 1, offsetX: 0, offsetY: 0 },
      {
        scaleX: 1,
        scaleY: 1,
        rotationCos: Math.cos(-Math.PI / 2),
        rotationSin: Math.sin(-Math.PI / 2)
      }
    )

    expect(world.x).toBeCloseTo(0, 6)
    expect(world.y).toBeCloseTo(1, 6)
  })

  it('round-trips world and clip coordinates under rotation', () => {
    const view = { zoom: 1.7, offsetX: -0.12, offsetY: 0.33 }
    const scale = {
      scaleX: 0.82,
      scaleY: 0.63,
      rotationCos: Math.cos(-37 * Math.PI / 180),
      rotationSin: Math.sin(-37 * Math.PI / 180)
    }

    const points = [
      { x: -0.9, y: 0.9 },
      { x: -0.2, y: 0.1 },
      { x: 0.4, y: -0.3 },
      { x: 0.8, y: -0.7 }
    ]

    for (const point of points) {
      const clip = worldToClipCoords(point, view, scale)
      const roundTrip = clipToWorldCoords(clip, view, scale)
      expect(roundTrip.x).toBeCloseTo(point.x, 7)
      expect(roundTrip.y).toBeCloseTo(point.y, 7)
    }
  })

  it('keeps inverse/forward consistency with non-uniform scales at aspect=1', () => {
    const view = { zoom: 1, offsetX: 0, offsetY: 0 }
    const scale = {
      scaleX: 0.5,
      scaleY: 1,
      rotationCos: Math.cos(-Math.PI / 2),
      rotationSin: Math.sin(-Math.PI / 2),
      rotationAspect: 1
    }

    const clip = worldToClipCoords({ x: 1, y: 0 }, view, scale)
    expect(clip.x).toBeCloseTo(0, 8)
    expect(clip.y).toBeCloseTo(-0.5, 8)

    const roundTrip = clipToWorldCoords(clip, view, scale)
    expect(roundTrip.x).toBeCloseTo(1, 8)
    expect(roundTrip.y).toBeCloseTo(0, 8)
  })

  it('rotates in pixel space for non-square canvases', () => {
    const view = { zoom: 1, offsetX: 0, offsetY: 0 }
    const scale = {
      scaleX: 0.5,
      scaleY: 1,
      rotationCos: Math.cos(-Math.PI / 2),
      rotationSin: Math.sin(-Math.PI / 2),
      rotationAspect: 2
    }

    const clip = worldToClipCoords({ x: 1, y: 0 }, view, scale)
    expect(clip.x).toBeCloseTo(0, 8)
    expect(clip.y).toBeCloseTo(-1, 8)

    const roundTrip = clipToWorldCoords(clip, view, scale)
    expect(roundTrip.x).toBeCloseTo(1, 8)
    expect(roundTrip.y).toBeCloseTo(0, 8)
  })
})
