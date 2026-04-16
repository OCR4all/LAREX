import type { Command, CommandContext } from './types'
import type { Point } from '@/models/editor'
import { PolygonType } from '@/models/editor'
import { CreatePolygonCommand } from './create-polygon-command'
import { CompoundCommand } from './compound-command'
import { toPlainPoints } from './utils'
import {
  findBestContainingPolygon,
  createBoundingRectangleFromPoints
} from '@/utils/editor/polygon-clipping'
import { collectRenderablePolygonsFromPcGts } from '@/utils/editor/pcgts-editor-primitives'
import type { RenderablePolygon } from '@/types/editor/rendering'

/**
 * Default padding for auto-generated parent regions (in coordinate units).
 * This creates a small margin around the textline for the bounding-box region.
 */
const AUTO_PARENT_PADDING = 0.02

/**
 * Minimum overlap percentage required to assign a textline to an existing region.
 */
const MIN_OVERLAP_PERCENTAGE = 50

export interface CreateTextlineAutoParentCommandData {
  points: Point[]
  label?: string
  preventOverlapOnCreate?: boolean
  overlapMinAreaThreshold?: number
}

/**
 * Result of textline creation with auto-parent
 */
export interface CreateTextlineAutoParentResult {
  textlineId: string
  parentRegionId: string
  createdParent: boolean
}

/**
 * Command that creates a TextLine with automatic parent TextRegion assignment.
 *
 * Behavior:
 * 1. Searches for existing TextRegions that overlap with the new textline by >= 50%
 * 2. If found: assigns the textline to the region with highest overlap (smallest on tie)
 * 3. If not found: creates a minimal bounding-box TextRegion and places the textline inside
 *
 * All operations are wrapped in a CompoundCommand for atomic undo.
 */
export class CreateTextlineAutoParentCommand implements Command {
  private points: Point[]
  private label?: string
  private preventOverlapOnCreate: boolean
  private overlapMinAreaThreshold: number

  private innerCommand: Command | null = null
  private result: CreateTextlineAutoParentResult | null = null

  constructor(data: CreateTextlineAutoParentCommandData) {
    this.points = toPlainPoints(data.points)
    this.label = data.label
    this.preventOverlapOnCreate = data.preventOverlapOnCreate ?? false
    this.overlapMinAreaThreshold = data.overlapMinAreaThreshold ?? 0.0001
  }

  execute(ctx?: CommandContext): CreateTextlineAutoParentResult {
    const session = ctx?.session
    const pcGts = session?.document.value

    if (!session || !pcGts) {
      throw new Error('CreateTextlineAutoParentCommand: No session or document available')
    }

    const allPolygons = collectRenderablePolygonsFromPcGts(pcGts)

    const textRegionCandidates = allPolygons
      .filter((p): p is RenderablePolygon & { regionKind: 'TextRegion' } =>
        p.type === PolygonType.REGION && p.regionKind === 'TextRegion'
      )
      .map(p => ({ polygon: p.points, id: p.id }))

    const bestParent = findBestContainingPolygon(
      this.points,
      textRegionCandidates,
      MIN_OVERLAP_PERCENTAGE
    )

    if (bestParent) {
      const createTextlineCmd = new CreatePolygonCommand({
        points: this.points,
        type: PolygonType.TEXTLINE,
        label: this.label,
        parentId: bestParent.id,
        preventOverlapOnCreate: this.preventOverlapOnCreate,
        overlapMinAreaThreshold: this.overlapMinAreaThreshold
      })

      this.innerCommand = createTextlineCmd
      const cmdResult = createTextlineCmd.execute(ctx) as { id: string, created: boolean }
      if (cmdResult.created === false) {
        throw new Error('Created textline has no remaining visible area after overlap prevention.')
      }

      this.result = {
        textlineId: cmdResult.id,
        parentRegionId: bestParent.id,
        createdParent: false
      }
    } else {
      const parentPoints = createBoundingRectangleFromPoints(this.points, AUTO_PARENT_PADDING)

      const createRegionCmd = new CreatePolygonCommand({
        points: parentPoints,
        type: PolygonType.REGION,
        label: 'TextRegion' // Default label for auto-created regions
      })

      const regionResult = createRegionCmd.execute(ctx) as { id: string }
      const parentId = regionResult.id

      const createTextlineCmd = new CreatePolygonCommand({
        points: this.points,
        type: PolygonType.TEXTLINE,
        label: this.label,
        parentId,
        preventOverlapOnCreate: this.preventOverlapOnCreate,
        overlapMinAreaThreshold: this.overlapMinAreaThreshold
      })

      const textlineResult = createTextlineCmd.execute(ctx) as { id: string, created: boolean }
      if (textlineResult.created === false) {
        createRegionCmd.undo(ctx)
        throw new Error('Created textline has no remaining visible area after overlap prevention.')
      }

      this.innerCommand = new CompoundCommand(
        [createRegionCmd, createTextlineCmd],
        'Create textline with auto-generated parent region'
      )

      this.result = {
        textlineId: textlineResult.id,
        parentRegionId: parentId,
        createdParent: true
      }
    }

    return this.result
  }

  undo(ctx?: CommandContext): void {
    if (!this.innerCommand) return

    if (this.result?.createdParent) {
      this.innerCommand.undo(ctx)
    } else {
      this.innerCommand.undo(ctx)
    }
  }

  getDescription(): string {
    if (this.result?.createdParent) {
      return `Create textline with auto-generated parent region`
    }
    return `Create textline with auto-assigned parent`
  }

  /**
   * Get the result of the last execution
   */
  getResult(): CreateTextlineAutoParentResult | null {
    return this.result
  }
}
