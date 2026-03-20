import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Relation } from '@/models/editor'
import {
  cloneRelations,
  createGeneratedRelationId,
  normalizeRelation
} from '@/utils/editor/relations'

export interface UpdateRelationCommandData {
  relationId: string
  relation: Relation
}

export class UpdateRelationCommand implements Command<{ id: string } | undefined> {
  private readonly relationId: string
  private readonly relation: Relation
  private previousRelations: Relation[] | undefined
  private nextRelationId: string | null = null

  constructor(data: UpdateRelationCommandData) {
    this.relationId = data.relationId
    this.relation = data.relation
  }

  execute(ctx?: CommandContext): { id: string } | undefined {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts?.page.relations?.length) return undefined

    const normalized = normalizeRelation(this.relation)
    if (!normalized.sourceRegionRef || !normalized.targetRegionRef) {
      return undefined
    }

    const existingIndex = pcGts.page.relations.findIndex(relation => relation.id === this.relationId)
    if (existingIndex < 0) return undefined

    const nextRelation: Relation = {
      ...normalized,
      id: normalized.id ?? this.relationId ?? createGeneratedRelationId(),
      type: normalized.type ?? 'link'
    }

    this.previousRelations = cloneRelations(pcGts.page.relations)
    this.nextRelationId = nextRelation.id ?? null

    const updatedRelations = cloneRelations(pcGts.page.relations) ?? []
    updatedRelations.splice(existingIndex, 1, nextRelation)
    pcGts.page.relations = updatedRelations
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)

    return this.nextRelationId ? { id: this.nextRelationId } : undefined
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
    return 'Update relation'
  }
}
