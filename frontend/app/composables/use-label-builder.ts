import type {
  AltoBlockType,
  AltoRole,
  LabelDefinition,
  LabelMapping,
  LabelScope,
  PageRegionType,
  PageTextType
} from '~/types/label-set'
import { createCanonicalRegionMappingSignatureFromLabel } from '@/utils/editor/page-label-mapping'

const PRESET_COLORS = ['#ef4444', '#f97316', '#f59e0b', '#84cc16', '#10b981', '#06b6d4', '#3b82f6', '#6366f1', '#8b5cf6', '#d946ef', '#f43f5e', '#64748b']
const PAGE_REGIONS = ['TextRegion', 'ImageRegion', 'LineDrawingRegion', 'GraphicRegion', 'TableRegion', 'ChartRegion', 'MapRegion', 'SeparatorRegion', 'MathsRegion', 'ChemRegion', 'MusicRegion', 'AdvertRegion', 'NoiseRegion', 'UnknownRegion']
const PAGE_TEXT_TYPES = ['paragraph', 'heading', 'caption', 'header', 'footer', 'page-number', 'drop-capital', 'credit', 'floating', 'signature-mark', 'catch-word', 'marginalia', 'footnote', 'footnote-continued', 'endnote', 'TOC-entry', 'list-label', 'other', 'custom']
const ALTO_BLOCK_TYPES = ['TextBlock', 'Illustration', 'GraphicalElement', 'ComposedBlock']

const meta = reactive({ name: 'My Custom Label Set', description: 'Optimized for historical document layout analysis', tags: [] as string[], altoEnabled: false, isSystem: false })
const filters = reactive({ region: true, line: true })
type EditableLabelMapping = {
  altoXml: {
    role: AltoRole
    tag: string
    blockType?: AltoBlockType
  }
  pageXml: {
    regionType?: PageRegionType
    textType?: PageTextType
    customSubType: string
    customKey: string
    customData: string
  }
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

function normalizeEditableLabel(label: LabelDefinition): EditableLabelDefinition {
  return {
    ...label,
    description: label.description ?? '',
    group: label.group ?? null,
    mapping: {
      altoXml: {
        role: label.mapping.altoXml.role,
        tag: label.mapping.altoXml.tag ?? '',
        ...(label.mapping.altoXml.blockType ? { blockType: label.mapping.altoXml.blockType } : {})
      },
      pageXml: {
        ...(label.mapping.pageXml.regionType ? { regionType: label.mapping.pageXml.regionType } : {}),
        ...(label.mapping.pageXml.textType ? { textType: label.mapping.pageXml.textType } : {}),
        customSubType: label.mapping.pageXml.customSubType ?? '',
        customKey: label.mapping.pageXml.customKey,
        customData: label.mapping.pageXml.customData ?? ''
      }
    }
  }
}

const createMapping = (name = '', scope: LabelScope = 'region'): LabelMapping => {
  const lowerName = name.toLowerCase()
  const customKey = 'structure'
  let customData = ''

  if (scope === 'line') {
    if (lowerName.includes('drop')) customData = 'type:drop-capital'
    return {
      altoXml: { role: 'TAGREFS', tag: name.replace(/\s+/g, '') || '' },
      pageXml: { customKey, customData }
    }
  }

  let pageRegion: PageRegionType = 'TextRegion'
  let pageText: PageTextType = 'paragraph'
  let altoBlock: AltoBlockType = 'TextBlock'
  const customSubType = ''

  if (lowerName.includes('image')) {
    pageRegion = 'ImageRegion'
    altoBlock = 'Illustration'
  } else if (lowerName.includes('table')) {
    pageRegion = 'TableRegion'
    altoBlock = 'ComposedBlock'
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
    altoXml: { blockType: altoBlock, role: 'TAGREFS', tag: name.replace(/\s+/g, '') || '' },
    pageXml: { regionType: pageRegion, textType: pageRegion === 'TextRegion' ? pageText : '', customSubType, customKey, customData }
  }
}

if (labels.value.length === 0) {
  labels.value = [
    normalizeEditableLabel({ id: '1', scope: 'region', name: 'Paragraph', description: 'Body text', color: '#3b82f6', hasText: true, isContainer: false, group: null, mapping: createMapping('Paragraph') }),
    normalizeEditableLabel({ id: '2', scope: 'line', name: 'Drop Cap Line', description: 'Line with drop capital', color: '#f43f5e', hasText: true, isContainer: false, group: null, mapping: createMapping('DropCapLine', 'line') })
  ]
}

export const useLabelBuilder = () => {
  const toggleSelection = (labelId: string) => {
    if (selectedLabelIds.value.has(labelId)) {
      selectedLabelIds.value.delete(labelId)
    } else {
      selectedLabelIds.value.add(labelId)
    }
  }

  const clearSelection = () => {
    selectedLabelIds.value.clear()
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
      mapping: createMapping(labelName)
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

  const duplicateLabel = (label: EditableLabelDefinition) => {
    const copy = normalizeEditableLabel(structuredClone(label))
    copy.id = Date.now().toString()
    copy.name = getUniqueLabelName(`${copy.name} (Copy)`)
    labels.value.push(copy)
    activeLabel.value = copy
  }

  const deleteLabel = (id: string) => {
    labels.value = labels.value.filter(l => l.id !== id)
    if (activeLabel.value?.id === id) activeLabel.value = null
  }

  const selectLabel = (l: BuilderEntry) => {
    if (isGroupMeta(l)) return
    activeLabel.value = l
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
      if (entry.scope !== 'region') continue
      const signature = createCanonicalRegionMappingSignatureFromLabel(entry)
      if (!signature) continue
      counts.set(signature, (counts.get(signature) || 0) + 1)
    }
    return counts
  })

  const getErrors = (label: BuilderEntry | null | undefined) => {
    const errors: { code: string, message: string }[] = []
    if (!label || isGroupMeta(label)) return errors

    const normalized = label.name.trim().toLowerCase()
    if (normalized && (nameCounts.value.get(normalized) || 0) > 1) {
      errors.push({ code: 'duplicateName', message: 'Label name must be unique within the label set.' })
    }

    const customKey = label.mapping?.pageXml?.customKey?.trim?.() ?? ''
    if (!customKey) {
      errors.push({ code: 'missingCustomKey', message: 'PAGE XML custom attribute key is required.' })
    }

    if (label.scope === 'region') {
      const pageXml = label.mapping.pageXml
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
    }

    if (label.scope === 'line') return errors

    const pageType = label.mapping.pageXml.regionType
    const altoType = label.mapping.altoXml?.blockType
    const effectiveHasText = pageType === 'TextRegion'
    const effectiveIsContainer = altoType === 'ComposedBlock'

    if (effectiveHasText) {
      if (pageType !== 'TextRegion') errors.push({ code: 'textConflict', message: `Text Content enabled but PAGE XML type is '${pageType}'.` })
      if (meta.altoEnabled && altoType !== 'TextBlock' && altoType !== 'ComposedBlock') errors.push({ code: 'textConflict', message: `Text Content enabled but ALTO XML type is '${altoType}'.` })
    }

    if (effectiveIsContainer && meta.altoEnabled) {
      if (altoType !== 'ComposedBlock') errors.push({ code: 'containerConflict', message: `Container enabled but ALTO XML type is '${altoType}'.` })
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
    if (!filters.region) res = res.filter(l => l.scope !== 'region')
    if (!filters.line) res = res.filter(l => l.scope !== 'line')
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
    meta, labels, activeLabel, filters, searchQuery, filteredLabels,
    PRESET_COLORS, PAGE_REGIONS, PAGE_TEXT_TYPES, ALTO_BLOCK_TYPES,
    createLabel, duplicateLabel, deleteLabel, selectLabel, createMapping,
    getErrors, hasError, totalErrors, optimizeColors,
    selectedLabelIds, toggleSelection, clearSelection, selectedLabels, canGroup,
    groupSelectedLabels, dissolveGroup, moveSelectedToGroup, mergeGroups
  }
}
