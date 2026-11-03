import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Glyph, Polygon, PolygonType, TextContentVariant, TextLine, Word, isTextRegion } from '@/models/editor'
import type { PcGts, TextRegion } from '@/models/editor'
import {
  collectRenderablePolygonsFromPcGts,
  findGlyphRecursive,
  findWordRecursive
} from '@/utils/editor/pcgts-editor-primitives'
import { ConstrainToParentCommand } from '../constrain-to-parent-command'
import { CreatePolygonCommand } from '../create-polygon-command'
import { CreateTextElementAutoParentCommand } from '../create-text-element-auto-parent-command'
import { CutElementsCommand } from '../cut-elements-command'
import { DeletePolygonCommand } from '../delete-polygon-command'
import { DuplicateElementCommand } from '../duplicate-element-command'
import { MergeElementsCommand } from '../merge-elements-command'
import { MoveElementCommand } from '../move-element-command'
import { ReparentElementCommand } from '../reparent-element-command'
import { UpdatePolygonCommand } from '../update-polygon-command'
import {
  createMockSession,
  createTestContext,
  createTestDocument,
  createTestTextRegion
} from './test-utils'

vi.mock('@/services/editor/visibility-service', () => ({
  visibilityService: {
    clearCache: vi.fn()
  }
}))

vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({
  invalidatePolygonGeometry: vi.fn(),
  invalidateMultiplePolygonGeometry: vi.fn()
}))

vi.mock('@/stores/editor/editor.ui.store', () => ({
  useEditorUiStore: () => ({
    bumpReadingOrderVersion: vi.fn()
  })
}))

function rect(minX: number, minY: number, maxX: number, maxY: number): Polygon {
  return new Polygon([[minX, minY], [maxX, minY], [maxX, maxY], [minX, maxY]])
}

function glyph(id: string, minX: number, maxX: number, text?: string): Glyph {
  return Glyph.fromFields({
    id,
    coords: rect(minX, 210, maxX, 290),
    unicode: text,
    textContentVariants: text ? [new TextContentVariant(text, undefined, undefined, 0)] : undefined
  })
}

function word(id: string, minX: number, maxX: number, glyphs: Glyph[] = [], text?: string): Word {
  return Word.fromFields({
    id,
    coords: rect(minX, 205, maxX, 295),
    glyphs,
    textContentVariants: text ? [new TextContentVariant(text, undefined, undefined, 0)] : undefined
  })
}

/**
 * Page with one region and two lines:
 * - tl1 (x 100-900) with words w1 (150-350, glyphs g1, g2) and w2 (650-850)
 * - tl2 (x 100-900, lower) without words
 */
function createFixture(): { doc: PcGts, region: TextRegion, tl1: TextLine, tl2: TextLine } {
  const tl1 = new TextLine({
    id: 'tl1',
    coords: rect(100, 200, 900, 300),
    words: [
      word('w1', 150, 350, [glyph('g1', 160, 240, 'a'), glyph('g2', 260, 340, 'b')], 'ab'),
      word('w2', 650, 850, [], 'cd')
    ]
  })
  const tl2 = new TextLine({ id: 'tl2', coords: rect(100, 400, 900, 500) })
  const region = createTestTextRegion({
    id: 'r1',
    points: [{ x: 0, y: 0 }, { x: 1000, y: 0 }, { x: 1000, y: 1000 }, { x: 0, y: 1000 }],
    textLines: [tl1, tl2]
  })
  return { doc: createTestDocument({ regions: [region] }), region, tl1, tl2 }
}

function textLineOf(doc: PcGts, id: string): TextLine | undefined {
  const region = doc.page.regions[0]
  return region && isTextRegion(region) ? region.textLines?.find(tl => tl.id === id) : undefined
}

describe('word and glyph editing commands', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('projects words and glyphs as nested polygons', () => {
    const { doc } = createFixture()
    const polygons = collectRenderablePolygonsFromPcGts(doc)

    expect(polygons.find(p => p.id === 'w1')).toMatchObject({ type: PolygonType.WORD, parentId: 'tl1' })
    expect(polygons.find(p => p.id === 'g2')).toMatchObject({ type: PolygonType.GLYPH, parentId: 'w1' })
    expect(polygons.find(p => p.id === 'w1')?.textContentVariants?.[0]?.unicode).toBe('ab')
  })

  it('creates words under a textline and glyphs under a word, with undo', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const createWord = new CreatePolygonCommand({
      points: [{ x: 400, y: 210 }, { x: 600, y: 210 }, { x: 600, y: 290 }],
      type: PolygonType.WORD,
      parentId: 'tl2'
    })
    const { id: wordId } = createWord.execute(ctx)
    expect(textLineOf(getDocument()!, 'tl2')?.words?.map(w => w.id)).toEqual([wordId])

    const createGlyph = new CreatePolygonCommand({
      points: [{ x: 410, y: 220 }, { x: 450, y: 220 }, { x: 450, y: 280 }],
      type: PolygonType.GLYPH,
      parentId: wordId
    })
    const { id: glyphId } = createGlyph.execute(ctx)
    expect(findGlyphRecursive(getDocument()!.page.regions, glyphId)?.parentWord.id).toBe(wordId)

    createGlyph.undo(ctx)
    expect(findGlyphRecursive(getDocument()!.page.regions, glyphId)).toBeNull()
    createWord.undo(ctx)
    expect(textLineOf(getDocument()!, 'tl2')?.words).toEqual([])
  })

  it('deletes a word with its glyphs and restores it at the same index on undo', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new DeletePolygonCommand({ polygonId: 'w1' })
    command.execute(ctx)
    expect(textLineOf(getDocument()!, 'tl1')?.words?.map(w => w.id)).toEqual(['w2'])
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')).toBeNull()

    command.undo(ctx)
    expect(textLineOf(getDocument()!, 'tl1')?.words?.map(w => w.id)).toEqual(['w1', 'w2'])
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')?.parentWord.id).toBe('w1')
  })

  it('updates glyph geometry and reverts it on undo', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const before = [...findGlyphRecursive(doc.page.regions, 'g1')!.glyph.coords.points]

    const command = new UpdatePolygonCommand({
      polygonId: 'g1',
      newPoints: [{ x: 1, y: 2 }, { x: 3, y: 4 }, { x: 5, y: 6 }]
    })
    command.execute(ctx)
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')?.glyph.coords.points).toEqual([[1, 2], [3, 4], [5, 6]])

    command.undo(ctx)
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')?.glyph.coords.points).toEqual(before)
  })

  it('moves a textline together with its words and glyphs', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new MoveElementCommand({
      elementId: 'tl1',
      elementType: 'polygon',
      delta: { x: 10, y: 5 },
      moveWithChildren: true
    })
    command.execute(ctx)
    expect(findWordRecursive(getDocument()!.page.regions, 'w2')?.word.coords.points[0]).toEqual([660, 210])
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')?.glyph.coords.points[0]).toEqual([170, 215])

    command.undo(ctx)
    expect(findWordRecursive(getDocument()!.page.regions, 'w2')?.word.coords.points[0]).toEqual([650, 205])
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')?.glyph.coords.points[0]).toEqual([160, 210])
  })

  it('moves a word without its glyphs unless moving with children', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    new MoveElementCommand({ elementId: 'w1', elementType: 'polygon', delta: { x: 10, y: 0 } }).execute(ctx)

    expect(findWordRecursive(getDocument()!.page.regions, 'w1')?.word.coords.points[0]).toEqual([160, 205])
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')?.glyph.coords.points[0]).toEqual([160, 210])
  })

  it('reparents a word to another textline and back on undo', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new ReparentElementCommand({ elementId: 'w1', elementType: 'word', newParentId: 'tl2' })
    expect(command.execute(ctx)).toBe(true)
    expect(findWordRecursive(getDocument()!.page.regions, 'w1')?.parentTextLine.id).toBe('tl2')

    command.undo(ctx)
    expect(textLineOf(getDocument()!, 'tl1')?.words?.map(w => w.id)).toEqual(['w1', 'w2'])
  })

  it('rejects reparenting a glyph onto a textline', () => {
    const { doc } = createFixture()
    const { session } = createMockSession(doc)

    const command = new ReparentElementCommand({ elementId: 'g1', elementType: 'glyph', newParentId: 'tl2' })
    expect(command.execute(createTestContext(session))).toBe(false)
  })

  it('merges words into one word that keeps all glyphs and joins the text', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new MergeElementsCommand({ elementIds: ['w1', 'w2'], elementType: 'word' })
    const result = command.execute(ctx)

    const words = textLineOf(getDocument()!, 'tl1')?.words ?? []
    expect(words.map(w => w.id)).toEqual([result!.id])
    expect(words[0]?.glyphs?.map(g => g.id)).toEqual(['g1', 'g2'])
    expect(words[0]?.textContentVariants?.[0]?.unicode).toBe('abcd')

    command.undo(ctx)
    expect(textLineOf(getDocument()!, 'tl1')?.words?.map(w => w.id)).toEqual(['w1', 'w2'])
  })

  it('keeps words when merging textlines', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const result = new MergeElementsCommand({ elementIds: ['tl1', 'tl2'], elementType: 'textline' }).execute(ctx)

    expect(textLineOf(getDocument()!, result!.id)?.words?.map(w => w.id)).toEqual(['w1', 'w2'])
  })

  it('duplicates a word as an empty sibling and removes it on undo', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new DuplicateElementCommand({ elementId: 'w1', elementType: 'polygon', parentId: 'tl1' })
    const result = command.execute(ctx)

    const duplicate = findWordRecursive(getDocument()!.page.regions, result!.id)
    expect(duplicate?.parentTextLine.id).toBe('tl1')
    expect(duplicate?.word.glyphs).toBeUndefined()

    command.undo(ctx)
    expect(findWordRecursive(getDocument()!.page.regions, result!.id)).toBeNull()
  })

  it('distributes words to the matching line pieces when a cut line splits a textline', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new CutElementsCommand({
      mode: 'line',
      cutPoints: [{ x: 500, y: 150 }, { x: 500, y: 350 }],
      targetElementIds: ['tl1']
    })
    command.execute(ctx)

    const region = getDocument()!.page.regions[0] as TextRegion
    const original = region.textLines?.find(tl => tl.id === 'tl1')
    const piece = region.textLines?.find(tl => tl.id !== 'tl1' && tl.id !== 'tl2')
    const pieceWordIds = piece?.words?.map(w => w.id) ?? []
    const originalWordIds = original?.words?.map(w => w.id) ?? []

    expect([...originalWordIds, ...pieceWordIds].sort()).toEqual(['w1', 'w2'])
    expect(originalWordIds).toHaveLength(1)
    expect(pieceWordIds).toHaveLength(1)
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')?.parentWord.id).toBe('w1')

    command.undo(ctx)
    expect(textLineOf(getDocument()!, 'tl1')?.words?.map(w => w.id)).toEqual(['w1', 'w2'])
  })

  it('splits a targeted word and assigns its glyphs to the matching word piece', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const result = new CutElementsCommand({
      mode: 'line',
      cutPoints: [{ x: 250, y: 150 }, { x: 250, y: 350 }],
      targetElementIds: ['w1']
    }).execute(ctx)

    expect(result.createdCount).toBe(1)
    const words = textLineOf(getDocument()!, 'tl1')?.words ?? []
    expect(words.map(w => w.id)[0]).toBe('w1')
    expect(words).toHaveLength(3)
    // The new piece is inserted right after the original word.
    expect(words[2]?.id).toBe('w2')
    expect(findGlyphRecursive(getDocument()!.page.regions, 'g1')?.parentWord.id).not.toBe(findGlyphRecursive(getDocument()!.page.regions, 'g2')?.parentWord.id)
  })

  it('constrains a word to its parent textline', () => {
    const { doc } = createFixture()
    findWordRecursive(doc.page.regions, 'w2')!.word.coords = rect(650, 150, 850, 350)
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    new ConstrainToParentCommand(['w2']).execute(ctx)

    const ys = findWordRecursive(getDocument()!.page.regions, 'w2')!.word.coords.points.map(([, y]) => y)
    expect(Math.min(...ys)).toBeCloseTo(200)
    expect(Math.max(...ys)).toBeCloseTo(300)
  })
})

describe('CreateTextElementAutoParentCommand', () => {
  it('assigns a word to the textline it overlaps', () => {
    const { doc } = createFixture()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const result = new CreateTextElementAutoParentCommand({
      points: [{ x: 400, y: 410 }, { x: 600, y: 410 }, { x: 600, y: 490 }, { x: 400, y: 490 }],
      type: PolygonType.WORD
    }).execute(ctx)

    expect(result).toMatchObject({ parentId: 'tl2', createdParent: false })
    expect(findWordRecursive(getDocument()!.page.regions, result.id)?.parentTextLine.id).toBe('tl2')
  })

  it('creates helper word, textline and region for a glyph drawn on an empty page, undone atomically', () => {
    const doc = createTestDocument()
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new CreateTextElementAutoParentCommand({
      points: [{ x: 0.1, y: 0.1 }, { x: 0.2, y: 0.1 }, { x: 0.2, y: 0.2 }, { x: 0.1, y: 0.2 }],
      type: PolygonType.GLYPH
    })
    const result = command.execute(ctx)

    const glyphHit = findGlyphRecursive(getDocument()!.page.regions, result.id)
    expect(result.createdParent).toBe(true)
    expect(glyphHit?.parentWord.id).toBe(result.parentId)
    expect(getDocument()!.page.regions).toHaveLength(1)

    command.undo(ctx)
    expect(getDocument()!.page.regions).toHaveLength(0)
  })
})
