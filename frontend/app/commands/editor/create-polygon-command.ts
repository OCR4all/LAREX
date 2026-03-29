import type { Command, CommandContext } from './types'
import { PcGts, PolygonType, TextLine } from '@/models/editor'
import type { Point, PolygonType as PolygonTypeType, TextRegion } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { toPlainPoints } from './utils'
import { visibilityService } from '@/services/editor/visibility-service'
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
}

export class CreatePolygonCommand implements Command {
  private regionId: string
  private points: Point[]
  private label: string
  private type: PolygonTypeType
  private parentId?: string

  private createdKind: 'region' | 'textline' | null = null

  constructor(data: CreatePolygonCommandData) {
    this.points = toPlainPoints(data.points)
    this.regionId = this.generateId()
    this.label = data.label || this.generateDefaultLabel(data.type)
    this.type = data.type || PolygonType.REGION
    this.parentId = data.parentId
  }

  execute(ctx?: CommandContext): { id: string } {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return { id: this.regionId }

    if (this.type === PolygonType.TEXTLINE) {
      const parentId = this.parentId
      if (!parentId) return { id: this.regionId }

      const hit = findRegionRecursive(pcGts.page.regions, parentId)
      if (!hit || hit.region.kind !== 'TextRegion') return { id: this.regionId }

      const textRegion = hit.region as TextRegion
      textRegion.textLines = textRegion.textLines ?? []
      textRegion.textLines.push(
        new TextLine({
          id: this.regionId,
          coords: new Polygon(this.points.map(p => [p.x, p.y]))
        })
      )
      this.createdKind = 'textline'
    } else {
      const region: TextRegion = {
        id: this.regionId,
        kind: 'TextRegion',
        coords: new Polygon(this.points.map(p => [p.x, p.y])),
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
    return { id: this.regionId }
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
