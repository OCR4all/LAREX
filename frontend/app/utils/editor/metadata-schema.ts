/**
 * Zod validation schemas for PAGE XML metadata forms
 * Based on PAGE XML 2019 schema specification
 */
import { z } from 'zod'
import { LANGUAGE_SIMPLE_TYPE_VALUES, SCRIPT_SIMPLE_TYPE_VALUES } from '@/utils/editor/page-xml-enums'

export const readingDirectionEnum = z.enum(['left-to-right', 'right-to-left', 'top-to-bottom', 'bottom-to-top'])
export const textLineOrderEnum = z.enum(['top-to-bottom', 'bottom-to-top', 'left-to-right', 'right-to-left'])
export const productionEnum = z.enum(['printed', 'handwritten-cursive', 'handwritten-printscript', 'medieval-manuscript', 'typewritten'])
export const pageTypeEnum = z.enum(['front-cover', 'back-cover', 'title', 'table-of-contents', 'index', 'content', 'blank', 'other'])
export const alignEnum = z.enum(['left', 'centre', 'right', 'justify'])
export const resolutionUnitEnum = z.enum(['PPI', 'PPCM', 'other'])

const optionalString = z.string().optional().transform(val => val?.trim() || undefined)
const optionalEnumValue = <TValues extends readonly [string, ...string[]]>(values: TValues) => z.preprocess((value) => {
  if (value === undefined || value === null) return undefined
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return undefined
    return trimmed
  }
  return value
}, z.enum(values).optional())
const optionalLanguage = optionalEnumValue(LANGUAGE_SIMPLE_TYPE_VALUES)
const optionalScript = optionalEnumValue(SCRIPT_SIMPLE_TYPE_VALUES)
const optionalCoercedNumber = (schema: z.ZodNumber) => z.preprocess((value) => {
  if (value === undefined || value === null) return undefined
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return undefined
    return Number(trimmed)
  }
  return value
}, schema.optional())
const optionalNumber = optionalCoercedNumber(z.number())

/**
 * Schema for document-level Metadata (MetadataType)
 */
export const documentMetadataSchema = z.object({
  creator: optionalString,
  created: z.string(), // Read-only
  lastChange: z.string(), // Read-only, auto-updated
  comments: optionalString,
  externalRef: optionalString
})

export type DocumentMetadataFormState = z.infer<typeof documentMetadataSchema>

/**
 * Schema for Page attributes (PageType)
 */
export const pageMetadataSchema = z.object({
  imageFilename: z.string(),
  imageWidth: z.number(),
  imageHeight: z.number(),
  imageXResolution: optionalNumber,
  imageYResolution: optionalNumber,
  imageResolutionUnit: resolutionUnitEnum.optional(),
  custom: optionalString,
  orientation: optionalCoercedNumber(z.number().min(-180).max(180)),
  type: pageTypeEnum.optional(),
  primaryLanguage: optionalLanguage,
  secondaryLanguage: optionalLanguage,
  primaryScript: optionalScript,
  secondaryScript: optionalScript,
  readingDirection: readingDirectionEnum.optional(),
  textLineOrder: textLineOrderEnum.optional(),
  conf: optionalCoercedNumber(z.number().min(0).max(1))
})

export type PageMetadataFormState = z.infer<typeof pageMetadataSchema>

/**
 * Schema for TextRegion attributes (TextRegionType extends RegionType)
 */
export const textRegionMetadataSchema = z.object({
  id: z.string(), // Read-only
  kind: z.string(), // Read-only
  custom: optionalString,
  comments: optionalString,
  continuation: z.boolean().optional(),
  orientation: z.coerce.number().min(-180).max(180).optional(),
  type: optionalString, // TextTypeSimpleType - paragraph, heading, etc.
  leading: optionalCoercedNumber(z.number().int()),
  readingDirection: readingDirectionEnum.optional(),
  textLineOrder: textLineOrderEnum.optional(),
  readingOrientation: optionalCoercedNumber(z.number().min(-180).max(180)),
  indented: z.boolean().optional(),
  align: alignEnum.optional(),
  primaryLanguage: optionalLanguage,
  secondaryLanguage: optionalLanguage,
  primaryScript: optionalScript,
  secondaryScript: optionalScript,
  production: productionEnum.optional()
})

export type TextRegionMetadataFormState = z.infer<typeof textRegionMetadataSchema>

/**
 * Schema for generic Region attributes (RegionType base only)
 * Includes type field for regions that support subtypes (GraphicRegion, ChartRegion)
 */
export const genericRegionMetadataSchema = z.object({
  id: z.string(), // Read-only
  kind: z.string(), // Read-only
  type: optionalString, // Subtype for GraphicRegion, ChartRegion, etc.
  custom: optionalString,
  comments: optionalString,
  continuation: z.boolean().optional()
})

export type GenericRegionMetadataFormState = z.infer<typeof genericRegionMetadataSchema>

/**
 * Schema for TextLine attributes (TextLineType)
 */
export const textLineMetadataSchema = z.object({
  id: z.string(), // Read-only
  primaryLanguage: optionalLanguage,
  primaryScript: optionalScript,
  secondaryScript: optionalScript,
  readingDirection: readingDirectionEnum.optional(),
  production: productionEnum.optional(),
  custom: optionalString,
  comments: optionalString,
  index: optionalCoercedNumber(z.number().int().min(0))
})

export type TextLineMetadataFormState = z.infer<typeof textLineMetadataSchema>

/**
 * Schema for Baseline - very minimal, only conf is editable
 */
export const baselineMetadataSchema = z.object({
  conf: optionalCoercedNumber(z.number().min(0).max(1))
})

export type BaselineMetadataFormState = z.infer<typeof baselineMetadataSchema>

export function createDocumentMetadataFormState(metadata: {
  creator?: string
  created?: string
  lastChange?: string
  comments?: string
  externalRef?: string
}): DocumentMetadataFormState {
  return {
    creator: metadata.creator ?? '',
    created: metadata.created ?? '',
    lastChange: metadata.lastChange ?? '',
    comments: metadata.comments ?? '',
    externalRef: metadata.externalRef ?? ''
  }
}

export function createPageMetadataFormState(page: {
  imageFilename: string
  imageWidth: number
  imageHeight: number
  imageXResolution?: number
  imageYResolution?: number
  imageResolutionUnit?: 'PPI' | 'PPCM' | 'other'
  custom?: string
  orientation?: number
  type?: 'front-cover' | 'back-cover' | 'title' | 'table-of-contents' | 'index' | 'content' | 'blank' | 'other'
  primaryLanguage?: string
  secondaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  readingDirection?: 'left-to-right' | 'right-to-left' | 'top-to-bottom' | 'bottom-to-top'
  textLineOrder?: 'top-to-bottom' | 'bottom-to-top' | 'left-to-right' | 'right-to-left'
  conf?: number
}): PageMetadataFormState {
  return {
    imageFilename: page.imageFilename,
    imageWidth: page.imageWidth,
    imageHeight: page.imageHeight,
    imageXResolution: page.imageXResolution,
    imageYResolution: page.imageYResolution,
    imageResolutionUnit: page.imageResolutionUnit,
    custom: page.custom ?? '',
    orientation: page.orientation,
    type: page.type,
    primaryLanguage: page.primaryLanguage,
    secondaryLanguage: page.secondaryLanguage,
    primaryScript: page.primaryScript,
    secondaryScript: page.secondaryScript,
    readingDirection: page.readingDirection,
    textLineOrder: page.textLineOrder,
    conf: page.conf
  }
}

export function createTextRegionMetadataFormState(region: {
  id: string
  kind: string
  custom?: string
  comments?: string
  continuation?: boolean
  orientation?: number
  type?: string
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
}): TextRegionMetadataFormState {
  return {
    id: region.id,
    kind: region.kind,
    custom: region.custom ?? '',
    comments: region.comments ?? '',
    continuation: region.continuation,
    orientation: region.orientation,
    type: region.type ?? '',
    leading: region.leading,
    readingDirection: region.readingDirection as TextRegionMetadataFormState['readingDirection'],
    textLineOrder: region.textLineOrder as TextRegionMetadataFormState['textLineOrder'],
    readingOrientation: region.readingOrientation,
    indented: region.indented,
    align: region.align as TextRegionMetadataFormState['align'],
    primaryLanguage: region.primaryLanguage,
    secondaryLanguage: region.secondaryLanguage,
    primaryScript: region.primaryScript,
    secondaryScript: region.secondaryScript,
    production: region.production as TextRegionMetadataFormState['production']
  }
}

export function createGenericRegionMetadataFormState(region: {
  id: string
  kind: string
  type?: string
  custom?: string
  comments?: string
  continuation?: boolean
}): GenericRegionMetadataFormState {
  return {
    id: region.id,
    kind: region.kind,
    type: region.type ?? '',
    custom: region.custom ?? '',
    comments: region.comments ?? '',
    continuation: region.continuation
  }
}

export function createTextLineMetadataFormState(textLine: {
  id: string
  primaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  readingDirection?: string
  production?: string
  custom?: string
  comments?: string
  index?: number
}): TextLineMetadataFormState {
  return {
    id: textLine.id,
    primaryLanguage: textLine.primaryLanguage,
    primaryScript: textLine.primaryScript,
    secondaryScript: textLine.secondaryScript,
    readingDirection: textLine.readingDirection as TextLineMetadataFormState['readingDirection'],
    production: textLine.production as TextLineMetadataFormState['production'],
    custom: textLine.custom ?? '',
    comments: textLine.comments ?? '',
    index: textLine.index
  }
}

export function createBaselineMetadataFormState(baseline: {
  conf?: number
}): BaselineMetadataFormState {
  return {
    conf: baseline.conf
  }
}
