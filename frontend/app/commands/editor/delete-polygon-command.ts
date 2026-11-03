import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Glyph, Region, TextLine, TextRegion, ReadingOrder, Relation, Word } from '@/models/editor'
import { invalidateMultiplePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  collectRenderablePolygonsFromPcGts,
  findGlyphRecursive,
  findRegionRecursive,
  findTextLineRecursive,
  findWordRecursive,
  rebuildSpatialIndexFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { cloneRelations, removeRelationsReferencingIds } from '@/utils/editor/relations'
import { cloneReadingOrder, removeIdsFromReadingOrder } from './reading-order-utils'

export interface DeletePolygonCommandData {
  polygonId: string // Region, text line, word or glyph ID to delete
}

export class DeletePolygonCommand implements Command {
  private polygonId: string

  private deletedRegion: { region: Region, parent: Region | null, index: number } | null = null
  private deletedTextLine: { textLine: TextLine, parentTextRegion: TextRegion, index: number } | null = null
  private deletedWord: { word: Word, parentTextLine: TextLine, index: number } | null = null
  private deletedGlyph: { glyph: Glyph, parentWord: Word, index: number } | null = null
  private previousRelations: Relation[] | undefined
  private previousReadingOrder?: ReadingOrder
  private relationsModified = false
  private readingOrderModified = false

  constructor(data: DeletePolygonCommandData) {
    this.polygonId = data.polygonId
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const polygonIdsToInvalidate = collectDescendantPolygonIds(pcGts, this.polygonId)
    if (ctx?.canvasId && polygonIdsToInvalidate.length) {
      invalidateMultiplePolygonGeometry(ctx.canvasId, polygonIdsToInvalidate)
    }

    const regionHit = findRegionRecursive(pcGts.page.regions, this.polygonId)
    if (regionHit) {
      const siblings = regionHit.parent ? (regionHit.parent.regions ?? []) : pcGts.page.regions
      this.deletedRegion = { region: regionHit.region, parent: regionHit.parent, index: regionHit.index }
      siblings.splice(regionHit.index, 1)
    } else if (!this.deleteTextElement(pcGts)) {
      return
    }

    const idsToRemove = new Set(polygonIdsToInvalidate)
    this.readingOrderModified = false
    if (pcGts.page.readingOrder?.root?.elements) {
      this.previousReadingOrder = cloneReadingOrder(pcGts.page.readingOrder)
      this.readingOrderModified = removeIdsFromReadingOrder(pcGts.page.readingOrder.root.elements, idsToRemove)
      if (!this.readingOrderModified) {
        this.previousReadingOrder = undefined
      }
    }

    this.relationsModified = false
    if (this.deletedRegion) {
      this.previousRelations = cloneRelations(pcGts.page.relations)
      const nextRelations = removeRelationsReferencingIds(pcGts.page.relations, idsToRemove)
      this.relationsModified = JSON.stringify(this.previousRelations ?? []) !== JSON.stringify(nextRelations ?? [])
      pcGts.page.relations = nextRelations
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()

    if (this.readingOrderModified) {
      const editorUiStore = useEditorUiStore()
      editorUiStore.bumpReadingOrderVersion()
    }
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    if (this.deletedRegion) {
      const { region, parent, index } = this.deletedRegion
      const siblings = parent ? (parent.regions ?? []) : pcGts.page.regions
      const safeIndex = Math.min(Math.max(index, 0), siblings.length)
      siblings.splice(safeIndex, 0, region)
    } else if (this.deletedTextLine) {
      const { textLine, parentTextRegion, index } = this.deletedTextLine
      parentTextRegion.textLines = parentTextRegion.textLines ?? []
      const safeIndex = Math.min(Math.max(index, 0), parentTextRegion.textLines.length)
      parentTextRegion.textLines.splice(safeIndex, 0, textLine)
    } else if (this.deletedWord) {
      const { word, parentTextLine, index } = this.deletedWord
      parentTextLine.words = parentTextLine.words ?? []
      const safeIndex = Math.min(Math.max(index, 0), parentTextLine.words.length)
      parentTextLine.words.splice(safeIndex, 0, word)
    } else if (this.deletedGlyph) {
      const { glyph, parentWord, index } = this.deletedGlyph
      parentWord.glyphs = parentWord.glyphs ?? []
      const safeIndex = Math.min(Math.max(index, 0), parentWord.glyphs.length)
      parentWord.glyphs.splice(safeIndex, 0, glyph)
    } else {
      return
    }

    if (this.relationsModified) {
      pcGts.page.relations = cloneRelations(this.previousRelations)
    }
    if (this.readingOrderModified) {
      pcGts.page.readingOrder = cloneReadingOrder(this.previousReadingOrder)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    if (this.readingOrderModified) {
      useEditorUiStore().bumpReadingOrderVersion()
    }
  }

  getDescription(): string {
    return `Delete polygon`
  }

  private deleteTextElement(pcGts: PcGts): boolean {
    const textLineHit = findTextLineRecursive(pcGts.page.regions, this.polygonId)
    if (textLineHit) {
      this.deletedTextLine = {
        textLine: textLineHit.textLine,
        parentTextRegion: textLineHit.parentTextRegion,
        index: textLineHit.index
      }
      textLineHit.parentTextRegion.textLines?.splice(textLineHit.index, 1)
      return true
    }

    const wordHit = findWordRecursive(pcGts.page.regions, this.polygonId)
    if (wordHit) {
      this.deletedWord = { word: wordHit.word, parentTextLine: wordHit.parentTextLine, index: wordHit.index }
      wordHit.parentTextLine.words?.splice(wordHit.index, 1)
      return true
    }

    const glyphHit = findGlyphRecursive(pcGts.page.regions, this.polygonId)
    if (glyphHit) {
      this.deletedGlyph = { glyph: glyphHit.glyph, parentWord: glyphHit.parentWord, index: glyphHit.index }
      glyphHit.parentWord.glyphs?.splice(glyphHit.index, 1)
      return true
    }

    return false
  }
}

function collectDescendantPolygonIds(pcGts: PcGts, rootId: string): string[] {
  const polygons = collectRenderablePolygonsFromPcGts(pcGts)
  const childrenByParent = new Map<string, string[]>()
  for (const p of polygons) {
    if (!p.parentId) continue
    const list = childrenByParent.get(p.parentId) ?? []
    list.push(p.id)
    childrenByParent.set(p.parentId, list)
  }

  const out = new Set<string>()
  const queue: string[] = [rootId]
  out.add(rootId)

  while (queue.length) {
    const current = queue.shift()
    if (!current) continue
    const children = childrenByParent.get(current) ?? []
    for (const child of children) {
      if (out.has(child)) continue
      out.add(child)
      queue.push(child)
    }
  }

  return Array.from(out)
}
