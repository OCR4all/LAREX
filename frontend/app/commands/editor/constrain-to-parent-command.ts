import type { Command, CommandContext } from './types'
import { CutElementsCommand } from './cut-elements-command'
import { collectRenderablePolygonsFromPcGts, findRegionRecursive, findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'

/** Clips selected polygons against their current parents, in hierarchy order. */
export class ConstrainToParentCommand implements Command {
  private commands: CutElementsCommand[] = []

  constructor(private elementIds: string[]) {}

  execute(ctx?: CommandContext): void {
    const document = ctx?.session.document.value
    if (!document) return
    this.commands = []
    const selected = new Set(this.elementIds)
    // The document traversal visits parents before children. Resolve the parent
    // again for every operation, since an earlier clip may have split it.
    const targets = collectRenderablePolygonsFromPcGts(document).filter(p => selected.has(p.id))
    try {
      for (const target of targets) {
        const region = findRegionRecursive(document.page.regions, target.id)
        const line = region ? null : findTextLineRecursive(document.page.regions, target.id)
        const parent = region?.parent ?? line?.parentTextRegion
        if (!parent || parent.coords.points.length < 3) continue
        const command = new CutElementsCommand({
          mode: 'polygon',
          intersect: true,
          targetElementIds: [target.id],
          cutPoints: parent.coords.points.map(([x, y]) => ({ x, y }))
        })
        this.commands.push(command)
        command.execute(ctx)
      }
    } catch (error) {
      this.undo(ctx)
      throw error
    }
  }

  undo(ctx?: CommandContext): void {
    for (const command of [...this.commands].reverse()) command.undo(ctx)
  }

  getDescription(): string {
    return 'Constrain to Parent'
  }
}
