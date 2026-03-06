import type { ResourceCapabilities } from './capabilities'

export interface CodecSummary {
  id: string
  name: string
  description?: string | null
  tags: string[]
  characterCount: number
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface Codec {
  id: string
  name: string
  description?: string | null
  tags: string[]
  codec: string[]
  characterCount: number
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface CodecCreateOrUpdateRequest {
  name: string
  description?: string | null
  tags?: string[]
  codec?: string[]
}

export interface GenerateCodecFromProjectRequest {
  projectId: string
}

export interface GenerateCodecFromProjectResponse {
  codec: string[]
  characterCount: number
  message: string
}

export interface ValidateCodecAgainstProjectRequest {
  projectId: string
}

export interface ValidateCodecAgainstProjectResponse {
  valid: boolean
  projectCharactersNotInCodec: string[]
  message: string
}

export type CodecVariantScope = 'ALL' | 'PRIMARY'

export interface CodecProjectScope {
  projectId: string
  pageIds?: string[]
}

export interface GenerateCodecFromSourcesRequest {
  sources: CodecProjectScope[]
  targetCodecId?: string | null
  newCodecName?: string | null
  newCodecDescription?: string | null
  newCodecTags?: string[]
  variantScope?: CodecVariantScope
  variantIndex?: number | null
  unindexedOnly?: boolean
  includeWhitespace?: boolean
}

export interface GenerateCodecFromSourcesResponse {
  codec: Codec
  createdNewCodec: boolean
  analyzedProjectCount: number
  analyzedPageCount: number
  extractedCharacterCount: number
  addedCharacterCount: number
  message: string
}

export interface ValidateCodecAgainstSourcesRequest {
  sources: CodecProjectScope[]
  variantScope?: CodecVariantScope
  variantIndex?: number | null
  unindexedOnly?: boolean
  includeWhitespace?: boolean
}

export interface ValidateCodecProjectResult {
  projectId: string
  projectName?: string | null
  analyzedPageCount: number
  missingCharacters: string[]
  missingCharacterCount: number
  missingPageIds: string[]
  missingPageCount: number
  valid: boolean
}

export interface ValidateCodecCharacterPageRef {
  projectId: string
  projectName?: string | null
  pageId: string
  pageName?: string | null
}

export interface ValidateCodecCharacterResult {
  character: string
  pages: ValidateCodecCharacterPageRef[]
}

export interface ValidateCodecAgainstSourcesResponse {
  valid: boolean
  missingCharacters: string[]
  missingCharacterCount: number
  analyzedProjectCount: number
  analyzedPageCount: number
  projectResults: ValidateCodecProjectResult[]
  missingCharacterResults: ValidateCodecCharacterResult[]
  message: string
}
