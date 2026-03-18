import type { ResourceCapabilities } from './capabilities'

export interface DictionarySummary {
  id: string
  name: string
  description?: string | null
  tags: string[]
  caseSensitive: boolean
  unicodeNormalization: string
  locked: boolean
  entryCount: number
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface Dictionary {
  id: string
  name: string
  description?: string | null
  tags: string[]
  caseSensitive: boolean
  unicodeNormalization: string
  locked: boolean
  entryCount: number
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface DictionaryCreateOrUpdateRequest {
  name: string
  description?: string | null
  tags?: string[]
  caseSensitive?: boolean
  unicodeNormalization?: string
  locked?: boolean
}

export interface DictionaryEntry {
  id: string
  form: string
  normalizedValue: string
  sourceEntryKey?: string | null
  metadata?: Record<string, unknown> | null
  created: string
  updated: string
}

export interface DictionaryEntryPageResponse {
  entries: DictionaryEntry[]
  totalEntries: number
  totalPages: number
  page: number
  size: number
}

export interface DictionaryEntryCreateOrUpdateRequest {
  form: string
  sourceEntryKey?: string | null
  metadata?: Record<string, unknown> | null
  fromEditor?: boolean
}

export type DictionaryVariantScope = 'ALL' | 'PRIMARY'

export interface DictionaryProjectScope {
  projectId: string
  pageIds?: string[]
}

export interface DictionaryValidateAgainstSourcesRequest {
  sources: DictionaryProjectScope[]
  variantScope?: DictionaryVariantScope
  variantIndex?: number | null
  unindexedOnly?: boolean
}

export interface DictionaryValidateAgainstProjectRequest {
  projectId: string
}

export interface DictionarySuggestion {
  display: string
  normalized: string
  distance: number
}

export interface DictionarySuggestResponse {
  token: string
  normalizedToken: string
  suggestions: DictionarySuggestion[]
}

export interface DictionaryTokenCheckResult {
  token: string
  normalizedToken: string
  known: boolean
  suggestions: DictionarySuggestion[]
}

export interface DictionaryCheckTokensResponse {
  dictionaryId: string
  results: DictionaryTokenCheckResult[]
}

export interface DictionaryValidateTokenPageRef {
  projectId: string
  projectName?: string | null
  pageId: string
  pageName?: string | null
}

export interface DictionaryValidateTokenResult {
  token: string
  normalizedToken: string
  occurrenceCount: number
  pages: DictionaryValidateTokenPageRef[]
  suggestions: DictionarySuggestion[]
}

export interface DictionaryValidateProjectResult {
  projectId: string
  projectName?: string | null
  analyzedPageCount: number
  unknownTokens: string[]
  unknownTokenCount: number
  unknownPageIds: string[]
  unknownPageCount: number
  valid: boolean
}

export interface DictionaryValidateAgainstSourcesResponse {
  valid: boolean
  analyzedProjectCount: number
  analyzedPageCount: number
  analyzedTokenCount: number
  knownTokenCount: number
  unknownTokenCount: number
  unknownTokens: string[]
  projectResults: DictionaryValidateProjectResult[]
  unknownTokenResults: DictionaryValidateTokenResult[]
  message: string
}

export interface DictionaryFormEntry {
  display: string
  normalized: string
}

export interface DictionaryFormsResponse {
  dictionaryId: string
  caseSensitive: boolean
  unicodeNormalization: string
  forms: DictionaryFormEntry[]
}
