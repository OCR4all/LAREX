import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Relation } from '@/models/editor'
import { cloneRelations } from '@/utils/editor/relations'

export interface DeleteRelationCommandData {
  relationId: string
}

export class DeleteRelationCommand implements Command<void> {
  private readonly relationId: string
  private previousRelations: Relation[] | undefined

  constructor(data: DeleteRelationCommandData) {
    this.relationId = data.relationId
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts?.page.relations?.length) return

    this.previousRelations = cloneRelations(pcGts.page.relations)
    const nextRelations = pcGts.page.relations.filter(relation => relation.id !== this.relationId)
    if (nextRelations.length === pcGts.page.relations.length) return

    pcGts.page.relations = nextRelations.length > 0 ? cloneRelations(nextRelations) : undefined
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    pcGts.page.relations = cloneRelations(this.previousRelations)
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  getDescription(): string {
    return 'Delete relation'
  }
}
