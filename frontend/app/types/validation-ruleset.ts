import type { ResourceCapabilities } from './capabilities'

export type ValidationSeverity = 'INFO' | 'WARNING' | 'ERROR'
export type ValidationVariantScope = 'ALL' | 'PRIMARY'

export interface ValidationRule {
  id?: string | null
  name: string
  description?: string | null
  severity?: ValidationSeverity | null
  pattern: string
  flags?: string | null
  message?: string | null
}

export interface ValidationRulesetSummary {
  id: string
  name: string
  description?: string | null
  tags: string[]
  ruleCount: number
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface ValidationRuleset {
  id: string
  name: string
  description?: string | null
  tags: string[]
  rules: ValidationRule[]
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface ValidationRulesetCreateOrUpdateRequest {
  name: string
  description?: string | null
  tags?: string[]
  rules: ValidationRule[]
}

export interface ValidationProjectScope {
  projectId: string
  pageIds?: string[]
}

export interface ValidateAgainstSourcesRequest {
  sources: ValidationProjectScope[]
  variantScope?: ValidationVariantScope
  variantIndex?: number | null
  unindexedOnly?: boolean
}

export interface ValidationRulePageRef {
  projectId: string
  projectName: string
  pageId: string
  pageName: string
}

export interface ValidationRuleResult {
  ruleId: string
  ruleName: string
  severity: ValidationSeverity
  message: string
  occurrenceCount: number
  matchedSamples: string[]
  pages: ValidationRulePageRef[]
}

export interface ValidateAgainstSourcesResponse {
  valid: boolean
  analyzedProjectCount: number
  analyzedPageCount: number
  totalOccurrenceCount: number
  ruleResults: ValidationRuleResult[]
  message: string
}
