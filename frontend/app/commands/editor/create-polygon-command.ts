import type { Command, CommandContext } from './types'
import { PcGts, PolygonType, TextLine } from '@/models/editor'
import type { Point, PolygonType as PolygonTypeType, TextRegion } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { toPlainPoints } from './utils'
import { visibilityService } from '@/services/editor/visibility-service'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { subtractPolygon } from '@/utils/editor/polygon-clipping'
import {
  findRegionRecursive,
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'

export interface CreatePolygonCommandData {
  points: Point[]
  label?: string
  type?: PolygonTypeType
  parentId?: string
  preventOverlapOnCreate?: boolean
  overlapMinAreaThreshold?: number
}

export class CreatePolygonCommand implements Command {
  private regionId: string
  private points: Point[]
  private label: string
  private type: PolygonTypeType
  private parentId?: string
  private preventOverlapOnCreate: boolean
  private overlapMinAreaThreshold: number

  private createdKind: 'region' | 'textline' | null = null
  private resolvedPoints: Point[] | null | undefined = undefined

  constructor(data: CreatePolygonCommandData) {
    this.points = toPlainPoints(data.points)
    this.regionId = this.generateId()
    this.label = data.label || this.generateDefaultLabel(data.type)
    this.type = data.type || PolygonType.REGION
    this.parentId = data.parentId
    this.preventOverlapOnCreate = data.preventOverlapOnCreate ?? false
    this.overlapMinAreaThreshold = data.overlapMinAreaThreshold ?? 0.0001
  }

  execute(ctx?: CommandContext): { id: string, created: boolean } {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return { id: this.regionId, created: false }

    const pointsToCreate = this.resolvePointsToCreate(ctx)
    if (!pointsToCreate || pointsToCreate.length < 3) {
      this.createdKind = null
      throw new Error('New shape is fully covered by visible annotations.')
    }

    if (this.type === PolygonType.TEXTLINE) {
      const parentId = this.parentId
      if (!parentId) return { id: this.regionId, created: false }

      const hit = findRegionRecursive(pcGts.page.regions, parentId)
      if (!hit || hit.region.kind !== 'TextRegion') return { id: this.regionId, created: false }

      const textRegion = hit.region as TextRegion
      textRegion.textLines = textRegion.textLines ?? []
      textRegion.textLines.push(
        new TextLine({
          id: this.regionId,
          coords: new Polygon(pointsToCreate.map(p => [p.x, p.y]))
        })
      )
      this.createdKind = 'textline'
    } else {
      const region: TextRegion = {
        id: this.regionId,
        kind: 'TextRegion',
        coords: new Polygon(pointsToCreate.map(p => [p.x, p.y])),
        regions: [],
        textLines: [],
        textContentVariants: []
      }

      if (this.parentId) {
        const parentHit = findRegionRecursive(pcGts.page.regions, this.parentId)
        if (parentHit) {
          parentHit.region.regions = parentHit.region.regions ?? []
          parentHit.region.regions.push(region)
        } else {
          pcGts.page.regions.push(region)
        }
      } else {
        pcGts.page.regions.push(region)
      }
      this.createdKind = 'region'
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    return { id: this.regionId, created: true }
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    if (this.createdKind === 'textline') {
      const hit = findTextLineById(pcGts, this.regionId)
      if (hit) {
        hit.parent.textLines?.splice(hit.index, 1)
      }
    } else if (this.createdKind === 'region') {
      removeRegionById(pcGts, this.regionId)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  getDescription(): string {
    return `Create polygon with ${this.points.length} points`
  }

  private generateId(): string {
    return `polygon_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  private generateDefaultLabel(type?: PolygonType): string {
    if (!type) return 'TextRegion'
    if (type === PolygonType.TEXTLINE) return 'TextLine'
    if (type === PolygonType.BASELINE) return 'Baseline'
    return 'TextRegion'
  }

  private resolvePointsToCreate(ctx?: CommandContext): Point[] | null {
    if (this.resolvedPoints !== undefined) {
      return this.resolvedPoints
    }

    if (!this.preventOverlapOnCreate) {
      this.resolvedPoints = toPlainPoints(this.points)
      return this.resolvedPoints
    }

    if (this.type !== PolygonType.REGION && this.type !== PolygonType.TEXTLINE) {
      this.resolvedPoints = toPlainPoints(this.points)
      return this.resolvedPoints
    }

    const clipped = this.subtractVisibleOverlaps(ctx)
    this.resolvedPoints = clipped ? toPlainPoints(clipped) : null
    return this.resolvedPoints
  }

  private subtractVisibleOverlaps(ctx?: CommandContext): Point[] | null {
    const controls = ctx?.session?.controls.value
    const runtimePolygons = controls?.polygons
    if (!runtimePolygons || runtimePolygons.length === 0) {
      return toPlainPoints(this.points)
    }

    const visibilityContext = {
      selectedPolygonIndex: controls?.selectedPolygonIndex?.value ?? -1,
      selectedPolylineIndex: controls?.selectedPolylineIndex?.value ?? -1,
      allPolygons: runtimePolygons,
      allPolylines: controls?.polylines ?? [],
      viewMode: controls?.viewMode?.value,
      hiddenPolygonIds: new Set(controls?.hiddenPolygonIds?.value ?? []),
      hiddenPolylineIds: new Set(controls?.hiddenPolylineIds?.value ?? []),
      temporaryHoverPolygonId: controls?.hoveredPolygonId?.value ?? null,
      temporaryHoverPolylineId: controls?.hoveredPolylineId?.value ?? null
    }

    const excludedPolygonIds = this.collectExcludedPolygonIds(runtimePolygons)
    let remaining = toPlainPoints(this.points)

    for (const candidate of runtimePolygons) {
      if (!candidate || candidate.type !== this.type) continue
      if (excludedPolygonIds.has(candidate.id)) continue

      const isVisible = visibilityService.shouldShowPolygon(candidate, visibilityContext)
        || visibilityService.shouldRenderAsBackground(candidate, visibilityContext)
      if (!isVisible) continue

      const subtractResult = subtractPolygon(
        remaining,
        candidate.points,
        this.overlapMinAreaThreshold
      )

      if (!subtractResult.success) continue
      if (subtractResult.resultPolygons.length === 0 || subtractResult.largestPolygonIndex < 0) {
        return null
      }

      const largest = subtractResult.resultPolygons[subtractResult.largestPolygonIndex]
      if (!largest || largest.length < 3) {
        return null
      }

      remaining = toPlainPoints(largest)
    }

    return remaining
  }

  private collectExcludedPolygonIds(polygons: RenderablePolygon[]): Set<string> {
    const excluded = new Set<string>()
    let currentParentId = this.parentId

    while (currentParentId) {
      excluded.add(currentParentId)
      const parent = polygons.find(p => p.id === currentParentId)
      currentParentId = parent?.parentId
    }

    return excluded
  }
}

function findTextLineById(pcGts: { page: { regions: any[] } }, id: string): { parent: TextRegion, index: number } | null {
  const hit = findTextLineRecursive(pcGts.page.regions as any, id)
  if (!hit) return null
  return { parent: hit.parentTextRegion, index: hit.index }
}

function removeRegionById(pcGts: { page: { regions: any[] } }, id: string): void {
  const hit = findRegionRecursive(pcGts.page.regions as any, id)
  if (!hit) return
  const siblings = hit.parent ? (hit.parent.regions ?? []) : pcGts.page.regions
  siblings.splice(hit.index, 1)
}
