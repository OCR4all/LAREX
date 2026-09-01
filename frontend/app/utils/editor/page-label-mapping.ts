export type PageCustomBlocks = Record<string, Record<string, string>>

export interface PageXmlMappingLike {
  regionType?: string | null
  textType?: string | null
  customSubType?: string | null
  customKey?: string | null
  customData?: string | null
}

export interface RegionLabelDefinitionLike {
  id?: string | null
  name?: string | null
  scope?: 'region' | null
  mapping?: {
    pageXml?: PageXmlMappingLike | null
  } | null
}

export interface RegionLikeForLabelMatching {
  regionKind?: string | null
  regionSubtype?: string | null
  regionCustom?: string | null
}

export interface RuntimePageRegionLike {
  kind?: string | null
  type?: string | null
  custom?: string | null
}

export interface ResolvedPageXmlRegionMapping {
  regionType: string
  type?: string
  custom?: string
}

const CUSTOM_BLOCK_PATTERN = /([^\s{}]+)\s*\{([^}]*)\}/g

function normalizeString(value: string | null | undefined): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function cloneBlocks(blocks: PageCustomBlocks): PageCustomBlocks {
  const next: PageCustomBlocks = {}
  for (const [blockKey, pairs] of Object.entries(blocks)) {
    next[blockKey] = { ...pairs }
  }
  return next
}

export function parsePageCustomPairs(raw: string | null | undefined): Record<string, string> {
  const source = normalizeString(raw)
  if (!source) return {}

  const pairs: Record<string, string> = {}
  for (const segment of source.split(';')) {
    const trimmed = segment.trim()
    if (!trimmed) continue

    const idx = trimmed.indexOf(':')
    if (idx <= 0 || idx >= trimmed.length - 1) continue

    const key = normalizeString(trimmed.slice(0, idx))
    const value = normalizeString(trimmed.slice(idx + 1))
    if (!key || !value) continue
    pairs[key] = value
  }

  return pairs
}

export function parsePageCustomBlocks(custom: string | null | undefined): PageCustomBlocks {
  const source = normalizeString(custom)
  if (!source) return {}

  const blocks: PageCustomBlocks = {}
  let match: RegExpExecArray | null
  CUSTOM_BLOCK_PATTERN.lastIndex = 0
  while ((match = CUSTOM_BLOCK_PATTERN.exec(source)) !== null) {
    const blockKey = normalizeString(match[1])
    if (!blockKey) continue

    const rawPairs = typeof match[2] === 'string' ? match[2] : ''
    const parsedPairs = parsePageCustomPairs(rawPairs)
    blocks[blockKey] = {
      ...(blocks[blockKey] ?? {}),
      ...parsedPairs
    }
  }

  return blocks
}

export function serializePageCustomBlocks(blocks: PageCustomBlocks): string | undefined {
  const normalizedBlocks: Array<{ key: string, pairs: Array<[string, string]> }> = []
  for (const [blockKey, pairs] of Object.entries(blocks)) {
    const key = normalizeString(blockKey)
    if (!key) continue

    const normalizedPairs: Array<[string, string]> = []
    for (const [pairKey, rawValue] of Object.entries(pairs ?? {})) {
      const normalizedPairKey = normalizeString(pairKey)
      const normalizedValue = normalizeString(rawValue)
      if (!normalizedPairKey || !normalizedValue) continue
      normalizedPairs.push([normalizedPairKey, normalizedValue])
    }
    normalizedPairs.sort(([a], [b]) => a.localeCompare(b))
    normalizedBlocks.push({ key, pairs: normalizedPairs })
  }
  normalizedBlocks.sort((a, b) => a.key.localeCompare(b.key))

  if (normalizedBlocks.length === 0) return undefined

  return normalizedBlocks
    .map(({ key, pairs }) => {
      const pairText = pairs.map(([pairKey, value]) => `${pairKey}:${value};`).join(' ')
      return pairText ? `${key} { ${pairText} }` : `${key} { }`
    })
    .join(' ')
}

export function mergePageCustomBlock(
  rawCustom: string | null | undefined,
  blockKey: string | null | undefined,
  updates: Record<string, string | null | undefined>
): string | undefined {
  const normalizedBlockKey = normalizeString(blockKey)
  if (!normalizedBlockKey) return normalizeString(rawCustom) ?? undefined

  const parsedBlocks = parsePageCustomBlocks(rawCustom)
  const nextBlocks = cloneBlocks(parsedBlocks)
  const nextBlock = { ...(nextBlocks[normalizedBlockKey] ?? {}) }

  for (const [rawKey, rawValue] of Object.entries(updates)) {
    const key = normalizeString(rawKey)
    if (!key) continue
    const value = normalizeString(rawValue)
    if (!value) {
      delete nextBlock[key]
      continue
    }
    nextBlock[key] = value
  }

  if (Object.keys(nextBlock).length === 0) {
    delete nextBlocks[normalizedBlockKey]
  } else {
    nextBlocks[normalizedBlockKey] = nextBlock
  }

  const serialized = serializePageCustomBlocks(nextBlocks)

  // Preserve fully-unparseable external custom strings by appending the merged block.
  if (!serialized) {
    const raw = normalizeString(rawCustom)
    if (!raw) return undefined

    const fallbackBlock = serializePageCustomBlocks({
      [normalizedBlockKey]: Object.fromEntries(
        Object.entries(updates)
          .map(([k, v]) => [normalizeString(k), normalizeString(v)] as const)
          .filter((entry): entry is readonly [string, string] => Boolean(entry[0] && entry[1]))
      )
    })
    if (!fallbackBlock) return raw
    return `${raw} ${fallbackBlock}`.trim()
  }

  return serialized
}

const LAREX_LABEL_BLOCK_KEY = 'larex'
const LAREX_LABEL_ALIAS_KEY = 'labelAlias'
const LAREX_LABEL_ID_KEY = 'labelId'

function normalizeExpectedPairsForTextRegionCustom(mapping: PageXmlMappingLike): Record<string, string> {
  const pairs = parsePageCustomPairs(mapping.customData)
  delete pairs.type
  return pairs
}

function getTextRegionCustomBlockKey(mapping: PageXmlMappingLike): string {
  return normalizeString(mapping.customKey) ?? 'structure'
}

function normalizeTextTypeForSignature(textType: string | null | undefined): string {
  return normalizeString(textType) ?? ''
}

function normalizeSubtypeForSignature(subType: string | null | undefined): string {
  return normalizeString(subType) ?? ''
}

function pairsToCanonical(pairs: Record<string, string>): string {
  return Object.entries(pairs)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `${key}=${value}`)
    .join(',')
}

export function createCanonicalRegionMappingSignatureFromPageXml(mapping: PageXmlMappingLike | null | undefined): string | null {
  if (!mapping) return null

  const regionType = normalizeString(mapping.regionType)
  if (!regionType) return null

  if (regionType !== 'TextRegion') {
    const subType = normalizeSubtypeForSignature(mapping.customSubType)
    return subType
      ? `region|kind=${regionType}|subType=${subType}`
      : `region|kind=${regionType}`
  }

  const textType = normalizeTextTypeForSignature(mapping.textType)
  if (textType !== 'custom') {
    return `region|kind=TextRegion|textType=${textType}`
  }

  const customSubType = normalizeSubtypeForSignature(mapping.customSubType)
  const blockKey = getTextRegionCustomBlockKey(mapping)
  const pairs = normalizeExpectedPairsForTextRegionCustom(mapping)
  return `region|kind=TextRegion|textType=custom|block=${blockKey}|customType=${customSubType}|pairs=${pairsToCanonical(pairs)}`
}

export function createCanonicalRegionMappingSignatureFromLabel(label: RegionLabelDefinitionLike): string | null {
  return createCanonicalRegionMappingSignatureFromPageXml(label.mapping?.pageXml)
}

export function resolveRegionLabelDisplayName(
  labels: RegionLabelDefinitionLike[] | null | undefined,
  region: RegionLikeForLabelMatching | RuntimePageRegionLike,
  fallback?: string | null
): string | undefined {
  const mappedName = findRegionLabelDefinitionForRegion(labels ?? undefined, region)?.name?.trim()
  if (mappedName) return mappedName

  const fallbackName = normalizeString(fallback)
  return fallbackName ?? undefined
}

function runtimeRegionParts(input: RegionLikeForLabelMatching | RuntimePageRegionLike): {
  kind: string | null
  subtype: string | null
  custom: string | null
} {
  const anyInput = input as any
  return {
    kind: normalizeString(anyInput.regionKind ?? anyInput.kind),
    subtype: normalizeString(anyInput.regionSubtype ?? anyInput.type),
    custom: normalizeString(anyInput.regionCustom ?? anyInput.custom)
  }
}

export function createCanonicalRegionSignatureFromRuntimeRegion(
  input: RegionLikeForLabelMatching | RuntimePageRegionLike,
  customBlockKey = 'structure'
): string | null {
  const { kind, subtype, custom } = runtimeRegionParts(input)
  if (!kind) return null

  if (kind !== 'TextRegion') {
    return subtype
      ? `region|kind=${kind}|subType=${subtype}`
      : `region|kind=${kind}`
  }

  const textType = subtype ?? ''
  const blockKey = normalizeString(customBlockKey) ?? 'structure'
  const structure = parsePageCustomBlocks(custom)[blockKey] ?? {}
  const customType = normalizeString(structure.type) ?? ''
  const isRuntimeCustomTextRegion = (textType === 'other' || textType === 'custom' || textType === '') && customType !== ''

  if (!isRuntimeCustomTextRegion) {
    return `region|kind=TextRegion|textType=${textType}`
  }

  const pairs = { ...structure }
  delete pairs.type
  return `region|kind=TextRegion|textType=custom|block=${blockKey}|customType=${customType}|pairs=${pairsToCanonical(pairs)}`
}

function getRegionLabelMatchScore(label: RegionLabelDefinitionLike, region: RegionLikeForLabelMatching | RuntimePageRegionLike): number {
  const mapping = label.mapping?.pageXml
  if (!mapping) return 0

  const regionType = normalizeString(mapping.regionType)
  if (!regionType) return 0

  const { kind, subtype, custom } = runtimeRegionParts(region)
  if (!kind || kind !== regionType) return 0

  if (regionType !== 'TextRegion') {
    const expectedSubtype = normalizeSubtypeForSignature(mapping.customSubType)
    const actualSubtype = subtype ?? ''
    return expectedSubtype === actualSubtype ? 2000 : 0
  }

  const expectedTextType = normalizeTextTypeForSignature(mapping.textType)
  const actualTextType = subtype ?? ''
  const blockKey = getTextRegionCustomBlockKey(mapping)
  const block = parsePageCustomBlocks(custom)[blockKey] ?? {}
  const actualCustomType = normalizeString(block.type) ?? ''
  const runtimeUsesCustomSemantics = (actualTextType === 'other' || actualTextType === 'custom' || actualTextType === '') && actualCustomType !== ''

  if (expectedTextType !== 'custom') {
    // Plain PAGE TextRegion[type="other"] is distinct from custom-text semantics stored in @custom structure.type.
    if (expectedTextType === 'other' && runtimeUsesCustomSemantics) return 0
    if (expectedTextType !== actualTextType) return 0
    return 2000
  }

  if (!runtimeUsesCustomSemantics) return 0
  const expectedCustomType = normalizeSubtypeForSignature(mapping.customSubType)
  if (expectedCustomType !== actualCustomType) return 0

  const expectedPairs = normalizeExpectedPairsForTextRegionCustom(mapping)
  for (const [key, value] of Object.entries(expectedPairs)) {
    if ((normalizeString(block[key]) ?? '') !== value) return 0
  }
  const specificity = Object.keys(expectedPairs).length

  const labelSignature = createCanonicalRegionMappingSignatureFromPageXml(mapping)
  const runtimeSignature = createCanonicalRegionSignatureFromRuntimeRegion({ kind, type: actualTextType, custom }, blockKey)
  if (labelSignature && runtimeSignature && labelSignature === runtimeSignature) {
    if (actualTextType === 'other') return 2200 + specificity
    if (actualTextType === 'custom') return 2000 + specificity
    return 1500 + specificity
  }
  if (actualTextType === 'other') return 1200 + specificity
  if (actualTextType === 'custom') return 1000 + specificity
  // Fallback for older malformed exports missing TextRegion @type plus structure.type
  return 500 + specificity
}

export function findRegionLabelDefinitionForRegion<T extends RegionLabelDefinitionLike>(
  labels: T[] | undefined,
  region: RegionLikeForLabelMatching | RuntimePageRegionLike
): T | undefined {
  if (!labels || labels.length === 0) return undefined

  let best: T | undefined
  let bestScore = 0
  for (const label of labels) {
    const score = getRegionLabelMatchScore(label, region)
    if (score > bestScore) {
      best = label
      bestScore = score
    }
  }

  return best
}

export function buildMergedCustomForRegionLabel(
  currentCustom: string | null | undefined,
  mapping: PageXmlMappingLike | null | undefined
): string | undefined {
  if (!mapping) return normalizeString(currentCustom) ?? undefined
  if (normalizeString(mapping.regionType) !== 'TextRegion') return normalizeString(currentCustom) ?? undefined
  // Internal label-set sentinel uses textType="custom", but PAGE XML writes TextRegion@type="other"
  // and stores the actual subtype in @custom structure.type.
  if (normalizeTextTypeForSignature(mapping.textType) !== 'custom') return normalizeString(currentCustom) ?? undefined

  const blockKey = getTextRegionCustomBlockKey(mapping)
  const pairs: Record<string, string> = {
    ...normalizeExpectedPairsForTextRegionCustom(mapping)
  }
  pairs.type = normalizeSubtypeForSignature(mapping.customSubType)

  return mergePageCustomBlock(currentCustom, blockKey, pairs)
}

/**
 * Resolve a label mapping to the PAGE region attributes used by the editor model
 * and, ultimately, PAGE XML serialization.
 */
export function resolvePageXmlRegionMapping(
  mapping: PageXmlMappingLike | null | undefined,
  currentCustom?: string | null
): ResolvedPageXmlRegionMapping | null {
  if (!mapping) return null

  const regionType = normalizeString(mapping.regionType)
  if (!regionType) return null

  const custom = buildMergedCustomForRegionLabel(currentCustom, mapping)
  if (regionType !== 'TextRegion') {
    const type = normalizeString(mapping.customSubType) ?? undefined
    return { regionType, ...(type ? { type } : {}), ...(custom ? { custom } : {}) }
  }

  const textType = normalizeTextTypeForSignature(mapping.textType)
  const type = textType === 'custom' ? 'other' : (textType || undefined)
  return { regionType, ...(type ? { type } : {}), ...(custom ? { custom } : {}) }
}

export function resolvePageXmlRegionLabel(
  label: RegionLabelDefinitionLike,
  currentCustom?: string | null
): ResolvedPageXmlRegionMapping | null {
  return resolvePageXmlRegionMapping(label.mapping?.pageXml, currentCustom)
}

function escapeXmlAttribute(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

export function serializePageXmlRegionStartTag(mapping: PageXmlMappingLike | null | undefined): string | null {
  const resolved = resolvePageXmlRegionMapping(mapping)
  if (!resolved) return null

  const attributes = [
    resolved.type ? `type="${escapeXmlAttribute(resolved.type)}"` : null,
    resolved.custom ? `custom="${escapeXmlAttribute(resolved.custom)}"` : null
  ].filter((attribute): attribute is string => attribute !== null)
  const suffix = attributes.length > 0 ? ` ${attributes.join(' ')}` : ''
  return `<${resolved.regionType}${suffix}>`
}

export function clearLarexRegionLabelMetadata(currentCustom: string | null | undefined): string | undefined {
  return mergePageCustomBlock(currentCustom, LAREX_LABEL_BLOCK_KEY, {
    [LAREX_LABEL_ALIAS_KEY]: null,
    [LAREX_LABEL_ID_KEY]: null
  })
}

export function buildMergedCustomForAppliedRegionLabel(
  currentCustom: string | null | undefined,
  label: RegionLabelDefinitionLike
): string | undefined {
  return resolvePageXmlRegionLabel(label, currentCustom)?.custom
}
