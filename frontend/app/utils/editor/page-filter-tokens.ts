import type { LabelDefinition, LabelScope } from '@/types/label-set'

export const LEGACY_PAGE_REGION_KINDS = new Set<string>([
  'TextRegion',
  'ImageRegion',
  'LineDrawingRegion',
  'GraphicRegion',
  'TableRegion',
  'ChartRegion',
  'MapRegion',
  'SeparatorRegion',
  'MathsRegion',
  'ChemRegion',
  'MusicRegion',
  'AdvertRegion',
  'NoiseRegion',
  'UnknownRegion',
  'CustomRegion'
])

function normalizeString(value: string | null | undefined): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function encodeTokenPart(value: string): string {
  return encodeURIComponent(value)
}

export function createRegionBaseToken(regionKind: string): string {
  return `region|kind=${encodeTokenPart(regionKind)}`
}

export function createRegionTextTypeToken(textType: string): string {
  return `region|kind=${encodeTokenPart('TextRegion')}|textType=${encodeTokenPart(textType)}`
}

export function createRegionSubtypeToken(regionKind: string, subtype: string): string {
  return `region|kind=${encodeTokenPart(regionKind)}|subType=${encodeTokenPart(subtype)}`
}

export function createLineCustomPresenceToken(customKey: string): string {
  return `line|customKey=${encodeTokenPart(customKey)}`
}

export function createLineCustomPairsToken(customKey: string, pairs: Record<string, string>): string {
  const pairsToken = Object.entries(pairs)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `${encodeTokenPart(key)}=${encodeTokenPart(value)}`)
    .join(',')

  return `line|customKey=${encodeTokenPart(customKey)}|pairs=${pairsToken}`
}

function parseKeyValuePairs(raw: string | null | undefined): Record<string, string> {
  const source = normalizeString(raw)
  if (!source) return {}

  const result: Record<string, string> = {}
  for (const segment of source.split(';')) {
    const trimmed = segment.trim()
    if (!trimmed) continue
    const idx = trimmed.indexOf(':')
    if (idx <= 0 || idx >= trimmed.length - 1) continue

    const key = normalizeString(trimmed.slice(0, idx))
    const value = normalizeString(trimmed.slice(idx + 1))
    if (!key || !value) continue
    result[key] = value
  }

  return result
}

export function createCanonicalTokenFromLabelDefinition(label: LabelDefinition): string | null {
  const scope = normalizeString(label.scope)
  const mapping = label.mapping?.pageXml

  if (!scope || !mapping) return null

  if (scope === 'region') {
    const regionType = normalizeString(mapping.regionType)
    if (!regionType) return null

    if (regionType === 'TextRegion') {
      const textType = normalizeString(mapping.textType)
      if (textType) {
        return createRegionTextTypeToken(textType)
      }
      return createRegionBaseToken(regionType)
    }

    const subType = normalizeString(mapping.customSubType)
    if (subType) {
      return createRegionSubtypeToken(regionType, subType)
    }

    return createRegionBaseToken(regionType)
  }

  if (scope === 'line') {
    const customKey = normalizeString(mapping.customKey)
    if (!customKey) return null

    const pairs = parseKeyValuePairs(mapping.customData)
    if (Object.keys(pairs).length > 0) {
      return createLineCustomPairsToken(customKey, pairs)
    }

    return createLineCustomPresenceToken(customKey)
  }

  return null
}

export function normalizeLegacyLabelFilterValues(input: unknown): string[] {
  if (!Array.isArray(input)) return []

  const normalized: string[] = []
  for (const value of input) {
    if (typeof value !== 'string') continue
    const trimmed = value.trim()
    if (!trimmed) continue

    if (trimmed.startsWith('region|') || trimmed.startsWith('line|')) {
      normalized.push(trimmed)
      continue
    }

    if (LEGACY_PAGE_REGION_KINDS.has(trimmed)) {
      normalized.push(createRegionBaseToken(trimmed))
      continue
    }
  }

  return [...new Set(normalized)]
}

export interface CanonicalLabelFilterOption {
  label: string
  value: string
  color: string
  scope: LabelScope
}

export function createCanonicalLabelFilterOptions(labels: LabelDefinition[]): CanonicalLabelFilterOption[] {
  const dedupedByToken = new Map<string, CanonicalLabelFilterOption>()
  for (const label of labels) {
    const token = createCanonicalTokenFromLabelDefinition(label)
    if (!token) continue
    if (dedupedByToken.has(token)) continue

    dedupedByToken.set(token, {
      label: label.name,
      value: token,
      color: label.color,
      scope: label.scope
    })
  }

  return [...dedupedByToken.values()]
}
