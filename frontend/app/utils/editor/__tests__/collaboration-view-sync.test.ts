import { describe, expect, it } from 'vitest'
import {
  isCollaborationCanvasViewMode,
  normalizeCollaborationViewport,
  resolveCollaborationSelection,
  sameCollaborationViewport
} from '../collaboration-view-sync'

describe('collaboration view synchronization', () => {
  it('accepts supported hierarchy modes only', () => {
    expect(isCollaborationCanvasViewMode('default')).toBe(true)
    expect(isCollaborationCanvasViewMode('textline')).toBe(true)
    expect(isCollaborationCanvasViewMode('baseline')).toBe(true)
    expect(isCollaborationCanvasViewMode('text')).toBe(false)
    expect(isCollaborationCanvasViewMode(null)).toBe(false)
  })

  it('normalizes finite in-range viewports and rejects malformed input', () => {
    expect(normalizeCollaborationViewport({
      zoom: 2,
      offsetX: -0.25,
      offsetY: 0.5
    })).toEqual({
      zoom: 2,
      offsetX: -0.25,
      offsetY: 0.5
    })
    expect(normalizeCollaborationViewport({ zoom: Number.NaN, offsetX: 0, offsetY: 0 })).toBeNull()
    expect(normalizeCollaborationViewport({ zoom: '2', offsetX: 0, offsetY: 0 })).toBeNull()
    expect(normalizeCollaborationViewport({ zoom: 0, offsetX: 0, offsetY: 0 })).toBeNull()
    expect(normalizeCollaborationViewport({ zoom: 1, offsetX: Number.POSITIVE_INFINITY, offsetY: 0 })).toBeNull()
    expect(normalizeCollaborationViewport(null)).toBeNull()
  })

  it('compares complete viewport state', () => {
    const viewport = { zoom: 1.5, offsetX: 0.2, offsetY: -0.4 }
    expect(sameCollaborationViewport(viewport, { ...viewport })).toBe(true)
    expect(sameCollaborationViewport(viewport, { ...viewport, offsetY: 0 })).toBe(false)
  })

  it('resolves root, valid, and deleted selections by stable ID', () => {
    const regions = new Set(['region-1'])
    const baselines = new Set(['baseline:line-1'])

    expect(resolveCollaborationSelection({
      selectionId: null,
      selectionKind: null
    }, regions, baselines)).toEqual({ status: 'root' })
    expect(resolveCollaborationSelection({
      selectionId: 'region-1',
      selectionKind: 'region'
    }, regions, baselines)).toEqual({
      status: 'valid',
      kind: 'region',
      id: 'region-1'
    })
    expect(resolveCollaborationSelection({
      selectionId: 'baseline:line-1',
      selectionKind: 'baseline'
    }, regions, baselines)).toEqual({
      status: 'valid',
      kind: 'baseline',
      id: 'baseline:line-1'
    })
    expect(resolveCollaborationSelection({
      selectionId: 'deleted-region',
      selectionKind: 'region'
    }, regions, baselines)).toEqual({
      status: 'missing',
      kind: 'region',
      id: 'deleted-region'
    })
    expect(resolveCollaborationSelection({
      selectionId: 'region-1',
      selectionKind: 'unsupported'
    } as never, regions, baselines)).toEqual({ status: 'root' })
  })
})
