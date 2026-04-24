import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Region, TextLine, TextRegion, ReadingOrder, Relation } from '@/models/editor'
import { invalidateMultiplePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  collectRenderablePolygonsFromPcGts,
  findRegionRecursive,
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { cloneRelations, removeRelationsReferencingIds } from '@/utils/editor/relations'
import { cloneReadingOrder, removeIdsFromReadingOrder } from './reading-order-utils'

export interface DeletePolygonCommandData {
  polygonId: string // Region ID to delete
}

export class DeletePolygonCommand implements Command {
  private polygonId: string

  private deletedRegion: { region: Region, parent: Region | null, index: number } | null = null
  private deletedTextLine: { textLine: TextLine, parentTextRegion: TextRegion, index: number } | null = null
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
    } else {
      const textLineHit = findTextLineRecursive(pcGts.page.regions, this.polygonId)
      if (!textLineHit) return
      this.deletedTextLine = {
        textLine: textLineHit.textLine,
        parentTextRegion: textLineHit.parentTextRegion,
        index: textLineHit.index
      }
      textLineHit.parentTextRegion.textLines?.splice(textLineHit.index, 1)
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
