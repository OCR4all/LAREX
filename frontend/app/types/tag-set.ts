import type { ResourceCapabilities } from './capabilities'

export interface TagNode {
  id: string
  title: string
  description?: string | null
  color: string
  children?: TagNode[]
}

export interface TagSetMeta {
  name: string
  description?: string | null
  tags?: string[]
}

export interface TagSet {
  id: string
  meta: TagSetMeta
  tags: TagNode[]
  totalTagCount: number
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface TagSetSummary {
  id: string
  meta: TagSetMeta
  tagCount: number
  created: string
  updated: string
  capabilities?: ResourceCapabilities
}

export interface FlattenedTag {
  id: string
  title: string
  path: string
  color: string
  ancestorIds: string[]
  descendantIds: string[]
}

export interface TagSetValidationResult {
  valid: boolean
  invalidTags: string[]
  validTags: string[]
  message: string
}

export interface TagSetCreateOrUpdateRequest {
  meta: TagSetMeta
  tags: TagNode[]
}
