import type { Command, CommandContext } from './types'
import { DeletePolygonCommand } from './delete-polygon-command'
import { DeletePolylineCommand } from './delete-polyline-command'
import {
  collectRenderablePolygonsFromPcGts,
  collectRenderablePolylinesFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'

export interface DeleteSelectedElementsCommandData {
  polygonIds?: string[]
  polylineIds?: string[]
}

function unique(ids: string[] | undefined): string[] {
  if (!ids?.length) return []
  return Array.from(new Set(ids.filter(Boolean)))
}

export class DeleteSelectedElementsCommand implements Command {
  private readonly selectedPolygonIds: string[]
  private readonly selectedPolylineIds: string[]

  private executed: Command[] = []

  constructor(data: DeleteSelectedElementsCommandData) {
    this.selectedPolygonIds = unique(data.polygonIds)
    this.selectedPolylineIds = unique(data.polylineIds)
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    this.executed = []

    const polygons = collectRenderablePolygonsFromPcGts(pcGts)
    const polylines = collectRenderablePolylinesFromPcGts(pcGts)

    const parentByPolygonId = new Map<string, string | undefined>()
    for (const p of polygons) parentByPolygonId.set(p.id, p.parentId)

    const selectedPolygonSet = new Set(this.selectedPolygonIds)

    const rootPolygonIds = this.selectedPolygonIds.filter(id => !isDescendantOfAnySelected(id, selectedPolygonSet, parentByPolygonId))
    const rootPolygonSet = new Set(rootPolygonIds)

    const parentByPolylineId = new Map<string, string | undefined>()
    for (const pl of polylines) parentByPolylineId.set(pl.id, pl.parentId)

    const polylineIds = this.selectedPolylineIds.filter((plId) => {
      const parentTextLineId = parentByPolylineId.get(plId)
      if (!parentTextLineId) return true
      return !isDescendantOfAnySelected(parentTextLineId, rootPolygonSet, parentByPolygonId)
    })

    for (const plId of polylineIds) {
      const cmd = new DeletePolylineCommand({ polylineId: plId })
      cmd.execute(ctx)
      this.executed.push(cmd)
    }

    for (const polyId of rootPolygonIds) {
      const cmd = new DeletePolygonCommand({ polygonId: polyId })
      cmd.execute(ctx)
      this.executed.push(cmd)
    }
  }

  undo(ctx?: CommandContext): void {
    for (let i = this.executed.length - 1; i >= 0; i--) {
      this.executed[i]?.undo(ctx)
    }
  }

  getDescription(): string {
    const count = this.selectedPolygonIds.length + this.selectedPolylineIds.length
    return `Delete ${count} selected element${count === 1 ? '' : 's'}`
  }
}

function isDescendantOfAnySelected(
  id: string,
  selected: Set<string>,
  parentById: Map<string, string | undefined>
): boolean {
  let current: string | undefined = id
  while (current) {
    const parent = parentById.get(current)
    if (!parent) return false
    if (selected.has(parent)) return true
    current = parent
  }
  return false
}
