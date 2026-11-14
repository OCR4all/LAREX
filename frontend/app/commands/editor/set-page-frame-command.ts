import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Border, Point } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { invalidatePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { visibilityService } from '@/services/editor/visibility-service'
import { rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'
import { PAGE_FRAME_LABELS, getPageFrame, pageFrameIdForKind, setPageFrame, type PageFrameKind } from '@/utils/editor/page-frame'
import { toPlainPoints } from './utils'

export interface SetPageFrameCommandData {
  kind: PageFrameKind
  points: Point[]
}

export class SetPageFrameCommand implements Command<{ id: string, created: boolean }> {
  private readonly kind: PageFrameKind
  private readonly points: [number, number][]
  private previous: Border | undefined
  private replaced = false

  constructor(data: SetPageFrameCommandData) {
    this.kind = data.kind
    this.points = toPlainPoints(data.points).map(p => [p.x, p.y])
  }

  execute(ctx?: CommandContext): { id: string, created: boolean } {
    const id = pageFrameIdForKind(this.kind)
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || this.points.length < 3) return { id, created: false }

    this.previous = getPageFrame(pcGts.page, this.kind)
    this.replaced = this.previous !== undefined
    setPageFrame(pcGts.page, this.kind, { coords: new Polygon([...this.points]) })
    this.commit(ctx)
    return { id, created: true }
  }

  undo(ctx?: CommandContext): void {
    const pcGts = ctx?.session.document.value
    if (!ctx || !pcGts) return

    setPageFrame(pcGts.page, this.kind, this.previous)
    this.commit(ctx)
  }

  getDescription(): string {
    return `${this.replaced ? 'Replace' : 'Create'} ${PAGE_FRAME_LABELS[this.kind].toLowerCase()}`
  }

  private commit(ctx: CommandContext): void {
    const { session } = ctx
    const pcGts = session.document.value
    if (!pcGts) return
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    invalidatePolygonGeometry(ctx.canvasId, pageFrameIdForKind(this.kind))
    visibilityService.clearCache()
  }
}
