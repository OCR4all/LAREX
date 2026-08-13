import { computed, reactive, ref } from 'vue'
import {
  CANONICAL_PAGE_CUSTOM_KEY,
  type LabelDefinition,
  type LabelMapping,
  type PageRegionType,
  type PageTextType
} from '~/types/label-set'
import { createCanonicalRegionMappingSignatureFromLabel } from '@/utils/editor/page-label-mapping'

const PRESET_COLORS = ['#ef4444', '#f97316', '#f59e0b', '#84cc16', '#10b981', '#06b6d4', '#3b82f6', '#6366f1', '#8b5cf6', '#d946ef', '#f43f5e', '#64748b']
const PAGE_REGIONS = ['TextRegion', 'ImageRegion', 'LineDrawingRegion', 'GraphicRegion', 'TableRegion', 'ChartRegion', 'MapRegion', 'SeparatorRegion', 'MathsRegion', 'ChemRegion', 'MusicRegion', 'AdvertRegion', 'NoiseRegion', 'UnknownRegion']
const PAGE_TEXT_TYPES = ['paragraph', 'heading', 'caption', 'header', 'footer', 'page-number', 'drop-capital', 'credit', 'floating', 'signature-mark', 'catch-word', 'marginalia', 'footnote', 'footnote-continued', 'endnote', 'TOC-entry', 'list-label', 'other', 'custom']

const meta = reactive({
  name: 'My Custom Label Set',
  description: 'Optimized for historical document layout analysis',
  tags: [] as string[],
  isSystem: false,
  defaultLabelId: null as string | null
})
export type EditablePageXmlMapping = {
  regionType?: PageRegionType
  textType?: PageTextType
  customSubType: string
  customKey: string
  customData: string
}
type EditableLabelMapping = {
  pageXml: EditablePageXmlMapping
}

export interface EditableLabelDefinition extends Omit<LabelDefinition, 'description' | 'group' | 'mapping'> {
  description: string
  group: string | null
  mapping: EditableLabelMapping
}

export interface GroupMeta {
  id: string
  name: string
  isGroup: true
}

export type BuilderEntry = EditableLabelDefinition | GroupMeta

export function isGroupMeta(entry: BuilderEntry): entry is GroupMeta {
  return 'isGroup' in entry && entry.isGroup === true
}

export function isEditableLabelDefinition(entry: BuilderEntry): entry is EditableLabelDefinition {
  return !isGroupMeta(entry)
}

const labels = ref<BuilderEntry[]>([])
const activeLabel = ref<EditableLabelDefinition | null>(null)
const searchQuery = ref('')
const selectedLabelIds = ref<Set<string>>(new Set())
const lastSelectedLabelId = ref<string | null>(null)
const savedStateSnapshot = ref<string | null>(null)

interface LabelBuilderMetaLike {
  name: string
  description?: string | null
  tags?: string[] | null
  defaultLabelId?: string | null
}

export function createLabelBuilderStateSnapshot(metaState: LabelBuilderMetaLike, entries: BuilderEntry[]): string {
  return JSON.stringify({
    meta: {
      name: metaState.name,
      description: metaState.description ?? '',
      tags: [...(metaState.tags ?? [])],
      defaultLabelId: metaState.defaultLabelId ?? null
    },
    labels: entries
      .filter(isEditableLabelDefinition)
      .map(label => ({
        id: label.id,
        scope: label.scope,
        name: label.name,
        description: label.description,
        color: label.color,
        hasText: label.hasText,
        isContainer: label.isContainer,
        group: label.group,
        mapping: {
          pageXml: {
            regionType: label.mapping.pageXml.regionType ?? null,
            textType: label.mapping.pageXml.textType ?? null,
            customSubType: label.mapping.pageXml.customSubType,
            customKey: label.mapping.pageXml.customKey,
            customData: label.mapping.pageXml.customData
          }
        }
      }))
  })
}

export function normalizeEditableLabel(label: LabelDefinition): EditableLabelDefinition {
  return {
    ...label,
    description: label.description ?? '',
    group: label.group ?? null,
    mapping: {
      pageXml: {
        ...(label.mapping.pageXml.regionType ? { regionType: label.mapping.pageXml.regionType } : {}),
        ...(label.mapping.pageXml.textType ? { textType: label.mapping.pageXml.textType } : {}),
        customSubType: label.mapping.pageXml.customSubType ?? '',
        customKey: CANONICAL_PAGE_CUSTOM_KEY,
        customData: ''
      }
    }
  }
}

const createMapping = (name = ''): LabelMapping => {
  const lowerName = name.toLowerCase()
  const customKey = CANONICAL_PAGE_CUSTOM_KEY
  const customData = ''

  let pageRegion: PageRegionType = 'TextRegion'
  let pageText: PageTextType = 'paragraph'
  const customSubType = ''

  if (lowerName.includes('image')) {
    pageRegion = 'ImageRegion'
  } else if (lowerName.includes('table')) {
    pageRegion = 'TableRegion'
  } else if (lowerName.includes('music')) {
    pageRegion = 'MusicRegion'
  } else if (lowerName.includes('header')) {
    pageText = 'header'
  } else if (lowerName.includes('footer')) {
    pageText = 'footer'
  } else if (lowerName.includes('caption')) {
    pageText = 'caption'
  } else if (lowerName.includes('margin')) {
    pageText = 'marginalia'
  } else if (lowerName.includes('heading') || lowerName.includes('title')) {
    pageText = 'heading'
  }

  return {
    pageXml: { regionType: pageRegion, textType: pageRegion === 'TextRegion' ? pageText : '', customSubType, customKey, customData }
  }
}

export function createNextFreeLabelMapping(entries: BuilderEntry[], fallbackLabelName: string): LabelMapping {
  const textTypes = PAGE_TEXT_TYPES as PageTextType[]
  const usedTextTypes = new Set(entries
    .filter(isEditableLabelDefinition)
    .filter(label => label.mapping.pageXml.regionType === 'TextRegion')
    .map(label => label.mapping.pageXml.textType)
    .filter((textType): textType is PageTextType => Boolean(textType)))
  const nextTextType = textTypes.find(textType => textType !== 'custom' && !usedTextTypes.has(textType))

  return {
    pageXml: {
      regionType: 'TextRegion',
      textType: nextTextType ?? 'custom',
      customSubType: nextTextType ? '' : fallbackLabelName,
      customKey: CANONICAL_PAGE_CUSTOM_KEY,
      customData: ''
    }
  }
}

if (labels.value.length === 0) {
  labels.value = [
    normalizeEditableLabel({ id: '1', scope: 'region', name: 'Paragraph', description: 'Body text', color: '#3b82f6', hasText: true, isContainer: false, group: null, mapping: createMapping('Paragraph') })
  ]
}

export function moveBuilderLabel(
  entries: BuilderEntry[],
  labelId: string,
  targetGroup: string | null,
  targetIndex: number
): BuilderEntry[] {
  const next = [...entries]
  const sourceIndex = next.findIndex(entry => !isGroupMeta(entry) && entry.id === labelId)
  if (sourceIndex < 0) return entries

  const [movedEntry] = next.splice(sourceIndex, 1)
  if (!movedEntry || isGroupMeta(movedEntry)) return entries
  movedEntry.group = targetGroup

  const targetLabels = next.filter((entry): entry is EditableLabelDefinition =>
    !isGroupMeta(entry) && (entry.group ?? null) === targetGroup
  )
  const insertionIndex = Math.max(0, Math.min(targetIndex, targetLabels.length))
  const anchor = targetLabels[insertionIndex]

  if (anchor) {
    next.splice(next.indexOf(anchor), 0, movedEntry)
    return next
  }

  const lastTarget = targetLabels.at(-1)
  if (lastTarget) {
    next.splice(next.indexOf(lastTarget) + 1, 0, movedEntry)
    return next
  }

  const firstGroupMetaIndex = next.findIndex(isGroupMeta)
  next.splice(firstGroupMetaIndex < 0 ? next.length : firstGroupMetaIndex, 0, movedEntry)
  return next
}

export function moveBuilderLabelByOffset(
  entries: BuilderEntry[],
  labelId: string,
  offset: -1 | 1
): BuilderEntry[] {
  const label = entries.find((entry): entry is EditableLabelDefinition =>
    isEditableLabelDefinition(entry) && entry.id === labelId
  )
  if (!label) return entries

  const group = label.group ?? null
  const siblings = entries.filter((entry): entry is EditableLabelDefinition =>
    isEditableLabelDefinition(entry) && (entry.group ?? null) === group
  )
  const currentIndex = siblings.findIndex(entry => entry.id === labelId)
  const targetIndex = currentIndex + offset
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= siblings.length) return entries

  return moveBuilderLabel(entries, labelId, group, targetIndex)
}

function getOrderedBuilderGroupIds(entries: BuilderEntry[]): string[] {
  const ids: string[] = []
  for (const entry of entries) {
    const groupId = isGroupMeta(entry) ? entry.id : entry.group
    if (groupId && !ids.includes(groupId)) ids.push(groupId)
  }
  return ids
}

export function moveBuilderGroup(
  entries: BuilderEntry[],
  groupId: string,
  targetIndex: number
): BuilderEntry[] {
  const groupIds = getOrderedBuilderGroupIds(entries)
  const sourceIndex = groupIds.indexOf(groupId)
  if (sourceIndex < 0) return entries

  const clampedTargetIndex = Math.max(0, Math.min(targetIndex, groupIds.length - 1))
  if (sourceIndex === clampedTargetIndex) return entries

  groupIds.splice(sourceIndex, 1)
  groupIds.splice(clampedTargetIndex, 0, groupId)

  const labelsByGroup = new Map<string, EditableLabelDefinition[]>()
  const ungrouped: EditableLabelDefinition[] = []
  const groupMetas = new Map<string, GroupMeta>()
  for (const entry of entries) {
    if (isGroupMeta(entry)) {
      groupMetas.set(entry.id, entry)
    } else if (entry.group) {
      const groupLabels = labelsByGroup.get(entry.group) ?? []
      groupLabels.push(entry)
      labelsByGroup.set(entry.group, groupLabels)
    } else {
      ungrouped.push(entry)
    }
  }

  const orderedLabels = groupIds.flatMap(id => labelsByGroup.get(id) ?? [])
  const orderedMetas = groupIds.flatMap((id) => {
    const metaEntry = groupMetas.get(id)
    return metaEntry ? [metaEntry] : []
  })
  return [...orderedLabels, ...ungrouped, ...orderedMetas]
}

export function moveBuilderGroupByOffset(
  entries: BuilderEntry[],
  groupId: string,
  offset: -1 | 1
): BuilderEntry[] {
  const groupIds = getOrderedBuilderGroupIds(entries)
  const currentIndex = groupIds.indexOf(groupId)
  const targetIndex = currentIndex + offset
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= groupIds.length) return entries
  return moveBuilderGroup(entries, groupId, targetIndex)
}

export function applyPageRegionTypeChange(pageXml: EditablePageXmlMapping, regionType: PageRegionType): void {
  const changed = pageXml.regionType !== regionType
  pageXml.regionType = regionType
  pageXml.customKey = CANONICAL_PAGE_CUSTOM_KEY
  pageXml.customData = ''

  if (!changed) {
    if (regionType !== 'TextRegion') pageXml.textType = undefined
    return
  }

  pageXml.textType = regionType === 'TextRegion' ? 'paragraph' : undefined
  pageXml.customSubType = ''
  pageXml.customData = ''
}

export function applyPageTextTypeChange(pageXml: EditablePageXmlMapping, textType: PageTextType | undefined): void {
  const changed = pageXml.textType !== textType
  pageXml.textType = textType
  pageXml.customKey = CANONICAL_PAGE_CUSTOM_KEY
  pageXml.customData = ''
  if (!changed) return

  pageXml.customSubType = ''
  pageXml.customData = ''
}

export const useLabelBuilder = () => {
  const currentStateSnapshot = computed(() => createLabelBuilderStateSnapshot(meta, labels.value))
  const isDirty = computed(() => savedStateSnapshot.value !== null && savedStateSnapshot.value !== currentStateSnapshot.value)

  const markSavedState = () => {
    savedStateSnapshot.value = currentStateSnapshot.value
  }

  const toggleSelection = (labelId: string) => {
    if (selectedLabelIds.value.has(labelId)) {
      selectedLabelIds.value.delete(labelId)
    } else {
      selectedLabelIds.value.add(labelId)
    }
    lastSelectedLabelId.value = labelId
  }

  const selectLabelRange = (labelId: string, orderedLabelIds: string[], extendRange: boolean) => {
    if (!extendRange || !lastSelectedLabelId.value) {
      toggleSelection(labelId)
      return
    }

    const anchorIndex = orderedLabelIds.indexOf(lastSelectedLabelId.value)
    const targetIndex = orderedLabelIds.indexOf(labelId)
    if (anchorIndex === -1 || targetIndex === -1) {
      toggleSelection(labelId)
      return
    }

    const start = Math.min(anchorIndex, targetIndex)
    const end = Math.max(anchorIndex, targetIndex)
    for (let index = start; index <= end; index++) {
      const id = orderedLabelIds[index]
      if (id) selectedLabelIds.value.add(id)
    }
    lastSelectedLabelId.value = labelId
  }

  const clearSelection = () => {
    selectedLabelIds.value.clear()
    lastSelectedLabelId.value = null
  }

  const selectedLabels = computed(() => {
    return labels.value.filter((l): l is EditableLabelDefinition => isEditableLabelDefinition(l) && selectedLabelIds.value.has(l.id))
  })

  const canGroup = computed(() => {
    return selectedLabelIds.value.size >= 2
  })

  const getUniqueLabelName = (baseName: string, ignoreId?: string) => {
    const trimmed = baseName.trim() || 'Label'
    const existing = new Set<string>()
    for (const label of labels.value) {
      if (isGroupMeta(label)) continue
      if (ignoreId && label.id === ignoreId) continue
      existing.add(label.name.trim().toLowerCase())
    }
    const normalized = trimmed.toLowerCase()
    if (!existing.has(normalized)) return trimmed
    let index = 2
    while (existing.has(`${trimmed} (${index})`.toLowerCase())) {
      index++
    }
    return `${trimmed} (${index})`
  }

  const getUniqueGroupName = (baseName: string, ignoreName?: string) => {
    const trimmed = baseName.trim() || 'Group'
    const existing = new Set<string>()
    for (const label of labels.value) {
      if (isGroupMeta(label)) {
        if (label.name !== ignoreName) existing.add(label.name)
      }
      if (!isGroupMeta(label) && label.group && label.group !== ignoreName) {
        existing.add(label.group)
      }
    }
    if (!existing.has(trimmed)) return trimmed
    let index = 2
    while (existing.has(`${trimmed} (${index})`)) {
      index++
    }
    return `${trimmed} (${index})`
  }

  const moveSelectedToGroup = (targetGroupId: string) => {
    if (!targetGroupId) return
    if (selectedLabelIds.value.size === 0) return
    for (const label of selectedLabels.value) {
      label.group = targetGroupId
    }
    clearSelection()
  }

  const mergeGroups = (targetGroupId: string, sourceGroupId: string, newName?: string) => {
    if (!targetGroupId || !sourceGroupId || targetGroupId === sourceGroupId) return
    let effectiveTargetGroupId = targetGroupId
    for (const label of labels.value) {
      if (!isGroupMeta(label) && label.group === sourceGroupId) {
        label.group = targetGroupId
      }
    }
    const targetMeta = labels.value.find((l): l is GroupMeta => isGroupMeta(l) && l.id === effectiveTargetGroupId) ?? null
    if (targetMeta && newName) {
      const uniqueName = getUniqueGroupName(newName, effectiveTargetGroupId)
      for (const label of labels.value) {
        if (!isGroupMeta(label) && label.group === effectiveTargetGroupId) {
          label.group = uniqueName
        }
      }
      targetMeta.id = uniqueName
      targetMeta.name = uniqueName
      effectiveTargetGroupId = uniqueName
    }
    labels.value = labels.value.filter(l => !isGroupMeta(l) || l.id !== sourceGroupId)
    if (activeLabel.value?.id === sourceGroupId) {
      activeLabel.value = null
    }
  }

  const createLabel = () => {
    const labelName = getUniqueLabelName('New Label')
    const newLabel = normalizeEditableLabel({
      id: Date.now().toString(),
      scope: 'region',
      name: labelName,
      description: '',
      color: PRESET_COLORS[Math.floor(Math.random() * PRESET_COLORS.length)] ?? '#3b82f6',
      hasText: true,
      isContainer: false,
      group: null,
      mapping: createNextFreeLabelMapping(labels.value, labelName)
    })
    labels.value.push(newLabel)
    activeLabel.value = newLabel
  }

  const groupSelectedLabels = (groupName: string) => {
    if (!canGroup.value) return null

    const groupId = getUniqueGroupName(groupName)
    for (const label of selectedLabels.value) {
      label.group = groupId
    }
    const groupMeta: GroupMeta = {
      id: groupId,
      name: groupId,
      isGroup: true
    }
    labels.value.push(groupMeta)
    clearSelection()
    return groupId
  }

  const dissolveGroup = (groupId: string) => {
    for (const label of labels.value) {
      if (!isGroupMeta(label) && label.group === groupId) {
        label.group = null
      }
    }
    labels.value = labels.value.filter(l => l.id !== groupId)
    if (activeLabel.value?.id === groupId) {
      activeLabel.value = null
    }
  }

  const deleteLabel = (id: string) => {
    labels.value = labels.value.filter(l => l.id !== id)
    if (meta.defaultLabelId === id) meta.defaultLabelId = null
    if (activeLabel.value?.id === id) activeLabel.value = null
  }

  const deleteSelectedLabels = () => {
    if (selectedLabelIds.value.size === 0) return
    const ids = new Set(selectedLabelIds.value)
    labels.value = labels.value.filter(label => isGroupMeta(label) || !ids.has(label.id))
    if (meta.defaultLabelId && ids.has(meta.defaultLabelId)) meta.defaultLabelId = null
    if (activeLabel.value && ids.has(activeLabel.value.id)) {
      activeLabel.value = null
    }
    clearSelection()
  }

  const moveLabel = (labelId: string, targetGroup: string | null, targetIndex: number) => {
    labels.value = moveBuilderLabel(labels.value, labelId, targetGroup, targetIndex)
  }

  const moveLabelByOffset = (labelId: string, offset: -1 | 1): boolean => {
    const next = moveBuilderLabelByOffset(labels.value, labelId, offset)
    if (next === labels.value) return false
    labels.value = next
    return true
  }

  const moveGroup = (groupId: string, targetIndex: number): boolean => {
    const next = moveBuilderGroup(labels.value, groupId, targetIndex)
    if (next === labels.value) return false
    labels.value = next
    return true
  }

  const moveGroupByOffset = (groupId: string, offset: -1 | 1): boolean => {
    const next = moveBuilderGroupByOffset(labels.value, groupId, offset)
    if (next === labels.value) return false
    labels.value = next
    return true
  }

  const selectLabel = (l: BuilderEntry) => {
    if (isGroupMeta(l)) return
    activeLabel.value = l
  }

  const setDefaultLabel = (labelId: string | null) => {
    if (labelId === null) {
      meta.defaultLabelId = null
      return
    }
    if (labels.value.some(entry => isEditableLabelDefinition(entry) && entry.id === labelId)) {
      meta.defaultLabelId = labelId
    }
  }

  const nameCounts = computed(() => {
    const counts = new Map<string, number>()
    for (const label of labels.value) {
      if (isGroupMeta(label)) continue
      const normalized = label.name.trim().toLowerCase()
      if (!normalized) continue
      counts.set(normalized, (counts.get(normalized) || 0) + 1)
    }
    return counts
  })

  const regionMappingSignatureCounts = computed(() => {
    const counts = new Map<string, number>()
    for (const entry of labels.value) {
      if (isGroupMeta(entry)) continue
      const signature = createCanonicalRegionMappingSignatureFromLabel(entry)
      if (!signature) continue
      counts.set(signature, (counts.get(signature) || 0) + 1)
    }
    return counts
  })

  const getErrors = (label: BuilderEntry | null | undefined) => {
    const errors: { code: string, message: string }[] = []
    if (!label || isGroupMeta(label)) return errors

    const trimmedName = label.name.trim()
    const normalized = trimmedName.toLowerCase()
    if (!trimmedName) {
      errors.push({ code: 'missingName', message: 'Label name is required.' })
    } else if (label.name.length > 255) {
      errors.push({ code: 'nameTooLong', message: 'Label name must not exceed 255 characters.' })
    } else if ((nameCounts.value.get(normalized) || 0) > 1) {
      errors.push({ code: 'duplicateName', message: 'Label name must be unique within the label set.' })
    }

    if ((label.description?.length ?? 0) > 10_000) {
      errors.push({ code: 'descriptionTooLong', message: 'Description must not exceed 10,000 characters.' })
    }

    if (!/^#[\da-f]{6}$/i.test(label.color)) {
      errors.push({ code: 'invalidColor', message: 'Color must be a six-digit hex value such as #3B82F6.' })
    }

    const pageXml = label.mapping.pageXml
    if (!pageXml.regionType) {
      errors.push({ code: 'missingRegionType', message: 'PAGE XML region type is required.' })
    }
    const signature = createCanonicalRegionMappingSignatureFromLabel(label)
    if (signature && (regionMappingSignatureCounts.value.get(signature) || 0) > 1) {
      errors.push({
        code: 'duplicatePageMapping',
        message: 'Another region label uses the same PAGE XML mapping. Region mappings must be unique.'
      })
    }

    if (pageXml.regionType === 'TextRegion' && pageXml.textType === 'custom' && !(pageXml.customSubType || '').trim()) {
      errors.push({
        code: 'missingCustomSubType',
        message: 'TextRegion with subtype "custom" requires a custom subtype value.'
      })
    }

    return errors
  }

  const hasError = (l: BuilderEntry | null | undefined, c: string) => getErrors(l).some(e => e.code === c)
  const totalErrors = computed(() => labels.value
    .filter((l): l is EditableLabelDefinition => isEditableLabelDefinition(l))
    .reduce((acc, l) => acc + getErrors(l).length, 0))

  const filteredLabels = computed(() => {
    const groupMetas = labels.value.filter(isGroupMeta)
    let res = labels.value.filter((l): l is EditableLabelDefinition => isEditableLabelDefinition(l))
    if (searchQuery.value) {
      const lower = searchQuery.value.toLowerCase()
      res = res.filter(l => l.name.toLowerCase().includes(lower) || (l.description && l.description.toLowerCase().includes(lower)))
    }
    return [...groupMetas, ...res]
  })

  const hslToHex = (h: number, s: number, l: number) => {
    l /= 100
    const a = s * Math.min(l, 1 - l) / 100
    const f = (n: number) => {
      const k = (n + h / 30) % 12
      const color = l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1)
      return Math.round(255 * color).toString(16).padStart(2, '0')
    }
    return `#${f(0)}${f(8)}${f(4)}`
  }

  const optimizeColors = () => {
    const count = labels.value.length
    labels.value.forEach((label, index) => {
      if (isGroupMeta(label)) return
      const hue = (index * (360 / count)) % 360
      label.color = hslToHex(hue, 70, 50)
    })
  }

  return {
    meta, labels, activeLabel, searchQuery, filteredLabels,
    PRESET_COLORS, PAGE_REGIONS, PAGE_TEXT_TYPES,
    createLabel, deleteLabel, deleteSelectedLabels, selectLabel, setDefaultLabel, createMapping,
    getErrors, hasError, totalErrors, optimizeColors,
    selectedLabelIds, toggleSelection, selectLabelRange, clearSelection, selectedLabels, canGroup,
    groupSelectedLabels, dissolveGroup, moveSelectedToGroup, mergeGroups,
    moveLabel, moveLabelByOffset, moveGroup, moveGroupByOffset,
    isDirty, markSavedState
  }
}
