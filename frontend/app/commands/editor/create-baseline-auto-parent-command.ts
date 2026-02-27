import type { Command, CommandContext } from './types'
import type { Point } from '@/models/editor'
import { PolygonType } from '@/models/editor'
import { CreatePolygonCommand } from './create-polygon-command'
import { CreatePolylineCommand } from './create-polyline-command'
import { toPlainPoints } from './utils'
import {
  findBestContainingPolygon,
  createBoundingRectangleFromPoints
} from '@/utils/editor/polygon-clipping'
import {
  collectRenderablePolygonsFromPcGts,
  collectRenderablePolylinesFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'
import type { RenderablePolygon } from '@/types/editor/rendering'

/**
 * Default padding for auto-generated parent shapes (in coordinate units).
 */
const AUTO_PARENT_PADDING = 0.02

/**
 * Minimum overlap percentage required to assign an element to an existing parent.
 */
const MIN_OVERLAP_PERCENTAGE = 50

export interface CreateBaselineAutoParentCommandData {
  points: Point[]
}

/**
 * Result of baseline creation with auto-parent
 */
export interface CreateBaselineAutoParentResult {
  baselineId: string
  parentTextlineId: string
  parentRegionId: string
  createdTextline: boolean
  createdRegion: boolean
}

/**
 * Command that creates a Baseline with automatic parent TextLine (and TextRegion) assignment.
 *
 * Behavior:
 * 1. Searches for existing TextLines (without baselines) that overlap with the new baseline by >= 50%
 * 2. If found: assigns the baseline to the textline with highest overlap
 * 3. If not found, searches for existing TextRegions that overlap:
 *    a. If found: creates a TextLine inside that region, then the baseline
 *    b. If not found: creates TextRegion -> TextLine -> Baseline (3-level hierarchy)
 *
 * All operations are wrapped in a CompoundCommand for atomic undo.
 */
export class CreateBaselineAutoParentCommand implements Command {
  private points: Point[]

  private executedCommands: Command[] = []
  private result: CreateBaselineAutoParentResult | null = null

  constructor(data: CreateBaselineAutoParentCommandData) {
    this.points = toPlainPoints(data.points)
  }

  execute(ctx?: CommandContext): CreateBaselineAutoParentResult {
    const session = ctx?.session
    const pcGts = session?.document.value

    if (!session || !pcGts) {
      throw new Error('CreateBaselineAutoParentCommand: No session or document available')
    }

    const allPolygons = collectRenderablePolygonsFromPcGts(pcGts)
    const allPolylines = collectRenderablePolylinesFromPcGts(pcGts)

    const textlinesWithBaselines = new Set<string>()
    for (const p of allPolylines) {
      if (p.type === PolygonType.BASELINE && p.parentId) {
        textlinesWithBaselines.add(p.parentId)
      }
    }

    const availableTextlines = allPolygons
      .filter((p): p is RenderablePolygon =>
        p.type === PolygonType.TEXTLINE && !textlinesWithBaselines.has(p.id)
      )
      .map(p => ({ polygon: p.points, id: p.id, parentId: p.parentId }))

    const bestTextline = findBestContainingPolygon(
      this.points,
      availableTextlines,
      MIN_OVERLAP_PERCENTAGE
    )

    if (bestTextline) {
      const createBaselineCmd = new CreatePolylineCommand({
        points: this.points,
        parentId: bestTextline.id
      })

      this.executedCommands.push(createBaselineCmd)
      const cmdResult = createBaselineCmd.execute(ctx) as { id: string, created?: boolean }
      if (cmdResult?.created === false) {
        throw new Error(`Failed to assign baseline to textline ${bestTextline.id}`)
      }

      const textline = availableTextlines.find(t => t.id === bestTextline.id)

      this.result = {
        baselineId: cmdResult.id,
        parentTextlineId: bestTextline.id,
        parentRegionId: textline?.parentId ?? '',
        createdTextline: false,
        createdRegion: false
      }

      return this.result
    }

    const textRegionCandidates = allPolygons
      .filter((p): p is RenderablePolygon & { regionKind: 'TextRegion' } =>
        p.type === PolygonType.REGION && p.regionKind === 'TextRegion'
      )
      .map(p => ({ polygon: p.points, id: p.id }))

    const bestRegion = findBestContainingPolygon(
      this.points,
      textRegionCandidates,
      MIN_OVERLAP_PERCENTAGE
    )

    const textlinePoints = createBoundingRectangleFromPoints(this.points, AUTO_PARENT_PADDING)

    if (bestRegion) {
      const createTextlineCmd = new CreatePolygonCommand({
        points: textlinePoints,
        type: PolygonType.TEXTLINE,
        parentId: bestRegion.id
      })

      this.executedCommands.push(createTextlineCmd)
      const textlineResult = createTextlineCmd.execute(ctx) as { id: string }
      const textlineId = textlineResult.id

      const createBaselineCmd = new CreatePolylineCommand({
        points: this.points,
        parentId: textlineId
      })

      this.executedCommands.push(createBaselineCmd)
      const baselineResult = createBaselineCmd.execute(ctx) as { id: string, created?: boolean }
      if (baselineResult?.created === false) {
        throw new Error(`Failed to assign baseline to textline ${textlineId}`)
      }

      this.result = {
        baselineId: baselineResult.id,
        parentTextlineId: textlineId,
        parentRegionId: bestRegion.id,
        createdTextline: true,
        createdRegion: false
      }

      return this.result
    }

    const regionPoints = createBoundingRectangleFromPoints(this.points, AUTO_PARENT_PADDING * 2)

    const createRegionCmd = new CreatePolygonCommand({
      points: regionPoints,
      type: PolygonType.REGION,
      label: 'TextRegion'
    })

    this.executedCommands.push(createRegionCmd)
    const regionResult = createRegionCmd.execute(ctx) as { id: string }
    const regionId = regionResult.id

    const createTextlineCmd = new CreatePolygonCommand({
      points: textlinePoints,
      type: PolygonType.TEXTLINE,
      parentId: regionId
    })

    this.executedCommands.push(createTextlineCmd)
    const textlineResult = createTextlineCmd.execute(ctx) as { id: string }
    const textlineId = textlineResult.id

    const createBaselineCmd = new CreatePolylineCommand({
      points: this.points,
      parentId: textlineId
    })

    this.executedCommands.push(createBaselineCmd)
    const baselineResult = createBaselineCmd.execute(ctx) as { id: string, created?: boolean }
    if (baselineResult?.created === false) {
      throw new Error(`Failed to assign baseline to textline ${textlineId}`)
    }

    this.result = {
      baselineId: baselineResult.id,
      parentTextlineId: textlineId,
      parentRegionId: regionId,
      createdTextline: true,
      createdRegion: true
    }

    return this.result
  }

  undo(ctx?: CommandContext): void {
    for (let i = this.executedCommands.length - 1; i >= 0; i--) {
      const cmd = this.executedCommands[i]
      if (cmd) {
        cmd.undo(ctx)
      }
    }
  }

  getDescription(): string {
    if (!this.result) return 'Create baseline with auto-parent'

    if (this.result.createdRegion && this.result.createdTextline) {
      return 'Create baseline with auto-generated region and textline'
    } else if (this.result.createdTextline) {
      return 'Create baseline with auto-generated textline'
    }
    return 'Create baseline with auto-assigned parent'
  }

  /**
   * Get the result of the last execution
   */
  getResult(): CreateBaselineAutoParentResult | null {
    return this.result
  }
}
