import type { Command, CommandContext } from './types'
import type { Point } from '@/models/editor'
import { PolygonType } from '@/models/editor'
import { CreatePolygonCommand } from './create-polygon-command'
import { CreateTextlineAutoParentCommand } from './create-textline-auto-parent-command'
import { CompoundCommand } from './compound-command'
import { toPlainPoints } from './utils'
import {
  findBestContainingPolygon,
  createBoundingRectangleFromPoints
} from '@/utils/editor/polygon-clipping'
import { collectRenderablePolygonsFromPcGts } from '@/utils/editor/pcgts-editor-primitives'
import {
  AUTO_PARENT_MIN_OVERLAP_PERCENTAGE,
  NESTED_AUTO_PARENT_PADDING
} from '@/utils/editor/auto-parent-utils'

export type TextElementPolygonType = typeof PolygonType.WORD | typeof PolygonType.GLYPH

export interface CreateTextElementAutoParentCommandData {
  points: Point[]
  type: TextElementPolygonType
  preventOverlapOnCreate?: boolean
  overlapMinAreaThreshold?: number
}

export interface CreateTextElementAutoParentResult {
  id: string
  parentId: string
  createdParent: boolean
}

/**
 * Command that creates a Word or Glyph with automatic parent assignment.
 *
 * Behavior:
 * 1. Searches for an existing parent (TextLine for words, Word for glyphs) that the new shape overlaps by >= 50%
 * 2. If found: assigns the element to the parent with the highest overlap (smallest on tie)
 * 3. If not found: creates a minimal bounding-box parent (recursively auto-parented itself) and places the element inside
 *
 * All operations are wrapped in a CompoundCommand for atomic undo.
 */
export class CreateTextElementAutoParentCommand implements Command {
  private points: Point[]
  private type: TextElementPolygonType
  private preventOverlapOnCreate: boolean
  private overlapMinAreaThreshold: number

  private innerCommand: Command | null = null
  private result: CreateTextElementAutoParentResult | null = null

  constructor(data: CreateTextElementAutoParentCommandData) {
    this.points = toPlainPoints(data.points)
    this.type = data.type
    this.preventOverlapOnCreate = data.preventOverlapOnCreate ?? false
    this.overlapMinAreaThreshold = data.overlapMinAreaThreshold ?? 0.0001
  }

  execute(ctx?: CommandContext): CreateTextElementAutoParentResult {
    const session = ctx?.session
    const pcGts = session?.document.value

    if (!session || !pcGts) {
      throw new Error('CreateTextElementAutoParentCommand: No session or document available')
    }

    const parentType = this.type === PolygonType.WORD ? PolygonType.TEXTLINE : PolygonType.WORD
    const candidates = collectRenderablePolygonsFromPcGts(pcGts)
      .filter(p => p.type === parentType)
      .map(p => ({ polygon: p.points, id: p.id }))

    const bestParent = findBestContainingPolygon(this.points, candidates, AUTO_PARENT_MIN_OVERLAP_PERCENTAGE)

    let parentCommand: Command | null = null
    let parentId = bestParent?.id
    if (!parentId) {
      const parentPoints = createBoundingRectangleFromPoints(this.points, NESTED_AUTO_PARENT_PADDING)
      if (parentType === PolygonType.TEXTLINE) {
        const textlineCommand = new CreateTextlineAutoParentCommand({ points: parentPoints })
        parentId = textlineCommand.execute(ctx).textlineId
        parentCommand = textlineCommand
      } else {
        const wordCommand = new CreateTextElementAutoParentCommand({ points: parentPoints, type: PolygonType.WORD })
        parentId = wordCommand.execute(ctx).id
        parentCommand = wordCommand
      }
    }

    const createCommand = new CreatePolygonCommand({
      points: this.points,
      type: this.type,
      parentId,
      preventOverlapOnCreate: this.preventOverlapOnCreate,
      overlapMinAreaThreshold: this.overlapMinAreaThreshold
    })

    let created: { id: string, created: boolean }
    try {
      created = createCommand.execute(ctx)
    } catch (error) {
      parentCommand?.undo(ctx)
      throw error
    }
    if (created.created === false) {
      parentCommand?.undo(ctx)
      throw new Error(`Created ${this.type} has no remaining visible area after overlap prevention.`)
    }

    this.innerCommand = parentCommand
      ? new CompoundCommand([parentCommand, createCommand], this.getDescription())
      : createCommand
    this.result = { id: created.id, parentId, createdParent: !!parentCommand }
    return this.result
  }

  undo(ctx?: CommandContext): void {
    this.innerCommand?.undo(ctx)
  }

  getDescription(): string {
    const parentLabel = this.type === PolygonType.WORD ? 'textline' : 'word'
    return this.result?.createdParent
      ? `Create ${this.type} with auto-generated parent ${parentLabel}`
      : `Create ${this.type} with auto-assigned parent`
  }

  getResult(): CreateTextElementAutoParentResult | null {
    return this.result
  }
}
