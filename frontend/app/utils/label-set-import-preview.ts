import { CANONICAL_PAGE_CUSTOM_KEY, type LabelDefinition, type LabelSetCreateOrUpdateRequest, type PageRegionType, type PageTextType } from '@/types/label-set'
import { createCanonicalRegionMappingSignatureFromLabel } from '@/utils/editor/page-label-mapping'

export interface LabelSetImportIssue {
  level: 'error' | 'warning'
  message: string
  labelName?: string
}

export interface LabelSetImportComparison {
  added: number
  removed: number
  changed: number
  orderChanged: boolean
  metadataChanged: boolean
}

export interface ImportedLabelSetPreview {
  name: string
  labelCount: number
  groupCount: number
  labelNames: string[]
  nameConflict: boolean
  issues: LabelSetImportIssue[]
  comparison: LabelSetImportComparison | null
}

export interface LabelSetImportPreview {
  fileName: string
  labelSets: ImportedLabelSetPreview[]
  otherResources: Array<{ type: string, name: string }>
  issues: LabelSetImportIssue[]
  canImport: boolean
}

interface PreviewOptions {
  fileName: string
  existingNames?: string[]
  current?: LabelSetCreateOrUpdateRequest | null
}

const PAGE_REGIONS = new Set<PageRegionType>([
  'TextRegion', 'ImageRegion', 'LineDrawingRegion', 'GraphicRegion', 'TableRegion',
  'ChartRegion', 'MapRegion', 'SeparatorRegion', 'MathsRegion', 'ChemRegion',
  'MusicRegion', 'AdvertRegion', 'NoiseRegion', 'UnknownRegion'
])
const PAGE_TEXT_TYPES = new Set<PageTextType>([
  '', 'paragraph', 'heading', 'caption', 'header', 'footer', 'page-number',
  'drop-capital', 'credit', 'floating', 'signature-mark', 'catch-word',
  'marginalia', 'footnote', 'footnote-continued', 'endnote', 'TOC-entry',
  'list-label', 'other', 'custom'
])

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function comparableLabel(label: LabelDefinition): string {
  return JSON.stringify({
    id: label.id,
    scope: label.scope,
    name: label.name,
    description: label.description ?? null,
    color: label.color,
    hasText: label.mapping.pageXml.regionType === 'TextRegion',
    isContainer: label.isContainer,
    group: label.group ?? null,
    mapping: {
      pageXml: {
        regionType: label.mapping.pageXml.regionType ?? null,
        textType: label.mapping.pageXml.textType || null,
        customSubType: label.mapping.pageXml.customSubType ?? '',
        customKey: label.mapping.pageXml.customKey,
        customData: label.mapping.pageXml.customData ?? ''
      }
    }
  })
}

function compareWithCurrent(
  incomingMeta: Record<string, unknown>,
  incoming: LabelDefinition[],
  current: LabelSetCreateOrUpdateRequest | null | undefined
): LabelSetImportComparison | null {
  if (!current || asString(incomingMeta.name).trim().toLowerCase() !== current.meta.name.trim().toLowerCase()) return null

  const currentById = new Map(current.labels.map(label => [label.id, label]))
  const incomingById = new Map(incoming.map(label => [label.id, label]))
  const added = incoming.filter(label => !currentById.has(label.id)).length
  const removed = current.labels.filter(label => !incomingById.has(label.id)).length
  const changed = incoming.filter((label) => {
    const existing = currentById.get(label.id)
    return existing ? comparableLabel(existing) !== comparableLabel(label) : false
  }).length

  const sharedIds = new Set(incoming.filter(label => currentById.has(label.id)).map(label => label.id))
  const incomingOrder = incoming.filter(label => sharedIds.has(label.id)).map(label => label.id)
  const currentOrder = current.labels.filter(label => sharedIds.has(label.id)).map(label => label.id)
  const orderChanged = JSON.stringify(incomingOrder) !== JSON.stringify(currentOrder)
  const metadataChanged = JSON.stringify({
    description: incomingMeta.description ?? '',
    tags: Array.isArray(incomingMeta.tags) ? incomingMeta.tags : [],
    defaultLabelId: asString(incomingMeta.defaultLabelId) || null
  }) !== JSON.stringify({
    description: current.meta.description ?? '',
    tags: current.meta.tags ?? [],
    defaultLabelId: current.meta.defaultLabelId ?? null
  })

  return { added, removed, changed, orderChanged, metadataChanged }
}

function inspectLabelSetPayload(
  payloadValue: unknown,
  existingNames: Set<string>,
  current: LabelSetCreateOrUpdateRequest | null | undefined
): ImportedLabelSetPreview {
  const payload = asRecord(payloadValue)
  const meta = asRecord(payload?.meta)
  const rawLabels = Array.isArray(payload?.labels) ? payload.labels : []
  const name = asString(meta?.name).trim() || 'Unnamed label set'
  const issues: LabelSetImportIssue[] = []
  const labels: LabelDefinition[] = []
  const ids = new Set<string>()
  const names = new Set<string>()
  const mappingSignatures = new Map<string, string>()

  if (!payload) issues.push({ level: 'error', message: 'Label set payload must be a JSON object.' })
  if (!meta || !asString(meta.name).trim()) issues.push({ level: 'error', message: 'Label set name is required.' })
  if (!Array.isArray(payload?.labels)) issues.push({ level: 'error', message: 'Label set labels must be an array.' })

  rawLabels.forEach((rawLabel, index) => {
    const record = asRecord(rawLabel)
    const fallbackName = `Label ${index + 1}`
    const labelName = asString(record?.name).trim() || fallbackName
    const labelIssues: string[] = []
    const id = asString(record?.id).trim()
    const scope = asString(record?.scope)
    const mapping = asRecord(record?.mapping)
    const pageXml = asRecord(mapping?.pageXml)
    const regionType = asString(pageXml?.regionType) as PageRegionType
    const textType = asString(pageXml?.textType) as PageTextType
    const customSubType = pageXml?.customSubType == null ? '' : asString(pageXml.customSubType)
    const importedCustomKey = asString(pageXml?.customKey)
    const importedCustomData = pageXml?.customData == null ? '' : asString(pageXml.customData)
    const color = asString(record?.color)

    if (!record) labelIssues.push('entry must be an object')
    if (!id) labelIssues.push('ID is required')
    else if (ids.has(id)) labelIssues.push(`duplicate ID "${id}"`)
    if (!asString(record?.name).trim()) labelIssues.push('name is required')
    else if (names.has(labelName.toLowerCase())) labelIssues.push(`duplicate name "${labelName}"`)
    if (scope !== 'region') labelIssues.push('scope must be "region"')
    if (!/^#[\da-f]{6}$/i.test(color)) labelIssues.push('color must be a six-digit hex value')
    if (!PAGE_REGIONS.has(regionType)) labelIssues.push('PAGE region type is invalid or missing')
    if (textType && !PAGE_TEXT_TYPES.has(textType)) labelIssues.push(`PAGE text subtype "${textType}" is invalid`)
    if (regionType !== 'TextRegion' && textType) labelIssues.push('non-text regions cannot define a text subtype')
    if (regionType === 'TextRegion' && textType === 'custom' && !customSubType.trim()) labelIssues.push('custom TextRegion requires a custom subtype')
    if (importedCustomKey.trim() !== CANONICAL_PAGE_CUSTOM_KEY) {
      issues.push({
        level: 'warning',
        labelName,
        message: `PAGE custom block will be normalized to "${CANONICAL_PAGE_CUSTOM_KEY}"`
      })
    }
    if (importedCustomData.trim()) {
      issues.push({
        level: 'warning',
        labelName,
        message: 'additional PAGE custom properties will be discarded'
      })
    }

    if (id) ids.add(id)
    if (asString(record?.name).trim()) names.add(labelName.toLowerCase())

    const label = {
      id,
      scope: 'region',
      name: labelName,
      description: typeof record?.description === 'string' ? record.description : null,
      color,
      hasText: regionType === 'TextRegion',
      isContainer: record?.isContainer === true,
      group: typeof record?.group === 'string' ? record.group : null,
      mapping: {
        pageXml: {
          regionType,
          textType: textType || null,
          customSubType,
          customKey: CANONICAL_PAGE_CUSTOM_KEY,
          customData: ''
        }
      }
    } satisfies LabelDefinition
    labels.push(label)

    const signature = createCanonicalRegionMappingSignatureFromLabel(label)
    if (signature) {
      const existingLabel = mappingSignatures.get(signature)
      if (existingLabel) labelIssues.push(`duplicates the PAGE mapping used by "${existingLabel}"`)
      else mappingSignatures.set(signature, labelName)
    }

    for (const message of labelIssues) issues.push({ level: 'error', labelName, message })
  })

  const defaultLabelId = asString(meta?.defaultLabelId).trim()
  if (defaultLabelId && !ids.has(defaultLabelId)) {
    issues.push({ level: 'error', message: `Default label ID "${defaultLabelId}" does not reference an imported label.` })
  }

  const groups = new Set(labels.map(label => label.group).filter((group): group is string => Boolean(group)))
  return {
    name,
    labelCount: rawLabels.length,
    groupCount: groups.size,
    labelNames: labels.map(label => label.name),
    nameConflict: existingNames.has(name.toLowerCase()),
    issues,
    comparison: compareWithCurrent(meta ?? {}, labels, current)
  }
}

export function buildLabelSetImportPreview(content: string, options: PreviewOptions): LabelSetImportPreview {
  const issues: LabelSetImportIssue[] = []
  let rootValue: unknown
  try {
    rootValue = JSON.parse(content)
  } catch {
    return {
      fileName: options.fileName,
      labelSets: [],
      otherResources: [],
      issues: [{ level: 'error', message: 'The selected file is not valid JSON.' }],
      canImport: false
    }
  }

  const root = asRecord(rootValue)
  const resources = Array.isArray(root?.resources)
    ? root.resources.map(asRecord).filter((resource): resource is Record<string, unknown> => resource !== null)
    : root?.meta && Array.isArray(root.labels)
      ? [{ type: 'LABEL_SET', name: asRecord(root.meta)?.name, payload: root }]
      : []

  if (resources.length === 0) {
    issues.push({ level: 'error', message: 'The file is not a supported LAREX toolkit package or legacy label set.' })
  }

  const existingNames = new Set((options.existingNames ?? []).map(name => name.trim().toLowerCase()))
  const labelResources = resources.filter(resource => resource.type === 'LABEL_SET')
  const otherResources = resources
    .filter(resource => resource.type !== 'LABEL_SET')
    .map(resource => ({ type: asString(resource.type) || 'UNKNOWN', name: asString(resource.name) || 'Unnamed resource' }))
  const labelSets = labelResources.map(resource => inspectLabelSetPayload(resource.payload, existingNames, options.current))

  if (labelSets.length === 0 && resources.length > 0) {
    issues.push({ level: 'error', message: 'This package does not contain a label set.' })
  }
  if (otherResources.length > 0) {
    issues.push({
      level: 'warning',
      message: `The package also contains ${otherResources.length} other toolkit resource${otherResources.length === 1 ? '' : 's'}, which will be imported too.`
    })
  }

  const hasErrors = issues.some(issue => issue.level === 'error')
    || labelSets.some(labelSet => labelSet.issues.some(issue => issue.level === 'error'))
  return { fileName: options.fileName, labelSets, otherResources, issues, canImport: labelSets.length > 0 && !hasErrors }
}
