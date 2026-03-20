import type { Labels, Relation, Region } from '@/models/editor'
import type { LabelsFormState } from '@/utils/editor/metadata-schema'

export const PAGE_RELATION_TYPE_OPTIONS = [
  { label: 'Link', value: 'link' },
  { label: 'Join', value: 'join' }
] as const

export type RelationPickerMode = 'idle' | 'pick-source' | 'pick-target' | 'repick-source' | 'repick-target'

export interface RelationDraftState {
  id: string
  type: string
  sourceRegionRef: string
  targetRegionRef: string
  custom: string
  comments: string
  labels: LabelsFormState[]
}

export function createEmptyRelationDraft(): RelationDraftState {
  return {
    id: '',
    type: 'link',
    sourceRegionRef: '',
    targetRegionRef: '',
    custom: '',
    comments: '',
    labels: []
  }
}

export function cloneRelations(relations?: Relation[]): Relation[] | undefined {
  if (!relations) return undefined
  return JSON.parse(JSON.stringify(relations)) as Relation[]
}

export function relationToDraft(relation?: Relation | null): RelationDraftState {
  if (!relation) {
    return createEmptyRelationDraft()
  }

  return {
    id: relation.id ?? '',
    type: relation.type ?? 'link',
    sourceRegionRef: relation.sourceRegionRef ?? '',
    targetRegionRef: relation.targetRegionRef ?? '',
    custom: relation.custom ?? '',
    comments: relation.comments ?? '',
    labels: (relation.labels ?? []).map(group => ({
      externalModel: group.externalModel,
      externalId: group.externalId,
      prefix: group.prefix,
      comments: group.comments,
      labels: (group.labels ?? []).map(label => ({
        value: label.value,
        type: label.type,
        comments: label.comments
      }))
    }))
  }
}

export function normalizeOptionalRelationString(value: string | null | undefined): string | undefined {
  if (typeof value !== 'string') return undefined
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : undefined
}

function normalizeRelationLabels(labelGroups?: Array<Partial<LabelsFormState> | Partial<Labels>>): Labels[] | undefined {
  if (!labelGroups?.length) return undefined

  const normalized = labelGroups.map((group) => {
    const labels = (group.labels ?? [])
      .map(label => ({
        value: normalizeOptionalRelationString(label.value) ?? '',
        type: normalizeOptionalRelationString(label.type),
        comments: normalizeOptionalRelationString(label.comments)
      }))
      .filter(label => label.value.length > 0)

    const externalModel = normalizeOptionalRelationString(group.externalModel)
    const externalId = normalizeOptionalRelationString(group.externalId)
    const prefix = normalizeOptionalRelationString(group.prefix)
    const comments = normalizeOptionalRelationString(group.comments)

    if (!externalModel && !externalId && !prefix && !comments && labels.length === 0) {
      return undefined
    }

    return {
      externalModel,
      externalId,
      prefix,
      comments,
      labels: labels.length > 0 ? labels : undefined
    } as Labels
  }).filter((group): group is Labels => Boolean(group))

  return normalized.length > 0 ? normalized : undefined
}

export function normalizeRelation(relation: Partial<RelationDraftState> | Partial<Relation>): Relation {
  return {
    id: normalizeOptionalRelationString(relation.id),
    type: normalizeOptionalRelationString(relation.type),
    sourceRegionRef: normalizeOptionalRelationString(relation.sourceRegionRef),
    targetRegionRef: normalizeOptionalRelationString(relation.targetRegionRef),
    custom: normalizeOptionalRelationString(relation.custom),
    comments: normalizeOptionalRelationString(relation.comments),
    labels: normalizeRelationLabels(relation.labels)
  }
}

export function createGeneratedRelationId(): string {
  return `rel_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

export function getRelationTypeOptions(currentType?: string | null) {
  const normalized = normalizeOptionalRelationString(currentType)
  if (!normalized || PAGE_RELATION_TYPE_OPTIONS.some(option => option.value === normalized)) {
    return [...PAGE_RELATION_TYPE_OPTIONS]
  }

  return [
    ...PAGE_RELATION_TYPE_OPTIONS,
    {
      label: `Existing (${normalized})`,
      value: normalized
    }
  ]
}

export function getRelationDisplayLabel(relation: Pick<Relation, 'id' | 'type'>): string {
  const type = normalizeOptionalRelationString(relation.type)
  const id = normalizeOptionalRelationString(relation.id)

  if (type && id) return `${type} • ${id}`
  if (type) return type
  if (id) return id
  return 'Untitled relation'
}

export function collectRegionIds(regions: Region[] | undefined): Set<string> {
  const ids = new Set<string>()

  function visit(items: Region[] | undefined) {
    for (const region of items ?? []) {
      ids.add(region.id)
      visit(region.regions)
    }
  }

  visit(regions)
  return ids
}

export function removeRelationsReferencingIds(relations: Relation[] | undefined, idsToRemove: Set<string>): Relation[] | undefined {
  if (!relations?.length) return undefined

  const filtered = relations.filter((relation) => {
    const source = normalizeOptionalRelationString(relation.sourceRegionRef)
    const target = normalizeOptionalRelationString(relation.targetRegionRef)
    return !source || !target || (!idsToRemove.has(source) && !idsToRemove.has(target))
  })

  return filtered.length > 0 ? cloneRelations(filtered) : undefined
}

export function filterRelationsByExistingRegionIds(relations: Relation[] | undefined, validRegionIds: Set<string>): Relation[] | undefined {
  if (!relations?.length) return undefined

  const filtered = relations.filter((relation) => {
    const source = normalizeOptionalRelationString(relation.sourceRegionRef)
    const target = normalizeOptionalRelationString(relation.targetRegionRef)
    return Boolean(source && target && validRegionIds.has(source) && validRegionIds.has(target))
  })

  return filtered.length > 0 ? cloneRelations(filtered) : undefined
}
