import type { ResourceCapabilities } from './capabilities'

export type NormalizationVariantScope = 'ALL' | 'PRIMARY'

export interface NormalizationReplacementRule {
  search: string
  replacement: string
  regex: boolean
}

export interface NormalizationProfileSummary {
  id: string
  name: string
  description?: string | null
  tags: string[]
  unicodeNormalization: string
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface NormalizationProfile {
  id: string
  name: string
  description?: string | null
  tags: string[]
  unicodeNormalization: string
  collapseWhitespace: boolean
  trimText: boolean
  dehyphenateLineBreaks: boolean
  mapLongSToS: boolean
  expandCommonLigatures: boolean
  normalizeQuotes: boolean
  normalizeDashes: boolean
  normalizeEllipsis: boolean
  replacementRules: NormalizationReplacementRule[]
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface NormalizationProfileCreateOrUpdateRequest {
  name: string
  description?: string | null
  tags?: string[]
  unicodeNormalization?: string | null
  collapseWhitespace?: boolean
  trimText?: boolean
  dehyphenateLineBreaks?: boolean
  mapLongSToS?: boolean
  expandCommonLigatures?: boolean
  normalizeQuotes?: boolean
  normalizeDashes?: boolean
  normalizeEllipsis?: boolean
  replacementRules?: NormalizationReplacementRule[]
}

export interface NormalizationProjectScope {
  projectId: string
  pageIds?: string[]
}

export interface NormalizeSourcesRequest {
  sources: NormalizationProjectScope[]
  variantScope?: NormalizationVariantScope
  variantIndex?: number | null
  unindexedOnly?: boolean
  targets?: NormalizeTarget[]
}

export interface NormalizeTarget {
  pageId: string
  textLineId?: string | null
  regionId?: string | null
  variantIndex?: number | null
}

export interface NormalizeMatch {
  key: string
  label: string
  description?: string | null
  manual: boolean
  regex: boolean
}

export interface NormalizePreview {
  projectId: string
  projectName: string
  pageId: string
  pageName: string
  textLineId?: string | null
  regionId?: string | null
  variantIndex?: number | null
  originalText: string
  normalizedText: string
  matchedRules: NormalizeMatch[]
}

export interface NormalizeSourcesResponse {
  analyzedProjectCount: number
  analyzedPageCount: number
  analyzedRowCount: number
  changedRowCount: number
  changedPageCount: number
  previews: NormalizePreview[]
  message: string
}

export interface ApplySourcesResponse {
  analyzedProjectCount: number
  analyzedPageCount: number
  targetedRowCount: number
  changedRowCount: number
  changedPageCount: number
  message: string
}
