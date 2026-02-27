export type LabelScope = 'region' | 'line'
export type AltoRole = 'TAGREFS' | 'STYLEREFS'

export type AltoBlockType = 'TextBlock' | 'Illustration' | 'GraphicalElement' | 'ComposedBlock'

export type PageRegionType
  = | 'TextRegion'
    | 'ImageRegion'
    | 'LineDrawingRegion'
    | 'GraphicRegion'
    | 'TableRegion'
    | 'ChartRegion'
    | 'MapRegion'
    | 'SeparatorRegion'
    | 'MathsRegion'
    | 'ChemRegion'
    | 'MusicRegion'
    | 'AdvertRegion'
    | 'NoiseRegion'
    | 'UnknownRegion'

export type PageTextType
  = | ''
    | 'paragraph'
    | 'heading'
    | 'caption'
    | 'header'
    | 'footer'
    | 'page-number'
    | 'drop-capital'
    | 'credit'
    | 'floating'
    | 'signature-mark'
    | 'catch-word'
    | 'marginalia'
    | 'footnote'
    | 'footnote-continued'
    | 'endnote'
    | 'TOC-entry'
    | 'list-label'
    | 'other'
    | 'custom'

export interface LabelSetMeta {
  name: string
  description?: string | null
  tags?: string[] | null
  altoEnabled?: boolean
  isSystem?: boolean
}

export interface AltoXmlMapping {
  role: AltoRole
  tag: string
  blockType?: AltoBlockType | null
}

export interface PageXmlMapping {
  regionType?: PageRegionType | null
  textType?: PageTextType | null
  customSubType?: string | null
  customKey: string
  customData?: string | null
}

export interface LabelMapping {
  altoXml: AltoXmlMapping
  pageXml: PageXmlMapping
}

export interface LabelDefinition {
  id: string
  scope: LabelScope
  name: string
  description?: string | null
  color: string
  hasText: boolean
  isContainer: boolean
  group?: string | null
  mapping: LabelMapping
}

export interface LabelSetCreateOrUpdateRequest {
  meta: LabelSetMeta
  labels: LabelDefinition[]
}

export interface LabelSet {
  id: string
  meta: LabelSetMeta
  labels: LabelDefinition[]
  created: string
  updated: string
}

export interface LabelSetSummary {
  id: string
  meta: LabelSetMeta
  labelCount: number
  created: string
  updated: string
}
