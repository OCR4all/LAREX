/**
 * Test utilities for command system integration tests.
 * Provides mock session, document, and spatial index infrastructure.
 */
import type { CommandContext } from '../types'
import type { EditorSession } from '@/session/editor/editor-session'
import type { PcGts } from '@/models/editor'
import { Metadata, PcGts as PcGtsClass } from '@/models/editor/document'
import { Page } from '@/models/editor/page'
import { Polygon } from '@/models/editor/geometry'
import type { TextRegion, Region } from '@/models/editor/region'
import { createSpatialIndex } from '@/services/editor/spatial-index-service'
import { shallowRef } from 'vue'

/**
 * Create a minimal PcGts document for testing
 */
export function createTestDocument(options?: {
  regions?: Region[]
}): PcGts {
  const metadata = new Metadata({
    creator: 'test',
    created: new Date().toISOString(),
    lastChange: new Date().toISOString()
  })

  const page = new Page({
    imageFilename: 'test.jpg',
    imageWidth: 2000,
    imageHeight: 3000,
    regions: options?.regions ?? []
  })

  return new PcGtsClass(metadata, page, 'test-pcgts')
}

/**
 * Create a test TextRegion
 */
export function createTestTextRegion(options: {
  id: string
  points?: Array<{ x: number, y: number }>
  textLines?: TextRegion['textLines']
  type?: string
}): TextRegion {
  const defaultPoints = [
    { x: 100, y: 100 },
    { x: 500, y: 100 },
    { x: 500, y: 200 },
    { x: 100, y: 200 }
  ]

  return {
    id: options.id,
    kind: 'TextRegion',
    type: options.type,
    coords: new Polygon((options.points ?? defaultPoints).map(p => [p.x, p.y])),
    regions: [],
    textLines: options.textLines ?? [],
    textContentVariants: []
  }
}

/**
 * Create a mock EditorSession for testing commands
 */
export function createMockSession(initialDocument?: PcGts): {
  session: EditorSession
  getDocument: () => PcGts | null
  setDocument: (doc: PcGts) => void
} {
  const doc = initialDocument ?? createTestDocument()
  const documentRef = shallowRef<PcGts | null>(doc)
  const spatialIndex = createSpatialIndex()
  const controlsRef = shallowRef<unknown | null>(null)
  const textViewSettingsRef = shallowRef({
    mode: 'textline' as const,
    gtIndex: 0,
    showDiff: false,
    confidenceRange: [0, 1] as [number, number],
    selectedIndices: [] as number[],
    filterUnindexed: false,
    showNonAssignedIndices: false,
    onlyMissingGt: false,
    padding: 10
  })

  const session: EditorSession = {
    canvasId: 'test-canvas',
    document: documentRef,
    spatialIndex,
    controls: controlsRef,
    textViewSettings: textViewSettingsRef,
    destroy: () => {}
  }

  return {
    session,
    getDocument: () => documentRef.value,
    setDocument: (newDoc: PcGts) => {
      documentRef.value = newDoc
    }
  }
}

/**
 * Create a CommandContext for testing
 */
export function createTestContext(session: EditorSession): CommandContext {
  return {
    canvasId: session.canvasId,
    session
  }
}

/**
 * Helper to count total regions (including nested) in a document
 */
export function countTotalRegions(regions: Region[]): number {
  let count = 0
  for (const region of regions) {
    count++
    if (region.regions) {
      count += countTotalRegions(region.regions)
    }
  }
  return count
}

/**
 * Helper to count total text lines in a document
 */
export function countTotalTextLines(regions: Region[]): number {
  let count = 0
  for (const region of regions) {
    if (region.kind === 'TextRegion' && (region as TextRegion).textLines) {
      count += (region as TextRegion).textLines!.length
    }
    if (region.regions) {
      count += countTotalTextLines(region.regions)
    }
  }
  return count
}

/**
 * Helper to find a region by ID in a document
 */
export function findRegionById(regions: Region[], id: string): Region | null {
  for (const region of regions) {
    if (region.id === id) {
      return region
    }
    if (region.regions) {
      const found = findRegionById(region.regions, id)
      if (found) return found
    }
  }
  return null
}
