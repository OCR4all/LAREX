import type { Polygon } from './geometry'
import type { TextContentVariant, TextLine } from './text'
import type { AlternativeImage, Labels, TextStyleAttributes, UserDefined } from './document'

export type RegionKind
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
    | 'CustomRegion'

/** All PAGE XML region kinds */
export const ALL_REGION_KINDS: RegionKind[] = [
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
]

/** Region kinds that can contain TextLines */
export const TEXT_CAPABLE_REGION_KINDS: RegionKind[] = ['TextRegion']

/** Check if a region kind can contain TextLines */
export function canContainTextLines(kind: RegionKind): boolean {
  return TEXT_CAPABLE_REGION_KINDS.includes(kind)
}

/** PAGE XML TextTypeSimpleType - allowed values for TextRegion @type attribute */
export type TextRegionSubtype
  = | 'paragraph'
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

/** All PAGE XML TextRegion subtypes */
export const ALL_TEXT_REGION_SUBTYPES: TextRegionSubtype[] = [
  'paragraph',
  'heading',
  'caption',
  'header',
  'footer',
  'page-number',
  'drop-capital',
  'credit',
  'floating',
  'signature-mark',
  'catch-word',
  'marginalia',
  'footnote',
  'footnote-continued',
  'endnote',
  'TOC-entry',
  'list-label',
  'other'
]

/** PAGE XML GraphicsTypeSimpleType - allowed values for GraphicRegion @type attribute */
export type GraphicRegionSubtype
  = | 'logo'
    | 'letterhead'
    | 'decoration'
    | 'frame'
    | 'handwritten-annotation'
    | 'stamp'
    | 'signature'
    | 'barcode'
    | 'paper-grow'
    | 'punch-hole'
    | 'other'

/** All PAGE XML GraphicRegion subtypes */
export const ALL_GRAPHIC_REGION_SUBTYPES: GraphicRegionSubtype[] = [
  'logo',
  'letterhead',
  'decoration',
  'frame',
  'handwritten-annotation',
  'stamp',
  'signature',
  'barcode',
  'paper-grow',
  'punch-hole',
  'other'
]

/** PAGE XML ChartTypeSimpleType - allowed values for ChartRegion @type attribute */
export type ChartRegionSubtype
  = | 'bar'
    | 'line'
    | 'pie'
    | 'scatter'
    | 'surface'
    | 'other'

/** All PAGE XML ChartRegion subtypes */
export const ALL_CHART_REGION_SUBTYPES: ChartRegionSubtype[] = [
  'bar',
  'line',
  'pie',
  'scatter',
  'surface',
  'other'
]

/**
 * Custom label stored in the @custom attribute.
 * These extend the PAGE XML schema with user-defined labels.
 * Serialized as JSON in the @custom attribute.
 */
export interface CustomLabel {
  /** Unique identifier for this custom label */
  id: string
  /** Display name */
  name: string
  /** Color in hex format (e.g., "#FF5722") */
  color?: string
  /** Optional description */
  description?: string
}

/**
 * Container for custom labels parsed from @custom attribute.
 * The @custom attribute may contain other data as well.
 */
export interface CustomLabelData {
  /** Custom labels array */
  labels?: CustomLabel[]
  /** Original custom string (for preserving non-label data) */
  rawCustom?: string
}

/**
 * Parse custom labels from the @custom attribute.
 * Expected format: JSON object with "labels" array, or legacy string.
 */
export function parseCustomLabels(custom?: string): CustomLabelData {
  if (!custom) return {}

  try {
    const parsed = JSON.parse(custom)
    if (typeof parsed === 'object' && parsed !== null) {
      return {
        labels: Array.isArray(parsed.labels) ? parsed.labels : undefined,
        rawCustom: custom
      }
    }
  } catch {
  }

  return { rawCustom: custom }
}

/**
 * Serialize custom labels to the @custom attribute format.
 */
export function serializeCustomLabels(data: CustomLabelData): string | undefined {
  if (!data.labels || data.labels.length === 0) {
    return data.rawCustom
  }

  const obj: Record<string, unknown> = { labels: data.labels }

  if (data.rawCustom) {
    try {
      const parsed = JSON.parse(data.rawCustom)
      if (typeof parsed === 'object' && parsed !== null) {
        Object.assign(obj, parsed, { labels: data.labels })
      }
    } catch {
      obj._raw = data.rawCustom
    }
  }

  return JSON.stringify(obj)
}

export interface Roles {
  tableCellRole?: {
    rowIndex?: number
    columnIndex?: number
    rowSpan?: number
    colSpan?: number
    header?: boolean
  }
}

export interface Grid {
  rows?: Array<{
    index?: number
    points: Polygon
  }>
}

/** PAGE XML 2019: RegionType (abstract) */
export interface RegionBase {
  /** @id (xsd:ID) */
  id: string
  /** Element name */
  kind: RegionKind
  /** Required Coords */
  coords: Polygon

  alternativeImages?: AlternativeImage[]
  userDefined?: UserDefined
  labels?: Labels[]
  roles?: Roles
  grid?: Grid
  textStyle?: TextStyleAttributes
  regions?: Region[]

  /** @custom */
  custom?: string
  /** @comments */
  comments?: string
  /** @continuation */
  continuation?: boolean
  /** @conf */
  confidence?: number
  numColours?: number
  embText?: boolean
  colourDepth?: string
  lineColour?: string
  lineSeparators?: boolean
  rows?: number
  columns?: number
  colour?: string
  penColour?: string
  borderPresent?: boolean
  serif?: boolean
  monospace?: boolean
  xHeight?: number
  textColourRgb?: number
  bgColourRgb?: number
}

export interface TextRegion extends RegionBase {
  kind: 'TextRegion'
  textLines?: TextLine[]
  textContentVariants?: TextContentVariant[]

  orientation?: number
  /** TextTypeSimpleType - subtype of TextRegion */
  type?: TextRegionSubtype | string
  leading?: number
  readingDirection?: string
  textLineOrder?: string
  readingOrientation?: number
  indented?: boolean
  align?: string
  primaryLanguage?: string
  secondaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  production?: string
}

export interface GraphicRegion extends RegionBase {
  kind: 'GraphicRegion'
  /** GraphicsTypeSimpleType - subtype of GraphicRegion */
  type?: GraphicRegionSubtype | string
  orientation?: number
  embText?: boolean
  numColour?: number
}

export interface ChartRegion extends RegionBase {
  kind: 'ChartRegion'
  /** ChartTypeSimpleType - subtype of ChartRegion */
  type?: ChartRegionSubtype | string
  orientation?: number
  embText?: boolean
  numColour?: number
}

export interface GenericRegion extends RegionBase {
  kind: Exclude<RegionKind, 'TextRegion' | 'GraphicRegion' | 'ChartRegion'>
  /** Generic type attribute for regions that support it */
  type?: string
  orientation?: number
}

export type Region = TextRegion | GraphicRegion | ChartRegion | GenericRegion

export function isTextRegion(region: Region): region is TextRegion {
  return region.kind === 'TextRegion'
}
