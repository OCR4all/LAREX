import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Relation } from '@/models/editor'
import {
  cloneRelations,
  createGeneratedRelationId,
  normalizeRelation
} from '@/utils/editor/relations'

export interface CreateRelationCommandData {
  relation: Partial<Relation>
}

export class CreateRelationCommand implements Command<{ id: string } | undefined> {
  private readonly relation: Partial<Relation>
  private previousRelations: Relation[] | undefined
  private createdId: string | null = null

  constructor(data: CreateRelationCommandData) {
    this.relation = data.relation
  }

  execute(ctx?: CommandContext): { id: string } | undefined {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return undefined

    const normalized = normalizeRelation(this.relation)
    if (!normalized.sourceRegionRef || !normalized.targetRegionRef) {
      return undefined
    }

    const nextRelation: Relation = {
      ...normalized,
      id: normalized.id ?? createGeneratedRelationId(),
      type: normalized.type ?? 'link'
    }

    this.previousRelations = cloneRelations(pcGts.page.relations)
    this.createdId = nextRelation.id ?? null

    pcGts.page.relations = [...(cloneRelations(pcGts.page.relations) ?? []), nextRelation]
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)

    return this.createdId ? { id: this.createdId } : undefined
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
    return 'Create relation'
  }
}
