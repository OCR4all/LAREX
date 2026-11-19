import type { Command, CommandContext } from './types'
import { Polygon } from '@/models/editor/geometry'
import type { Region } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { inferTableFromShapes } from '@/utils/editor/table/table-builder'
import { applyTableModel } from '@/utils/editor/table/table-model'
import { commitDocument, newRegionId, regionSiblings, replaceContents, shapePoints } from './table-command-utils'

export interface BuildTableFromRegionsCommandData {
  regionIds: string[]
}

export class BuildTableFromRegionsCommand implements Command<{ id: string } | undefined> {
  private regionIds: string[]
  private tableId = newRegionId('table')
  private parentId: string | null = null
  private previousSiblings: Region[] | null = null

  constructor(data: BuildTableFromRegionsCommandData) {
    this.regionIds = [...new Set(data.regionIds)]
  }

  execute(ctx?: CommandContext): { id: string } | undefined {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || this.regionIds.length === 0) return undefined

    const hits = this.regionIds.map(id => findRegionRecursive(pcGts.page.regions, id))
    if (hits.some(hit => !hit)) throw new Error('Some selected regions no longer exist')
    const parentIds = new Set(hits.map(hit => hit!.parent?.id ?? null))
    if (parentIds.size !== 1) throw new Error('All regions of a table must share the same parent')
    if (hits.some(hit => hit!.region.kind === 'TableRegion')) throw new Error('A table cannot be built from other tables')

    this.parentId = [...parentIds][0]!
    const siblings = regionSiblings(pcGts, this.parentId)
    if (!siblings) return undefined

    const regions = hits.map(hit => hit!.region)
    const { model, outline } = inferTableFromShapes(
      this.tableId,
      regions.map(region => ({ id: region.id, points: shapePoints(region) }))
    )
    const table = {
      id: this.tableId,
      kind: 'TableRegion',
      coords: new Polygon(outline),
      regions: regions.map(region => ({ ...region }))
    } as Region
    applyTableModel(table, model)

    const selected = new Set(this.regionIds)
    const insertAt = Math.min(...hits.map(hit => hit!.index))
    this.previousSiblings = [...siblings]
    const before = siblings.slice(0, insertAt)
    const after = siblings.slice(insertAt).filter(region => !selected.has(region.id))
    replaceContents(siblings, [...before, table, ...after])

    commitDocument(session, pcGts)
    return { id: this.tableId }
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || !this.previousSiblings) return
    const siblings = regionSiblings(pcGts, this.parentId)
    if (!siblings) return
    replaceContents(siblings, this.previousSiblings)
    commitDocument(session, pcGts)
  }

  getDescription(): string {
    return `Build table from ${this.regionIds.length} regions`
  }
}
