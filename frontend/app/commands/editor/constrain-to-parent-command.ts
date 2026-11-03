import type { Command, CommandContext } from './types'
import type { Region } from '@/models/editor'
import type { Polygon } from '@/models/editor/geometry'
import { CutElementsCommand } from './cut-elements-command'
import { collectRenderablePolygonsFromPcGts, findGlyphRecursive, findRegionRecursive, findTextLineRecursive, findWordRecursive } from '@/utils/editor/pcgts-editor-primitives'

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
        const parent = this.findParentCoordsHolder(document.page.regions, target.id)
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

  private findParentCoordsHolder(regions: Region[], id: string): { coords: Polygon } | null {
    const region = findRegionRecursive(regions, id)
    if (region) return region.parent
    const line = findTextLineRecursive(regions, id)
    if (line) return line.parentTextRegion
    const word = findWordRecursive(regions, id)
    if (word) return word.parentTextLine
    return findGlyphRecursive(regions, id)?.parentWord ?? null
  }
}
