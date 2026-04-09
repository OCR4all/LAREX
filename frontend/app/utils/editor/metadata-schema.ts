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
export const userAttributeTypeEnum = z.enum(['xsd:string', 'xsd:integer', 'xsd:boolean', 'xsd:float'])
export const metadataItemTypeEnum = z.enum(['author', 'imageProperties', 'processingStep', 'other'])

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
const optionalInteger = optionalCoercedNumber(z.number().int())
const optionalBoolean = z.boolean().optional()

export const labelSchema = z.object({
  value: optionalString,
  type: optionalString,
  comments: optionalString
})

export const labelsSchema = z.object({
  externalModel: optionalString,
  externalId: optionalString,
  prefix: optionalString,
  comments: optionalString,
  labels: z.array(labelSchema).default([])
})

export const alternativeImageSchema = z.object({
  filename: optionalString,
  comments: optionalString,
  confidence: optionalCoercedNumber(z.number().min(0).max(1))
})

export const userAttributeSchema = z.object({
  name: optionalString,
  description: optionalString,
  type: userAttributeTypeEnum.optional(),
  value: optionalString
})

export const textStyleSchema = z.object({
  fontFamily: optionalString,
  serif: optionalBoolean,
  monospace: optionalBoolean,
  fontSize: optionalNumber,
  xHeight: optionalNumber,
  kerning: optionalNumber,
  textColour: optionalString,
  textColourRgb: optionalNumber,
  bgColour: optionalString,
  bgColourRgb: optionalNumber,
  reverseVideo: optionalBoolean,
  bold: optionalBoolean,
  italic: optionalBoolean,
  underlined: optionalBoolean,
  underlineStyle: optionalString,
  subscript: optionalBoolean,
  superscript: optionalBoolean,
  strikethrough: optionalBoolean,
  smallCaps: optionalBoolean,
  letterSpaced: optionalBoolean
})

export const metadataItemSchema = z.object({
  type: metadataItemTypeEnum.optional(),
  name: optionalString,
  value: optionalString,
  date: optionalString,
  labels: z.array(labelsSchema).default([])
})

export const tableCellRoleSchema = z.object({
  rowIndex: optionalInteger,
  columnIndex: optionalInteger,
  rowSpan: optionalInteger,
  colSpan: optionalInteger,
  header: optionalBoolean
})

export const gridRowSchema = z.object({
  index: optionalInteger,
  points: optionalString
})

export type LabelFormState = z.infer<typeof labelSchema>
export type LabelsFormState = z.infer<typeof labelsSchema>
export type AlternativeImageFormState = z.infer<typeof alternativeImageSchema>
export type UserAttributeFormState = z.infer<typeof userAttributeSchema>
export type TextStyleFormState = z.infer<typeof textStyleSchema>
export type MetadataItemFormState = z.infer<typeof metadataItemSchema>
export type TableCellRoleFormState = z.infer<typeof tableCellRoleSchema>
export type GridRowFormState = z.infer<typeof gridRowSchema>
type LanguageValue = typeof LANGUAGE_SIMPLE_TYPE_VALUES[number]
type ScriptValue = typeof SCRIPT_SIMPLE_TYPE_VALUES[number]

export function createEmptyTextStyleFormState(): TextStyleFormState {
  return {
    fontFamily: undefined,
    serif: undefined,
    monospace: undefined,
    fontSize: undefined,
    xHeight: undefined,
    kerning: undefined,
    textColour: undefined,
    textColourRgb: undefined,
    bgColour: undefined,
    bgColourRgb: undefined,
    reverseVideo: undefined,
    bold: undefined,
    italic: undefined,
    underlined: undefined,
    underlineStyle: undefined,
    subscript: undefined,
    superscript: undefined,
    strikethrough: undefined,
    smallCaps: undefined,
    letterSpaced: undefined
  }
}

export function createEmptyTableCellRoleFormState(): TableCellRoleFormState {
  return {
    rowIndex: undefined,
    columnIndex: undefined,
    rowSpan: undefined,
    colSpan: undefined,
    header: undefined
  }
}

function normalizeLanguageValue(value: string | undefined): LanguageValue | undefined {
  if (!value) return undefined
  return LANGUAGE_SIMPLE_TYPE_VALUES.includes(value as LanguageValue) ? value as LanguageValue : undefined
}

function normalizeScriptValue(value: string | undefined): ScriptValue | undefined {
  if (!value) return undefined
  return SCRIPT_SIMPLE_TYPE_VALUES.includes(value as ScriptValue) ? value as ScriptValue : undefined
}

function createTextStyleFormState(textStyle?: Partial<TextStyleFormState>): TextStyleFormState {
  return {
    ...createEmptyTextStyleFormState(),
    ...textStyle
  }
}

function createAlternativeImageFormState(image?: {
  filename?: string
  comments?: string
  confidence?: number
}): AlternativeImageFormState {
  return {
    filename: image?.filename ?? '',
    comments: image?.comments ?? '',
    confidence: image?.confidence
  }
}

function createLabelFormState(label?: {
  value?: string
  type?: string
  comments?: string
}): LabelFormState {
  return {
    value: label?.value ?? '',
    type: label?.type ?? '',
    comments: label?.comments ?? ''
  }
}

function createLabelsFormState(group?: {
  externalModel?: string
  externalId?: string
  prefix?: string
  comments?: string
  labels?: Array<{
    value?: string
    type?: string
    comments?: string
  }>
}): LabelsFormState {
  return {
    externalModel: group?.externalModel ?? '',
    externalId: group?.externalId ?? '',
    prefix: group?.prefix ?? '',
    comments: group?.comments ?? '',
    labels: (group?.labels ?? []).map(createLabelFormState)
  }
}

function createUserAttributeFormState(attribute?: {
  name?: string
  description?: string
  type?: 'xsd:string' | 'xsd:integer' | 'xsd:boolean' | 'xsd:float'
  value?: string
}): UserAttributeFormState {
  return {
    name: attribute?.name ?? '',
    description: attribute?.description ?? '',
    type: attribute?.type,
    value: attribute?.value ?? ''
  }
}

function createMetadataItemFormState(item?: {
  type?: 'author' | 'imageProperties' | 'processingStep' | 'other'
  name?: string
  value?: string
  date?: string
  labels?: Array<{
    externalModel?: string
    externalId?: string
    prefix?: string
    comments?: string
    labels?: Array<{
      value?: string
      type?: string
      comments?: string
    }>
  }>
}): MetadataItemFormState {
  return {
    type: item?.type,
    name: item?.name ?? '',
    value: item?.value ?? '',
    date: item?.date ?? '',
    labels: (item?.labels ?? []).map(createLabelsFormState)
  }
}

/**
 * Schema for document-level Metadata (MetadataType)
 */
export const documentMetadataSchema = z.object({
  creator: optionalString,
  created: z.string(), // Read-only
  lastChange: z.string(), // Read-only, auto-updated
  comments: optionalString,
  externalRef: optionalString,
  userDefinedAttributes: z.array(userAttributeSchema).default([]),
  items: z.array(metadataItemSchema).default([])
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
  conf: optionalCoercedNumber(z.number().min(0).max(1)),
  alternativeImages: z.array(alternativeImageSchema).default([]),
  labels: z.array(labelsSchema).default([]),
  userDefinedAttributes: z.array(userAttributeSchema).default([]),
  textStyle: textStyleSchema.default(createEmptyTextStyleFormState())
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
  production: productionEnum.optional(),
  alternativeImages: z.array(alternativeImageSchema).default([]),
  labels: z.array(labelsSchema).default([]),
  userDefinedAttributes: z.array(userAttributeSchema).default([]),
  textStyle: textStyleSchema.default(createEmptyTextStyleFormState())
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
  continuation: z.boolean().optional(),
  orientation: optionalCoercedNumber(z.number().min(-180).max(180)),
  numColours: optionalInteger,
  embText: optionalBoolean,
  colourDepth: optionalString,
  lineColour: optionalString,
  lineSeparators: optionalBoolean,
  rows: optionalInteger,
  columns: optionalInteger,
  colour: optionalString,
  penColour: optionalString,
  borderPresent: optionalBoolean,
  textColourRgb: optionalNumber,
  bgColourRgb: optionalNumber,
  tableCellRole: tableCellRoleSchema.default(createEmptyTableCellRoleFormState()),
  gridRows: z.array(gridRowSchema).default([]),
  alternativeImages: z.array(alternativeImageSchema).default([]),
  labels: z.array(labelsSchema).default([]),
  userDefinedAttributes: z.array(userAttributeSchema).default([]),
  textStyle: textStyleSchema.default(createEmptyTextStyleFormState())
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
  index: optionalCoercedNumber(z.number().int().min(0)),
  alternativeImages: z.array(alternativeImageSchema).default([]),
  labels: z.array(labelsSchema).default([]),
  userDefinedAttributes: z.array(userAttributeSchema).default([]),
  textStyle: textStyleSchema.default(createEmptyTextStyleFormState())
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
  userDefined?: {
    attributes?: Array<{
      name?: string
      description?: string
      type?: 'xsd:string' | 'xsd:integer' | 'xsd:boolean' | 'xsd:float'
      value?: string
    }>
  }
  items?: Array<{
    type?: 'author' | 'imageProperties' | 'processingStep' | 'other'
    name?: string
    value?: string
    date?: string
    labels?: Array<{
      externalModel?: string
      externalId?: string
      prefix?: string
      comments?: string
      labels?: Array<{
        value: string
        type?: string
        comments?: string
      }>
    }>
  }>
}): DocumentMetadataFormState {
  return {
    creator: metadata.creator ?? '',
    created: metadata.created ?? '',
    lastChange: metadata.lastChange ?? '',
    comments: metadata.comments ?? '',
    externalRef: metadata.externalRef ?? '',
    userDefinedAttributes: (metadata.userDefined?.attributes ?? []).map(attribute => createUserAttributeFormState(attribute)),
    items: (metadata.items ?? []).map(item => createMetadataItemFormState(item))
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
  alternativeImages?: Array<{
    filename: string
    comments?: string
    confidence?: number
  }>
  labels?: Array<{
    externalModel?: string
    externalId?: string
    prefix?: string
    comments?: string
    labels?: Array<{
      value: string
      type?: string
      comments?: string
    }>
  }>
  userDefined?: {
    attributes?: Array<{
      name?: string
      description?: string
      type?: 'xsd:string' | 'xsd:integer' | 'xsd:boolean' | 'xsd:float'
      value?: string
    }>
  }
  textStyle?: {
    fontFamily?: string
    serif?: boolean
    monospace?: boolean
    fontSize?: number
    xHeight?: number
    kerning?: number
    textColour?: string
    textColourRgb?: number
    bgColour?: string
    bgColourRgb?: number
    reverseVideo?: boolean
    bold?: boolean
    italic?: boolean
    underlined?: boolean
    underlineStyle?: string
    subscript?: boolean
    superscript?: boolean
    strikethrough?: boolean
    smallCaps?: boolean
    letterSpaced?: boolean
  }
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
    primaryLanguage: normalizeLanguageValue(page.primaryLanguage),
    secondaryLanguage: normalizeLanguageValue(page.secondaryLanguage),
    primaryScript: normalizeScriptValue(page.primaryScript),
    secondaryScript: normalizeScriptValue(page.secondaryScript),
    readingDirection: page.readingDirection,
    textLineOrder: page.textLineOrder,
    conf: page.conf,
    alternativeImages: (page.alternativeImages ?? []).map(image => createAlternativeImageFormState(image)),
    labels: (page.labels ?? []).map(group => createLabelsFormState(group)),
    userDefinedAttributes: (page.userDefined?.attributes ?? []).map(attribute => createUserAttributeFormState(attribute)),
    textStyle: createTextStyleFormState(page.textStyle)
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
  alternativeImages?: Array<{
    filename: string
    comments?: string
    confidence?: number
  }>
  labels?: Array<{
    externalModel?: string
    externalId?: string
    prefix?: string
    comments?: string
    labels?: Array<{
      value: string
      type?: string
      comments?: string
    }>
  }>
  userDefined?: {
    attributes?: Array<{
      name?: string
      description?: string
      type?: 'xsd:string' | 'xsd:integer' | 'xsd:boolean' | 'xsd:float'
      value?: string
    }>
  }
  textStyle?: {
    fontFamily?: string
    serif?: boolean
    monospace?: boolean
    fontSize?: number
    xHeight?: number
    kerning?: number
    textColour?: string
    textColourRgb?: number
    bgColour?: string
    bgColourRgb?: number
    reverseVideo?: boolean
    bold?: boolean
    italic?: boolean
    underlined?: boolean
    underlineStyle?: string
    subscript?: boolean
    superscript?: boolean
    strikethrough?: boolean
    smallCaps?: boolean
    letterSpaced?: boolean
  }
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
    primaryLanguage: normalizeLanguageValue(region.primaryLanguage),
    secondaryLanguage: normalizeLanguageValue(region.secondaryLanguage),
    primaryScript: normalizeScriptValue(region.primaryScript),
    secondaryScript: normalizeScriptValue(region.secondaryScript),
    production: region.production as TextRegionMetadataFormState['production'],
    alternativeImages: (region.alternativeImages ?? []).map(image => createAlternativeImageFormState(image)),
    labels: (region.labels ?? []).map(group => createLabelsFormState(group)),
    userDefinedAttributes: (region.userDefined?.attributes ?? []).map(attribute => createUserAttributeFormState(attribute)),
    textStyle: createTextStyleFormState(region.textStyle)
  }
}

export function createGenericRegionMetadataFormState(region: {
  id: string
  kind: string
  type?: string
  custom?: string
  comments?: string
  continuation?: boolean
  orientation?: number
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
  textColourRgb?: number
  bgColourRgb?: number
  roles?: {
    tableCellRole?: {
      rowIndex?: number
      columnIndex?: number
      rowSpan?: number
      colSpan?: number
      header?: boolean
    }
  }
  grid?: {
    rows?: Array<{
      index?: number
      points?: {
        points?: Array<[number, number]>
      }
    }>
  }
  alternativeImages?: Array<{
    filename: string
    comments?: string
    confidence?: number
  }>
  labels?: Array<{
    externalModel?: string
    externalId?: string
    prefix?: string
    comments?: string
    labels?: Array<{
      value: string
      type?: string
      comments?: string
    }>
  }>
  userDefined?: {
    attributes?: Array<{
      name?: string
      description?: string
      type?: 'xsd:string' | 'xsd:integer' | 'xsd:boolean' | 'xsd:float'
      value?: string
    }>
  }
  textStyle?: {
    fontFamily?: string
    serif?: boolean
    monospace?: boolean
    fontSize?: number
    xHeight?: number
    kerning?: number
    textColour?: string
    textColourRgb?: number
    bgColour?: string
    bgColourRgb?: number
    reverseVideo?: boolean
    bold?: boolean
    italic?: boolean
    underlined?: boolean
    underlineStyle?: string
    subscript?: boolean
    superscript?: boolean
    strikethrough?: boolean
    smallCaps?: boolean
    letterSpaced?: boolean
  }
}): GenericRegionMetadataFormState {
  const gridRows = region.grid?.rows?.map<GridRowFormState>(row => ({
    index: row.index,
    points: row.points?.points?.map(point => `${point[0]},${point[1]}`).join(' ')
  })) ?? []

  const tableCellRole: TableCellRoleFormState = {
    ...createEmptyTableCellRoleFormState(),
    rowIndex: region.roles?.tableCellRole?.rowIndex,
    columnIndex: region.roles?.tableCellRole?.columnIndex,
    rowSpan: region.roles?.tableCellRole?.rowSpan,
    colSpan: region.roles?.tableCellRole?.colSpan,
    header: region.roles?.tableCellRole?.header
  }

  return {
    id: region.id,
    kind: region.kind,
    type: region.type ?? '',
    custom: region.custom ?? '',
    comments: region.comments ?? '',
    continuation: region.continuation,
    orientation: region.orientation,
    numColours: region.numColours,
    embText: region.embText,
    colourDepth: region.colourDepth ?? '',
    lineColour: region.lineColour ?? '',
    lineSeparators: region.lineSeparators,
    rows: region.rows,
    columns: region.columns,
    colour: region.colour ?? '',
    penColour: region.penColour ?? '',
    borderPresent: region.borderPresent,
    textColourRgb: region.textColourRgb,
    bgColourRgb: region.bgColourRgb,
    tableCellRole,
    gridRows,
    alternativeImages: (region.alternativeImages ?? []).map(image => createAlternativeImageFormState(image)),
    labels: (region.labels ?? []).map(group => createLabelsFormState(group)),
    userDefinedAttributes: (region.userDefined?.attributes ?? []).map(attribute => createUserAttributeFormState(attribute)),
    textStyle: createTextStyleFormState(region.textStyle)
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
  alternativeImages?: Array<{
    filename: string
    comments?: string
    confidence?: number
  }>
  labels?: Array<{
    externalModel?: string
    externalId?: string
    prefix?: string
    comments?: string
    labels?: Array<{
      value: string
      type?: string
      comments?: string
    }>
  }>
  userDefined?: {
    attributes?: Array<{
      name?: string
      description?: string
      type?: 'xsd:string' | 'xsd:integer' | 'xsd:boolean' | 'xsd:float'
      value?: string
    }>
  }
  textStyle?: {
    fontFamily?: string
    serif?: boolean
    monospace?: boolean
    fontSize?: number
    xHeight?: number
    kerning?: number
    textColour?: string
    textColourRgb?: number
    bgColour?: string
    bgColourRgb?: number
    reverseVideo?: boolean
    bold?: boolean
    italic?: boolean
    underlined?: boolean
    underlineStyle?: string
    subscript?: boolean
    superscript?: boolean
    strikethrough?: boolean
    smallCaps?: boolean
    letterSpaced?: boolean
  }
}): TextLineMetadataFormState {
  return {
    id: textLine.id,
    primaryLanguage: normalizeLanguageValue(textLine.primaryLanguage),
    primaryScript: normalizeScriptValue(textLine.primaryScript),
    secondaryScript: normalizeScriptValue(textLine.secondaryScript),
    readingDirection: textLine.readingDirection as TextLineMetadataFormState['readingDirection'],
    production: textLine.production as TextLineMetadataFormState['production'],
    custom: textLine.custom ?? '',
    comments: textLine.comments ?? '',
    index: textLine.index,
    alternativeImages: (textLine.alternativeImages ?? []).map(image => createAlternativeImageFormState(image)),
    labels: (textLine.labels ?? []).map(group => createLabelsFormState(group)),
    userDefinedAttributes: (textLine.userDefined?.attributes ?? []).map(attribute => createUserAttributeFormState(attribute)),
    textStyle: createTextStyleFormState(textLine.textStyle)
  }
}

export function createBaselineMetadataFormState(baseline: {
  conf?: number
}): BaselineMetadataFormState {
  return {
    conf: baseline.conf
  }
}
