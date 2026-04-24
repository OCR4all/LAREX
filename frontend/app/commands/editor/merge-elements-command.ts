import type { Command, CommandContext } from './types'
import { PcGts, TextLine, TextContentVariant } from '@/models/editor'
import type { Region, RegionKind, Relation, ReadingOrder } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { isTextRegion, canContainTextLines } from '@/models/editor/region'
import { unionPolygons } from '@/utils/editor/polygon-clipping'
import { visibilityService } from '@/services/editor/visibility-service'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import {
  findRegionRecursive,
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'
import { cloneRelations, removeRelationsReferencingIds } from '@/utils/editor/relations'
import { cloneReadingOrder, replaceIdsInReadingOrder } from './reading-order-utils'

export interface MergeElementsCommandData {
  elementIds: string[]
  elementType: 'region' | 'textline'
  targetKind?: RegionKind
  mergeChildren?: boolean
}

interface UndoData {
  removedRegions: Array<{ region: Region, parentId: string | null, index: number }>
  removedTextLines: Array<{ textLine: TextLine, parentRegionId: string, index: number }>
  createdId: string
  relations?: Relation[]
  readingOrder?: ReadingOrder
}

export class MergeElementsCommand implements Command {
  private data: MergeElementsCommandData
  private undoData: UndoData | null = null

  constructor(data: MergeElementsCommandData) {
    this.data = { mergeChildren: true, ...data }
  }

  execute(ctx?: CommandContext): { id: string } | undefined {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || this.data.elementIds.length < 2) return undefined

    if (this.data.elementType === 'textline') {
      return this.mergeTextLines(pcGts, session)
    }
    return this.mergeRegions(pcGts, session)
  }

  private mergeRegions(pcGts: PcGts, session: any): { id: string } | undefined {
    const regions: Array<{ region: Region, parentId: string | null, index: number }> = []

    for (const id of this.data.elementIds) {
      const hit = findRegionRecursive(pcGts.page.regions, id)
      if (hit) {
        regions.push({ region: hit.region, parentId: hit.parent?.id ?? null, index: hit.index })
      }
    }

    if (regions.length < 2) return undefined

    const polygons = regions.map(r => r.region.coords.points.map(([x, y]) => ({ x, y })))
    let finalPolygon = unionPolygons(polygons)
    if (!finalPolygon) {
      finalPolygon = polygons[0] ?? null
      if (!finalPolygon) return undefined
    }

    const mergedTextContentVariants = this.mergeTextContentVariants(
      regions.flatMap(r => (isTextRegion(r.region) ? r.region.textContentVariants ?? [] : []))
    )

    const mergedTextLines: TextLine[] = []
    const mergedChildRegions: Region[] = []
    const targetKind = this.data.targetKind ?? regions[0]!.region.kind

    if (this.data.mergeChildren) {
      for (const { region } of regions) {
        if (isTextRegion(region) && region.textLines && canContainTextLines(targetKind)) {
          mergedTextLines.push(...region.textLines)
        }
        if (region.regions) {
          mergedChildRegions.push(...region.regions)
        }
      }
    }

    const newId = `merged_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
    const newRegion: Region = canContainTextLines(targetKind)
      ? {
          id: newId,
          kind: targetKind as 'TextRegion',
          coords: new Polygon(finalPolygon.map(p => [p.x, p.y])),
          regions: mergedChildRegions,
          textLines: mergedTextLines,
          textContentVariants: mergedTextContentVariants.length > 0 ? mergedTextContentVariants : undefined
        }
      : {
          id: newId,
          kind: targetKind as Exclude<RegionKind, 'TextRegion' | 'GraphicRegion' | 'ChartRegion'>,
          coords: new Polygon(finalPolygon.map(p => [p.x, p.y])),
          regions: mergedChildRegions
        }

    this.undoData = {
      removedRegions: regions.map(r => ({ ...r })),
      removedTextLines: [],
      createdId: newId,
      relations: cloneRelations(pcGts.page.relations),
      readingOrder: cloneReadingOrder(pcGts.page.readingOrder)
    }

    const sortedRegions = [...regions].sort((a, b) => b.index - a.index)
    for (const { region, parentId } of sortedRegions) {
      if (parentId) {
        const parent = findRegionRecursive(pcGts.page.regions, parentId)
        if (parent?.region.regions) {
          const idx = parent.region.regions.findIndex(r => r.id === region.id)
          if (idx >= 0) parent.region.regions.splice(idx, 1)
        }
      } else {
        const idx = pcGts.page.regions.findIndex(r => r.id === region.id)
        if (idx >= 0) pcGts.page.regions.splice(idx, 1)
      }
    }

    const firstRegion = regions[0]!
    if (firstRegion.parentId) {
      const parent = findRegionRecursive(pcGts.page.regions, firstRegion.parentId)
      if (parent?.region.regions) {
        parent.region.regions.splice(Math.min(firstRegion.index, parent.region.regions.length), 0, newRegion)
      } else {
        pcGts.page.regions.push(newRegion)
      }
    } else {
      pcGts.page.regions.splice(Math.min(firstRegion.index, pcGts.page.regions.length), 0, newRegion)
    }

    const mergedSourceIds = new Set(regions.map(region => region.region.id))
    pcGts.page.relations = removeRelationsReferencingIds(pcGts.page.relations, mergedSourceIds)
    replaceIdsInReadingOrder(pcGts.page.readingOrder?.root?.elements, mergedSourceIds, newId)

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    useEditorUiStore().bumpReadingOrderVersion()
    return { id: newId }
  }

  private mergeTextLines(pcGts: PcGts, session: any): { id: string } | undefined {
    const textLines: Array<{ textLine: TextLine, parentRegionId: string, index: number }> = []

    for (const id of this.data.elementIds) {
      const hit = findTextLineRecursive(pcGts.page.regions, id)
      if (hit) {
        textLines.push({ textLine: hit.textLine, parentRegionId: hit.parentTextRegion.id, index: hit.index })
      }
    }

    if (textLines.length < 2) return undefined

    const polygons = textLines.map(t => t.textLine.coords.points.map(([x, y]) => ({ x, y })))
    const unionedPolygon = unionPolygons(polygons)
    if (!unionedPolygon) return undefined

    const mergedTextContentVariants = this.mergeTextContentVariants(
      textLines.flatMap(t => t.textLine.textContentVariants ?? [])
    )

    const newId = `merged_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
    const newTextLine = new TextLine({
      id: newId,
      coords: new Polygon(unionedPolygon.map(p => [p.x, p.y])),
      textContentVariants: mergedTextContentVariants.length > 0 ? mergedTextContentVariants : undefined
    })

    this.undoData = {
      removedRegions: [],
      removedTextLines: textLines.map(t => ({ ...t })),
      createdId: newId,
      relations: cloneRelations(pcGts.page.relations),
      readingOrder: cloneReadingOrder(pcGts.page.readingOrder)
    }

    const sortedTextLines = [...textLines].sort((a, b) => b.index - a.index)
    for (const { textLine, parentRegionId } of sortedTextLines) {
      const parentHit = findRegionRecursive(pcGts.page.regions, parentRegionId)
      if (parentHit && isTextRegion(parentHit.region) && parentHit.region.textLines) {
        const idx = parentHit.region.textLines.findIndex(tl => tl.id === textLine.id)
        if (idx >= 0) parentHit.region.textLines.splice(idx, 1)
      }
    }

    const firstTextLine = textLines[0]!
    const parentHit = findRegionRecursive(pcGts.page.regions, firstTextLine.parentRegionId)
    if (parentHit && isTextRegion(parentHit.region)) {
      parentHit.region.textLines = parentHit.region.textLines ?? []
      parentHit.region.textLines.splice(Math.min(firstTextLine.index, parentHit.region.textLines.length), 0, newTextLine)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    useEditorUiStore().bumpReadingOrderVersion()
    return { id: newId }
  }

  private mergeTextContentVariants(textContentVariants: TextContentVariant[]): TextContentVariant[] {
    if (textContentVariants.length === 0) return []

    const byIndex = new Map<number | undefined, string[]>()
    for (const te of textContentVariants) {
      const key = te.index
      const existing = byIndex.get(key) ?? []
      existing.push(te.unicode)
      byIndex.set(key, existing)
    }

    const result: TextContentVariant[] = []
    for (const [index, texts] of byIndex) {
      result.push(new TextContentVariant(texts.join(' '), undefined, undefined, index))
    }
    return result.sort((a, b) => (a.index ?? 0) - (b.index ?? 0))
  }

  undo(ctx?: CommandContext): void {
    if (!this.undoData) return

    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    if (this.data.elementType === 'textline') {
      for (const region of this.iterateAllRegions(pcGts.page.regions)) {
        if (isTextRegion(region) && region.textLines) {
          const idx = region.textLines.findIndex(tl => tl.id === this.undoData!.createdId)
          if (idx >= 0) {
            region.textLines.splice(idx, 1)
            break
          }
        }
      }
    } else {
      this.removeRegionById(pcGts.page.regions, this.undoData.createdId)
    }

    const sortedRegions = [...this.undoData.removedRegions].sort((a, b) => a.index - b.index)
    for (const { region, parentId, index } of sortedRegions) {
      if (parentId) {
        const parent = findRegionRecursive(pcGts.page.regions, parentId)
        if (parent?.region.regions) {
          parent.region.regions.splice(index, 0, region)
        }
      } else {
        pcGts.page.regions.splice(index, 0, region)
      }
    }

    const sortedTextLines = [...this.undoData.removedTextLines].sort((a, b) => a.index - b.index)
    for (const { textLine, parentRegionId, index } of sortedTextLines) {
      const parentHit = findRegionRecursive(pcGts.page.regions, parentRegionId)
      if (parentHit && isTextRegion(parentHit.region)) {
        parentHit.region.textLines = parentHit.region.textLines ?? []
        parentHit.region.textLines.splice(index, 0, textLine)
      }
    }

    pcGts.page.relations = cloneRelations(this.undoData.relations)
    pcGts.page.readingOrder = cloneReadingOrder(this.undoData.readingOrder)

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    useEditorUiStore().bumpReadingOrderVersion()
  }

  private* iterateAllRegions(regions: Region[]): Generator<Region> {
    for (const region of regions) {
      yield region
      if (region.regions) yield* this.iterateAllRegions(region.regions)
    }
  }

  private removeRegionById(regions: Region[], id: string): boolean {
    const idx = regions.findIndex(r => r.id === id)
    if (idx >= 0) {
      regions.splice(idx, 1)
      return true
    }
    for (const region of regions) {
      if (region.regions && this.removeRegionById(region.regions, id)) return true
    }
    return false
  }

  getDescription(): string {
    return `Merge ${this.data.elementIds.length} ${this.data.elementType}s`
  }
}
