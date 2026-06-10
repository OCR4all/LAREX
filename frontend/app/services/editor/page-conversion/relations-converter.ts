import type { Relation } from '@/models/editor'
import type { RelationsDto } from '@/types/page-dto'
import { convertLabelsFromDto, convertLabelsToDto, undefinedIfBlank } from './shared'

export function convertRelationsFromDto(dto?: RelationsDto): Relation[] | undefined {
  if (!dto?.relations?.length) return undefined
  return dto.relations.map(relation => ({
    id: undefinedIfBlank(relation.id),
    type: undefinedIfBlank(relation.type),
    sourceRegionRef: undefinedIfBlank(relation.sourceRegionRef),
    targetRegionRef: undefinedIfBlank(relation.targetRegionRef),
    custom: undefinedIfBlank(relation.custom),
    comments: undefinedIfBlank(relation.comments),
    labels: convertLabelsFromDto(relation.labels)
  }))
}

export function convertRelationsToDto(relations?: Relation[]): RelationsDto | undefined {
  if (!relations?.length) return undefined
  return {
    relations: relations.map(relation => ({
      id: undefinedIfBlank(relation.id),
      type: undefinedIfBlank(relation.type),
      sourceRegionRef: undefinedIfBlank(relation.sourceRegionRef),
      targetRegionRef: undefinedIfBlank(relation.targetRegionRef),
      custom: undefinedIfBlank(relation.custom),
      comments: undefinedIfBlank(relation.comments),
      labels: convertLabelsToDto(relation.labels)
    }))
  }
}
