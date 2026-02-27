import type { Page } from './page'

/** PAGE XML 2019 root element: PcGts */
export class PcGts {
  metadata: Metadata
  page: Page
  pcGtsId?: string

  constructor(metadata: Metadata, page: Page, pcGtsId?: string) {
    this.metadata = metadata
    this.page = page
    this.pcGtsId = pcGtsId
  }
}

/** PAGE XML 2019: MetadataType */
export class Metadata {
  creator?: string
  created?: string
  lastChange?: string
  comments?: string
  externalRef?: string
  userDefined?: UserDefined
  items?: MetadataItem[]

  constructor(params: {
    creator?: string
    created?: string
    lastChange?: string
    comments?: string
    externalRef?: string
    userDefined?: UserDefined
    items?: MetadataItem[]
  }) {
    this.creator = params.creator
    this.created = params.created
    this.lastChange = params.lastChange
    this.comments = params.comments
    this.externalRef = params.externalRef
    this.userDefined = params.userDefined
    this.items = params.items
  }

  touch(now: Date = new Date()): void {
    this.lastChange = now.toISOString()
  }
}

export interface UserDefined {
  entries: Record<string, string>
}

export type MetadataItemType = 'author' | 'imageProperties' | 'processingStep' | 'other'

export interface MetadataItem {
  type?: MetadataItemType
  name?: string
  value: string
  date?: string
  labels?: Labels[]
}

export interface Labels {
  externalModel?: string
  externalId?: string
  prefix?: string
  comments?: string
  labels?: Label[]
}

export interface Label {
  value: string
  type?: string
  comments?: string
}
