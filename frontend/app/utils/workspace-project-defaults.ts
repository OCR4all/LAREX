import type { ProjectDefaultKey } from '~/types/workspace-project-defaults'

export type WorkspaceProjectDefaultResourceKey = Exclude<ProjectDefaultKey, 'TEXT_INDICES'>

export interface WorkspaceProjectDefaultsSnapshot {
  codecId?: string | null
  labelSetId?: string | null
  dictionaryId?: string | null
  tagSetId?: string | null
  normalizationProfileId?: string | null
  validationRulesetId?: string | null
  defaultGtIndex: number
  defaultRecognitionIndices: number[]
}

export interface WorkspaceProjectDefaultsInput {
  codecId?: string | null
  labelSetId?: string | null
  dictionaryId?: string | null
  tagSetId?: string | null
  normalizationProfileId?: string | null
  validationRulesetId?: string | null
  defaultGtIndex?: number | null
  defaultRecognitionIndices?: number[] | null
}

export interface TextIndexDefaultsDraft {
  gtIndexInput?: string | number | null
  gtIndexUndefined?: boolean
  recognitionIndicesInput?: Array<string | number> | null
  recognitionIndicesUndefined?: boolean
}

export interface ResetTextIndexDefaults {
  gtIndexInput: string
  gtIndexUndefined: boolean
  recognitionIndicesInput: string[]
  recognitionIndicesUndefined: boolean
}

const resourceFields: Record<WorkspaceProjectDefaultResourceKey, keyof WorkspaceProjectDefaultsSnapshot> = {
  CODEC: 'codecId',
  LABEL_SET: 'labelSetId',
  DICTIONARY: 'dictionaryId',
  TAG_SET: 'tagSetId',
  NORMALIZATION_PROFILE: 'normalizationProfileId',
  VALIDATION_RULESET: 'validationRulesetId'
}

function normalizeId(value?: string | null): string | null {
  return value?.trim() || null
}

export function normalizeWorkspaceProjectDefaults(
  value?: WorkspaceProjectDefaultsInput | null
): WorkspaceProjectDefaultsSnapshot {
  return {
    codecId: normalizeId(value?.codecId),
    labelSetId: normalizeId(value?.labelSetId),
    dictionaryId: normalizeId(value?.dictionaryId),
    tagSetId: normalizeId(value?.tagSetId),
    normalizationProfileId: value?.normalizationProfileId ? normalizeId(value.normalizationProfileId) : null,
    validationRulesetId: value?.validationRulesetId ? normalizeId(value.validationRulesetId) : null,
    defaultGtIndex: Number.isInteger(value?.defaultGtIndex) && (value?.defaultGtIndex as number) >= 0
      ? value?.defaultGtIndex as number
      : 0,
    defaultRecognitionIndices: Array.isArray(value?.defaultRecognitionIndices) && value.defaultRecognitionIndices.length > 0
      ? [...new Set(value.defaultRecognitionIndices.filter(Number.isInteger))].sort((left, right) => left - right)
      : [1]
  }
}

export function workspaceResourceDefault(
  defaults: WorkspaceProjectDefaultsSnapshot,
  key: WorkspaceProjectDefaultResourceKey
): string | null {
  return normalizeId(defaults[resourceFields[key]] as string | null | undefined)
}

export function resetWorkspaceResourceDefault(
  defaults: WorkspaceProjectDefaultsSnapshot,
  key: WorkspaceProjectDefaultResourceKey
): string | null {
  return workspaceResourceDefault(defaults, key)
}

export function formatWorkspaceResourceDefault(
  id: string | null | undefined,
  options: Array<{ label: string, value: string }>
): string {
  const normalized = normalizeId(id)
  if (!normalized) return 'No workspace default'
  return options.find(option => option.value === normalized)?.label || 'Unavailable resource'
}

export function formatWorkspaceTextIndexDefault(defaults: WorkspaceProjectDefaultsSnapshot): string {
  const recognition = defaults.defaultRecognitionIndices
    .filter(index => index !== -1)
    .join(', ')
  const undefinedRecognition = defaults.defaultRecognitionIndices.includes(-1)
  const recognitionLabel = undefinedRecognition
    ? recognition ? `Undefined, ${recognition}` : 'Undefined'
    : recognition || 'None'
  return `GT ${defaults.defaultGtIndex}; Recognition ${recognitionLabel}`
}

export function resourceMatchesWorkspaceDefault(
  projectValue: string | null | undefined,
  workspaceValue: string | null | undefined
): boolean {
  return normalizeId(projectValue) === normalizeId(workspaceValue)
}

function parseIndex(value: string | number): number | null {
  const stringValue = String(value).trim()
  if (!/^\d+$/.test(stringValue)) return null
  return Number.parseInt(stringValue, 10)
}

function normalizeDraftIndices(values?: Array<string | number> | null, includeUndefined = false): number[] {
  const parsed = (values ?? [])
    .map(parseIndex)
    .filter((value): value is number => value !== null)
  if (includeUndefined) parsed.push(-1)
  return [...new Set(parsed)].sort((left, right) => left - right)
}

export function textIndicesMatchWorkspaceDefault(
  draft: TextIndexDefaultsDraft,
  defaults: WorkspaceProjectDefaultsSnapshot
): boolean {
  const gtIndex = draft.gtIndexUndefined === true ? null : parseIndex(String(draft.gtIndexInput ?? ''))
  const recognitionIndices = normalizeDraftIndices(draft.recognitionIndicesInput, draft.recognitionIndicesUndefined === true)
  const expectedGtIndex = defaults.defaultGtIndex
  return gtIndex === expectedGtIndex && sameNumbers(recognitionIndices, defaults.defaultRecognitionIndices)
}

export function resetTextIndexDefaults(defaults: WorkspaceProjectDefaultsSnapshot): ResetTextIndexDefaults {
  return {
    gtIndexInput: String(defaults.defaultGtIndex),
    gtIndexUndefined: false,
    recognitionIndicesInput: defaults.defaultRecognitionIndices
      .filter(index => index !== -1)
      .map(index => String(index)),
    recognitionIndicesUndefined: defaults.defaultRecognitionIndices.includes(-1)
  }
}

function sameNumbers(left: number[], right: number[]): boolean {
  const normalizedLeft = [...left].sort((a, b) => a - b)
  const normalizedRight = [...right].sort((a, b) => a - b)
  return normalizedLeft.length === normalizedRight.length
    && normalizedLeft.every((value, index) => value === normalizedRight[index])
}

export function changedProjectDefaultKeys(
  before: WorkspaceProjectDefaultsSnapshot,
  after: WorkspaceProjectDefaultsSnapshot
): ProjectDefaultKey[] {
  const changed: ProjectDefaultKey[] = []
  const resources: Array<[ProjectDefaultKey, string | null | undefined, string | null | undefined]> = [
    ['CODEC', before.codecId, after.codecId],
    ['LABEL_SET', before.labelSetId, after.labelSetId],
    ['DICTIONARY', before.dictionaryId, after.dictionaryId],
    ['TAG_SET', before.tagSetId, after.tagSetId],
    ['NORMALIZATION_PROFILE', before.normalizationProfileId, after.normalizationProfileId],
    ['VALIDATION_RULESET', before.validationRulesetId, after.validationRulesetId]
  ]
  for (const [key, current, next] of resources) {
    if (normalizeId(current) !== normalizeId(next)) changed.push(key)
  }
  if (before.defaultGtIndex !== after.defaultGtIndex
    || !sameNumbers(before.defaultRecognitionIndices, after.defaultRecognitionIndices)) {
    changed.push('TEXT_INDICES')
  }
  return changed
}
