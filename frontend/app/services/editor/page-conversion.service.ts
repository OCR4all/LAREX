/**
 * Service for converting between backend PageDto and frontend PcGts formats.
 *
 * Backend sends world coordinates as [x, y] arrays in the range [-1, 1].
 * Frontend uses [number, number][] for polygon points.
 */

import {
  PcGts,
  Metadata,
  Page,
  Polygon,
  Polyline,
  TextLine,
  Word,
  Glyph,
  TextContentVariant,
  type AlternativeImage,
  type GraphemeElement,
  type Graphemes,
  type Labels,
  type Layer,
  type ReadingOrder,
  type Region,
  type RegionKind,
  type Relation,
  type TextRegion,
  type TextStyleAttributes,
  type UserAttribute,
  type UserDefined
} from '@/models/editor'

export type PointDto = [number, number]

export interface PolygonDto {
  points: PointDto[]
  confidence?: number
}

export interface AlternativeImageDto {
  filename: string
  comments?: string
  confidence?: number
}

export interface LabelDto {
  value?: string
  type?: string
  comments?: string
}

export interface LabelsDto {
  externalModel?: string
  externalId?: string
  prefix?: string
  comments?: string
  labels?: LabelDto[]
}

export interface UserAttributeDto {
  name?: string
  description?: string
  type?: 'xsd:string' | 'xsd:integer' | 'xsd:boolean' | 'xsd:float'
  value?: string
}

export interface UserDefinedDto {
  attributes?: UserAttributeDto[]
}

export interface TextStyleDto {
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

export interface MetadataItemDto {
  type?: string
  name?: string
  value?: string
  date?: string
  labels?: LabelsDto[]
}

export interface TextContentVariantDto {
  unicode?: string
  plainText?: string
  index?: number
  confidence?: number
  comments?: string
  dataType?: string
  dataTypeDetails?: string
}

export interface GraphemeElementDto {
  kind?: 'grapheme' | 'nonPrintingChar' | 'graphemeGroup'
  id?: string
  index?: number
  charType?: string
  ligature?: boolean
  custom?: string
  comments?: string
  coords?: PolygonDto
  textContentVariants?: TextContentVariantDto[]
  labels?: LabelsDto[]
  members?: GraphemeElementDto[]
}

export interface GraphemesDto {
  elements?: GraphemeElementDto[]
}

export interface GlyphDto {
  id: string
  coords?: PolygonDto
  textContentVariants?: TextContentVariantDto[]
  alternativeImages?: AlternativeImageDto[]
  labels?: LabelsDto[]
  userDefined?: UserDefinedDto
  textStyle?: TextStyleDto
  graphemes?: GraphemesDto
  ligature?: boolean
  symbol?: boolean
  script?: string
  production?: string
  confidence?: number
  custom?: string
  comments?: string
}

export interface WordDto {
  id: string
  coords?: PolygonDto
  textContentVariants?: TextContentVariantDto[]
  glyphs?: GlyphDto[]
  alternativeImages?: AlternativeImageDto[]
  labels?: LabelsDto[]
  userDefined?: UserDefinedDto
  textStyle?: TextStyleDto
  language?: string
  primaryScript?: string
  secondaryScript?: string
  script?: string
  readingDirection?: string
  production?: string
  confidence?: number
  custom?: string
  comments?: string
}

export interface TextLineDto {
  id: string
  coords?: PolygonDto
  baseline?: PolygonDto
  textContentVariants?: TextContentVariantDto[]
  words?: WordDto[]
  alternativeImages?: AlternativeImageDto[]
  labels?: LabelsDto[]
  userDefined?: UserDefinedDto
  textStyle?: TextStyleDto
  bold?: boolean
  italic?: boolean
  underlined?: boolean
  underlineStyle?: string
  subscript?: boolean
  superscript?: boolean
  strikethrough?: boolean
  smallCaps?: boolean
  letterSpaced?: boolean
  primaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  readingDirection?: string
  production?: string
  custom?: string
  comments?: string
  index?: number
  confidence?: number
}

export interface TableCellRoleDto {
  rowIndex?: number
  columnIndex?: number
  rowSpan?: number
  colSpan?: number
  header?: boolean
}

export interface RolesDto {
  tableCellRole?: TableCellRoleDto
}

export interface GridPointsDto {
  index?: number
  points?: PolygonDto
}

export interface GridDto {
  rows?: GridPointsDto[]
}

export interface RegionDto {
  id: string
  kind: string
  coords?: PolygonDto
  textLines?: TextLineDto[]
  textContentVariants?: TextContentVariantDto[]
  alternativeImages?: AlternativeImageDto[]
  labels?: LabelsDto[]
  userDefined?: UserDefinedDto
  roles?: RolesDto
  grid?: GridDto
  textStyle?: TextStyleDto
  type?: string
  orientation?: number
  textColour?: string
  bgColour?: string
  reverseVideo?: boolean
  fontSize?: number
  fontFamily?: string
  serif?: boolean
  monospace?: boolean
  xHeight?: number
  leading?: number
  kerning?: number
  align?: string
  textColourRgb?: number
  bgColourRgb?: number
  readingDirection?: string
  readingOrientation?: number
  textLineOrder?: string
  indented?: boolean
  primaryLanguage?: string
  secondaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  production?: string
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
  nestedRegions?: RegionDto[]
  confidence?: number
  custom?: string
  comments?: string
  continuation?: boolean
  labelIds?: string[]
}

export interface GroupMemberDto {
  type: 'regionRef' | 'nestedGroup'
}

export interface RegionRefDto extends GroupMemberDto {
  type: 'regionRef'
  id?: string
  regionRef?: string
  index?: number
}

export interface NestedGroupDto extends GroupMemberDto {
  type: 'nestedGroup'
  group: GroupDto
}

export interface GroupDto {
  id?: string
  ordered?: boolean
  index?: number
  caption?: string
  groupType?: string
  regionRef?: string
  members?: GroupMemberDto[]
  continuation?: boolean
  userDefined?: UserDefinedDto
  labels?: LabelsDto[]
  custom?: string
  comments?: string
}

export interface ReadingOrderDto {
  root?: GroupDto
  confidence?: number
}

export interface LayerDto {
  id?: string
  zIndex?: number
  caption?: string
  regionRefs?: string[]
}

export interface LayersDto {
  layers?: LayerDto[]
}

export interface RelationDto {
  id?: string
  type?: string
  sourceRegionRef?: string
  targetRegionRef?: string
  custom?: string
  comments?: string
  labels?: LabelsDto[]
}

export interface RelationsDto {
  relations?: RelationDto[]
}

export interface MetadataDto {
  creator?: string
  created?: string
  lastChange?: string
  comments?: string
  externalRef?: string
  userDefined?: UserDefinedDto
  items?: MetadataItemDto[]
}

export interface PageDto {
  imageFilename?: string
  imageWidth: number
  imageHeight: number
  imageXResolution?: number
  imageYResolution?: number
  imageResolutionUnit?: string
  metadata?: MetadataDto
  pcGtsId?: string
  type?: string
  custom?: string
  orientation?: number
  primaryLanguage?: string
  secondaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  readingDirection?: string
  textLineOrder?: string
  confidence?: number
  border?: PolygonDto
  printSpace?: PolygonDto
  regions?: RegionDto[]
  readingOrder?: ReadingOrderDto
  alternativeImages?: AlternativeImageDto[]
  labels?: LabelsDto[]
  userDefined?: UserDefinedDto
  textStyle?: TextStyleDto
  layers?: LayersDto
  relations?: RelationsDto
  formatVersion?: string
  labelIds?: string[]
}

function convertPolygonFromDto(dto?: PolygonDto): Polygon | undefined {
  if (!dto?.points) return undefined
  return new Polygon(dto.points)
}

function convertPolygonToDto(polygon?: Polygon): PolygonDto | undefined {
  if (!polygon) return undefined
  return {
    points: polygon.points,
    confidence: undefined
  }
}

function undefinedIfBlank(value?: unknown): string | undefined {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed.length > 0 ? trimmed : undefined
  }
  if (value && typeof value === 'object' && 'value' in value) {
    return undefinedIfBlank((value as { value?: unknown }).value)
  }
  return undefined
}

function convertAlternativeImagesFromDto(images?: AlternativeImageDto[]): AlternativeImage[] | undefined {
  if (!images?.length) return undefined
  const converted = images
    .filter(img => typeof img.filename === 'string' && img.filename.length > 0)
    .map(img => ({
      filename: img.filename,
      comments: undefinedIfBlank(img.comments),
      confidence: img.confidence
    }))
  return converted.length > 0 ? converted : undefined
}

function convertAlternativeImagesToDto(images?: AlternativeImage[]): AlternativeImageDto[] | undefined {
  if (!images?.length) return undefined
  const converted = images
    .filter(img => typeof img.filename === 'string' && img.filename.length > 0)
    .map(img => ({
      filename: img.filename,
      comments: undefinedIfBlank(img.comments),
      confidence: img.confidence
    }))
  return converted.length > 0 ? converted : undefined
}

function convertLabelsFromDto(groups?: LabelsDto[]): Labels[] | undefined {
  if (!groups?.length) return undefined
  const converted = groups.map(group => ({
    externalModel: undefinedIfBlank(group.externalModel),
    externalId: undefinedIfBlank(group.externalId),
    prefix: undefinedIfBlank(group.prefix),
    comments: undefinedIfBlank(group.comments),
    labels: group.labels
      ?.filter(label => undefinedIfBlank(label.value))
      .map(label => ({
        value: label.value?.trim() ?? '',
        type: undefinedIfBlank(label.type),
        comments: undefinedIfBlank(label.comments)
      }))
  }))
  return converted.length > 0 ? converted : undefined
}

function convertLabelsToDto(groups?: Labels[]): LabelsDto[] | undefined {
  if (!groups?.length) return undefined
  const converted = groups.map(group => ({
    externalModel: undefinedIfBlank(group.externalModel),
    externalId: undefinedIfBlank(group.externalId),
    prefix: undefinedIfBlank(group.prefix),
    comments: undefinedIfBlank(group.comments),
    labels: group.labels
      ?.filter(label => undefinedIfBlank(label.value))
      .map(label => ({
        value: label.value?.trim(),
        type: undefinedIfBlank(label.type),
        comments: undefinedIfBlank(label.comments)
      }))
  }))
  return converted.length > 0 ? converted : undefined
}

function convertUserDefinedFromDto(userDefined?: UserDefinedDto): UserDefined | undefined {
  if (!userDefined?.attributes?.length) return undefined
  const attributes = userDefined.attributes
    .filter(attr => undefinedIfBlank(attr.name))
    .map(attr => ({
      name: undefinedIfBlank(attr.name),
      description: undefinedIfBlank(attr.description),
      type: attr.type,
      value: undefinedIfBlank(attr.value)
    })) as UserAttribute[]
  return attributes.length > 0 ? { attributes } : undefined
}

function convertUserDefinedToDto(userDefined?: UserDefined): UserDefinedDto | undefined {
  if (!userDefined?.attributes?.length) return undefined
  const attributes = userDefined.attributes
    .filter(attr => undefinedIfBlank(attr.name))
    .map(attr => ({
      name: undefinedIfBlank(attr.name),
      description: undefinedIfBlank(attr.description),
      type: attr.type,
      value: undefinedIfBlank(attr.value)
    })) as UserAttributeDto[]
  return attributes.length > 0 ? { attributes } : undefined
}

function convertTextStyleFromDto(style?: TextStyleDto): TextStyleAttributes | undefined {
  if (!style) return undefined
  const converted: TextStyleAttributes = {
    fontFamily: undefinedIfBlank(style.fontFamily),
    serif: style.serif,
    monospace: style.monospace,
    fontSize: style.fontSize,
    xHeight: style.xHeight,
    kerning: style.kerning,
    textColour: undefinedIfBlank(style.textColour),
    textColourRgb: style.textColourRgb,
    bgColour: undefinedIfBlank(style.bgColour),
    bgColourRgb: style.bgColourRgb,
    reverseVideo: style.reverseVideo,
    bold: style.bold,
    italic: style.italic,
    underlined: style.underlined,
    underlineStyle: undefinedIfBlank(style.underlineStyle),
    subscript: style.subscript,
    superscript: style.superscript,
    strikethrough: style.strikethrough,
    smallCaps: style.smallCaps,
    letterSpaced: style.letterSpaced
  }
  return Object.values(converted).some(value => value !== undefined) ? converted : undefined
}

function convertTextStyleToDto(style?: TextStyleAttributes): TextStyleDto | undefined {
  if (!style) return undefined
  const converted: TextStyleDto = {
    fontFamily: undefinedIfBlank(style.fontFamily),
    serif: style.serif,
    monospace: style.monospace,
    fontSize: style.fontSize,
    xHeight: style.xHeight,
    kerning: style.kerning,
    textColour: undefinedIfBlank(style.textColour),
    textColourRgb: style.textColourRgb,
    bgColour: undefinedIfBlank(style.bgColour),
    bgColourRgb: style.bgColourRgb,
    reverseVideo: style.reverseVideo,
    bold: style.bold,
    italic: style.italic,
    underlined: style.underlined,
    underlineStyle: undefinedIfBlank(style.underlineStyle),
    subscript: style.subscript,
    superscript: style.superscript,
    strikethrough: style.strikethrough,
    smallCaps: style.smallCaps,
    letterSpaced: style.letterSpaced
  }
  return Object.values(converted).some(value => value !== undefined) ? converted : undefined
}

function convertTextContentVariantFromDto(dto: TextContentVariantDto): TextContentVariant {
  return new TextContentVariant(
    dto.unicode ?? '',
    dto.plainText,
    dto.confidence,
    dto.index,
    dto.dataType,
    dto.dataTypeDetails,
    dto.comments
  )
}

function convertTextContentVariantToDto(te: TextContentVariant): TextContentVariantDto {
  return {
    unicode: te.unicode,
    plainText: te.plainText,
    index: te.index,
    confidence: te.confidence,
    comments: te.comments,
    dataType: te.dataType,
    dataTypeDetails: te.dataTypeDetails
  }
}

function convertGraphemeElementsFromDto(elements?: GraphemeElementDto[]): GraphemeElement[] | undefined {
  if (!elements?.length) return undefined
  return elements.map(element => ({
    kind: element.kind ?? 'grapheme',
    id: undefinedIfBlank(element.id),
    index: element.index,
    charType: undefinedIfBlank(element.charType),
    ligature: element.ligature,
    custom: undefinedIfBlank(element.custom),
    comments: undefinedIfBlank(element.comments),
    coords: convertPolygonFromDto(element.coords),
    textContentVariants: element.textContentVariants?.map(convertTextContentVariantFromDto),
    labels: convertLabelsFromDto(element.labels),
    members: convertGraphemeElementsFromDto(element.members)
  })) as GraphemeElement[]
}

function convertGraphemeElementsToDto(elements?: GraphemeElement[]): GraphemeElementDto[] | undefined {
  if (!elements?.length) return undefined
  return elements.map(element => ({
    kind: element.kind,
    id: undefinedIfBlank(element.id),
    index: element.index,
    charType: undefinedIfBlank(element.charType),
    ligature: element.ligature,
    custom: undefinedIfBlank(element.custom),
    comments: undefinedIfBlank(element.comments),
    coords: convertPolygonToDto(element.coords),
    textContentVariants: element.textContentVariants?.map(convertTextContentVariantToDto),
    labels: convertLabelsToDto(element.labels),
    members: convertGraphemeElementsToDto(element.members)
  }))
}

function convertGlyphFromDto(dto: GlyphDto): Glyph {
  const textContentVariants = dto.textContentVariants?.map(convertTextContentVariantFromDto)
  return new Glyph(
    dto.id,
    convertPolygonFromDto(dto.coords)!,
    textContentVariants?.[0]?.unicode,
    textContentVariants,
    dto.ligature,
    dto.symbol,
    undefinedIfBlank(dto.script),
    undefinedIfBlank(dto.production) as Glyph['production'],
    dto.confidence,
    undefined,
    undefinedIfBlank(dto.custom),
    undefinedIfBlank(dto.comments),
    convertAlternativeImagesFromDto(dto.alternativeImages),
    convertLabelsFromDto(dto.labels),
    convertUserDefinedFromDto(dto.userDefined),
    convertTextStyleFromDto(dto.textStyle),
    dto.graphemes ? { elements: convertGraphemeElementsFromDto(dto.graphemes.elements) } : undefined
  )
}

function convertGlyphToDto(glyph: Glyph): GlyphDto {
  const glyphVariants = glyph.textContentVariants?.map(convertTextContentVariantToDto)
    ?? (glyph.unicode ? [{ unicode: glyph.unicode, confidence: glyph.confidence }] : undefined)
  return {
    id: glyph.id,
    coords: convertPolygonToDto(glyph.coords),
    textContentVariants: glyphVariants,
    alternativeImages: convertAlternativeImagesToDto(glyph.alternativeImages),
    labels: convertLabelsToDto(glyph.labels),
    userDefined: convertUserDefinedToDto(glyph.userDefined),
    textStyle: convertTextStyleToDto(glyph.textStyle),
    graphemes: glyph.graphemes ? { elements: convertGraphemeElementsToDto(glyph.graphemes.elements) } : undefined,
    ligature: glyph.ligature,
    symbol: glyph.symbol,
    script: undefinedIfBlank(glyph.script),
    production: undefinedIfBlank(glyph.production),
    confidence: glyph.confidence,
    custom: undefinedIfBlank(glyph.custom),
    comments: undefinedIfBlank(glyph.comments)
  }
}

function convertWordFromDto(dto: WordDto): Word {
  return new Word(
    dto.id,
    convertPolygonFromDto(dto.coords)!,
    dto.textContentVariants?.map(convertTextContentVariantFromDto),
    dto.glyphs?.map(convertGlyphFromDto),
    undefined,
    undefined,
    dto.confidence,
    undefinedIfBlank(dto.language),
    undefinedIfBlank(dto.primaryScript) ?? undefinedIfBlank(dto.script),
    undefinedIfBlank(dto.secondaryScript),
    undefinedIfBlank(dto.script),
    undefinedIfBlank(dto.readingDirection),
    undefined,
    undefinedIfBlank(dto.production),
    undefinedIfBlank(dto.custom),
    undefinedIfBlank(dto.comments),
    convertAlternativeImagesFromDto(dto.alternativeImages),
    convertLabelsFromDto(dto.labels),
    convertUserDefinedFromDto(dto.userDefined),
    convertTextStyleFromDto(dto.textStyle)
  )
}

function convertWordToDto(word: Word): WordDto {
  return {
    id: word.id,
    coords: convertPolygonToDto(word.coords),
    textContentVariants: word.textContentVariants?.map(convertTextContentVariantToDto),
    glyphs: word.glyphs?.map(convertGlyphToDto),
    alternativeImages: convertAlternativeImagesToDto(word.alternativeImages),
    labels: convertLabelsToDto(word.labels),
    userDefined: convertUserDefinedToDto(word.userDefined),
    textStyle: convertTextStyleToDto(word.textStyle),
    language: undefinedIfBlank(word.language),
    primaryScript: undefinedIfBlank(word.primaryScript) ?? undefinedIfBlank(word.script),
    secondaryScript: undefinedIfBlank(word.secondaryScript),
    script: undefinedIfBlank(word.script),
    readingDirection: undefinedIfBlank(word.readingDirection),
    production: undefinedIfBlank(word.production),
    confidence: word.confidence,
    custom: undefinedIfBlank(word.custom),
    comments: undefinedIfBlank(word.comments)
  }
}

function convertTextLineFromDto(dto: TextLineDto): TextLine {
  return new TextLine({
    id: dto.id,
    coords: convertPolygonFromDto(dto.coords)!,
    baseline: dto.baseline ? { points: new Polyline(dto.baseline.points), conf: dto.baseline.confidence } : undefined,
    textContentVariants: dto.textContentVariants?.map(convertTextContentVariantFromDto),
    words: dto.words?.map(convertWordFromDto),
    alternativeImages: convertAlternativeImagesFromDto(dto.alternativeImages),
    labels: convertLabelsFromDto(dto.labels),
    userDefined: convertUserDefinedFromDto(dto.userDefined),
    textStyle: convertTextStyleFromDto(dto.textStyle),
    primaryLanguage: undefinedIfBlank(dto.primaryLanguage),
    primaryScript: undefinedIfBlank(dto.primaryScript),
    secondaryScript: undefinedIfBlank(dto.secondaryScript),
    readingDirection: undefinedIfBlank(dto.readingDirection) as TextLine['readingDirection'],
    production: undefinedIfBlank(dto.production) as TextLine['production'],
    custom: undefinedIfBlank(dto.custom),
    comments: undefinedIfBlank(dto.comments),
    index: dto.index,
    confidence: dto.confidence
  })
}

function convertTextLineToDto(line: TextLine): TextLineDto {
  return {
    id: line.id,
    coords: convertPolygonToDto(line.coords),
    baseline: line.baseline?.points
      ? {
          points: line.baseline.points.points,
          confidence: line.baseline.conf
        }
      : undefined,
    textContentVariants: line.textContentVariants?.map(convertTextContentVariantToDto),
    words: line.words?.map(convertWordToDto),
    alternativeImages: convertAlternativeImagesToDto(line.alternativeImages),
    labels: convertLabelsToDto(line.labels),
    userDefined: convertUserDefinedToDto(line.userDefined),
    textStyle: convertTextStyleToDto(line.textStyle),
    bold: line.textStyle?.bold,
    italic: line.textStyle?.italic,
    underlined: line.textStyle?.underlined,
    underlineStyle: undefinedIfBlank(line.textStyle?.underlineStyle),
    subscript: line.textStyle?.subscript,
    superscript: line.textStyle?.superscript,
    strikethrough: line.textStyle?.strikethrough,
    smallCaps: line.textStyle?.smallCaps,
    letterSpaced: line.textStyle?.letterSpaced,
    primaryLanguage: undefinedIfBlank(line.primaryLanguage),
    primaryScript: undefinedIfBlank(line.primaryScript),
    secondaryScript: undefinedIfBlank(line.secondaryScript),
    readingDirection: undefinedIfBlank(line.readingDirection),
    production: undefinedIfBlank(line.production),
    custom: undefinedIfBlank(line.custom),
    comments: undefinedIfBlank(line.comments),
    index: line.index,
    confidence: line.confidence
  }
}

function convertRegionFromDto(dto: RegionDto): Region {
  const baseRegion = {
    id: dto.id,
    kind: dto.kind as RegionKind,
    coords: convertPolygonFromDto(dto.coords)!,
    alternativeImages: convertAlternativeImagesFromDto(dto.alternativeImages),
    labels: convertLabelsFromDto(dto.labels),
    userDefined: convertUserDefinedFromDto(dto.userDefined),
    roles: dto.roles ? { tableCellRole: dto.roles.tableCellRole } : undefined,
    grid: dto.grid
      ? {
          rows: dto.grid.rows?.map(row => ({
            index: row.index,
            points: convertPolygonFromDto(row.points) ?? new Polygon([])
          }))
        }
      : undefined,
    textStyle: convertTextStyleFromDto(dto.textStyle),
    custom: undefinedIfBlank(dto.custom),
    comments: undefinedIfBlank(dto.comments),
    continuation: dto.continuation,
    confidence: dto.confidence,
    numColours: dto.numColours,
    embText: dto.embText,
    colourDepth: undefinedIfBlank(dto.colourDepth),
    lineColour: undefinedIfBlank(dto.lineColour),
    lineSeparators: dto.lineSeparators,
    rows: dto.rows,
    columns: dto.columns,
    colour: undefinedIfBlank(dto.colour),
    penColour: undefinedIfBlank(dto.penColour),
    borderPresent: dto.borderPresent,
    serif: dto.serif,
    monospace: dto.monospace,
    xHeight: dto.xHeight,
    textColourRgb: dto.textColourRgb,
    bgColourRgb: dto.bgColourRgb,
    regions: dto.nestedRegions?.map(convertRegionFromDto)
  }

  if (dto.kind === 'TextRegion') {
    return {
      ...baseRegion,
      kind: 'TextRegion',
      textLines: dto.textLines?.map(convertTextLineFromDto),
      textContentVariants: dto.textContentVariants?.map(convertTextContentVariantFromDto),
      type: undefinedIfBlank(dto.type),
      orientation: dto.orientation,
      textColour: undefinedIfBlank(dto.textColour),
      bgColour: undefinedIfBlank(dto.bgColour),
      reverseVideo: dto.reverseVideo,
      fontSize: dto.fontSize,
      fontFamily: undefinedIfBlank(dto.fontFamily),
      leading: dto.leading,
      kerning: dto.kerning,
      readingDirection: undefinedIfBlank(dto.readingDirection),
      readingOrientation: dto.readingOrientation,
      textLineOrder: undefinedIfBlank(dto.textLineOrder),
      indented: dto.indented,
      align: undefinedIfBlank(dto.align),
      primaryLanguage: undefinedIfBlank(dto.primaryLanguage),
      secondaryLanguage: undefinedIfBlank(dto.secondaryLanguage),
      primaryScript: undefinedIfBlank(dto.primaryScript),
      secondaryScript: undefinedIfBlank(dto.secondaryScript),
      production: undefinedIfBlank(dto.production)
    } as TextRegion
  }

  return {
    ...baseRegion,
    type: undefinedIfBlank(dto.type),
    orientation: dto.orientation
  } as Region
}

function convertRegionToDto(region: Region): RegionDto {
  const regionAny = region as any
  return {
    id: region.id,
    kind: region.kind,
    coords: convertPolygonToDto(region.coords),
    textLines: 'textLines' in region ? (region as any).textLines?.map((line: TextLine) => convertTextLineToDto(line)) : undefined,
    textContentVariants: 'textContentVariants' in region ? (region as any).textContentVariants?.map(convertTextContentVariantToDto) : undefined,
    alternativeImages: convertAlternativeImagesToDto(region.alternativeImages),
    labels: convertLabelsToDto(region.labels),
    userDefined: convertUserDefinedToDto(region.userDefined),
    roles: region.roles?.tableCellRole ? { tableCellRole: region.roles.tableCellRole } : undefined,
    grid: region.grid
      ? {
          rows: region.grid.rows?.map(row => ({
            index: row.index,
            points: convertPolygonToDto(row.points)
          }))
        }
      : undefined,
    textStyle: convertTextStyleToDto(region.textStyle),
    type: 'type' in region ? undefinedIfBlank((region as any).type) : undefined,
    orientation: region.orientation,
    textColour: 'textColour' in region ? undefinedIfBlank((region as any).textColour) : undefined,
    bgColour: 'bgColour' in region ? undefinedIfBlank((region as any).bgColour) : undefined,
    reverseVideo: 'reverseVideo' in region ? (region as any).reverseVideo : undefined,
    fontSize: 'fontSize' in region ? (region as any).fontSize : undefined,
    fontFamily: 'fontFamily' in region ? undefinedIfBlank((region as any).fontFamily) : undefined,
    serif: region.serif,
    monospace: region.monospace,
    xHeight: region.xHeight,
    leading: 'leading' in region ? (region as any).leading : undefined,
    kerning: 'kerning' in region ? (region as any).kerning : undefined,
    align: 'align' in region ? undefinedIfBlank((region as any).align) : undefined,
    textColourRgb: region.textColourRgb,
    bgColourRgb: region.bgColourRgb,
    readingDirection: undefinedIfBlank(regionAny.readingDirection),
    readingOrientation: 'readingOrientation' in region ? (region as any).readingOrientation : undefined,
    textLineOrder: undefinedIfBlank(regionAny.textLineOrder),
    indented: 'indented' in region ? (region as any).indented : undefined,
    primaryLanguage: undefinedIfBlank(regionAny.primaryLanguage),
    secondaryLanguage: undefinedIfBlank(regionAny.secondaryLanguage),
    primaryScript: undefinedIfBlank(regionAny.primaryScript),
    secondaryScript: undefinedIfBlank(regionAny.secondaryScript),
    production: undefinedIfBlank(regionAny.production),
    numColours: region.numColours,
    embText: region.embText,
    colourDepth: undefinedIfBlank(region.colourDepth),
    lineColour: undefinedIfBlank(region.lineColour),
    lineSeparators: region.lineSeparators,
    rows: region.rows,
    columns: region.columns,
    colour: undefinedIfBlank(region.colour),
    penColour: undefinedIfBlank(region.penColour),
    borderPresent: region.borderPresent,
    nestedRegions: region.regions?.map(convertRegionToDto),
    custom: undefinedIfBlank(region.custom),
    comments: undefinedIfBlank(region.comments),
    continuation: region.continuation,
    confidence: region.confidence
  }
}

function convertReadingOrderFromDto(dto: ReadingOrderDto): ReadingOrder | undefined {
  if (!dto.root) return undefined

  const convertGroupDto = (groupDto: GroupDto): import('@/models/editor/reading-order').ReadingOrderGroup => {
    const elements: import('@/models/editor/reading-order').ReadingOrderNode[] = []
    let hasIndex = groupDto.index !== undefined
    if (groupDto.members) {
      for (const member of groupDto.members) {
        if (member.type === 'regionRef') {
          const refDto = member as RegionRefDto
          if (refDto.index !== undefined) hasIndex = true
          elements.push({
            kind: refDto.index !== undefined ? 'RegionRefIndexed' : 'RegionRef',
            id: refDto.id ?? `ref-${refDto.regionRef ?? 'unknown'}`,
            regionRef: refDto.regionRef ?? '',
            index: refDto.index
          })
        } else if (member.type === 'nestedGroup') {
          const nested = (member as NestedGroupDto).group
          const converted = convertGroupDto(nested)
          if ('index' in converted && (converted as any).index !== undefined) hasIndex = true
          elements.push(converted)
        }
      }
    }
    const kind = groupDto.ordered
      ? hasIndex ? 'OrderedGroupIndexed' : 'OrderedGroup'
      : hasIndex ? 'UnorderedGroupIndexed' : 'UnorderedGroup'
    return {
      kind,
      id: groupDto.id ?? 'group-root',
      index: groupDto.index,
      regionRef: groupDto.regionRef,
      caption: undefinedIfBlank(groupDto.caption),
      groupType: undefinedIfBlank(groupDto.groupType),
      continuation: groupDto.continuation,
      custom: undefinedIfBlank(groupDto.custom),
      comments: undefinedIfBlank(groupDto.comments),
      userDefined: convertUserDefinedFromDto(groupDto.userDefined),
      labels: convertLabelsFromDto(groupDto.labels),
      elements
    } as import('@/models/editor/reading-order').ReadingOrderGroup
  }

  return {
    root: convertGroupDto(dto.root)
  }
}

function convertReadingOrderToDto(ro?: ReadingOrder): ReadingOrderDto | undefined {
  if (!ro?.root) return undefined

  const convertGroup = (group: import('@/models/editor/reading-order').ReadingOrderGroup): GroupDto => {
    const ordered = group.kind === 'OrderedGroup' || group.kind === 'OrderedGroupIndexed'
    const members: GroupMemberDto[] = []
    for (const element of group.elements) {
      if ('regionRef' in element && typeof element.regionRef === 'string') {
        members.push({
          type: 'regionRef',
          id: element.id,
          regionRef: element.regionRef,
          index: element.index
        } as RegionRefDto)
      } else if ('elements' in element) {
        members.push({
          type: 'nestedGroup',
          group: convertGroup(element as import('@/models/editor/reading-order').ReadingOrderGroup)
        } as NestedGroupDto)
      }
    }
    return {
      id: group.id,
      ordered,
      index: (group as any).index,
      caption: undefinedIfBlank((group as any).caption),
      groupType: undefinedIfBlank((group as any).groupType),
      regionRef: group.regionRef,
      members,
      continuation: (group as any).continuation,
      userDefined: convertUserDefinedToDto((group as any).userDefined),
      labels: convertLabelsToDto((group as any).labels),
      custom: undefinedIfBlank((group as any).custom),
      comments: undefinedIfBlank((group as any).comments)
    }
  }

  return {
    root: convertGroup(ro.root),
    confidence: undefined
  }
}

function convertLayersFromDto(dto?: LayersDto): Layer[] | undefined {
  if (!dto?.layers?.length) return undefined
  return dto.layers.map(layer => ({
    id: undefinedIfBlank(layer.id),
    zIndex: layer.zIndex,
    caption: undefinedIfBlank(layer.caption),
    regionRefs: layer.regionRefs ?? []
  }))
}

function convertLayersToDto(layers?: Layer[]): LayersDto | undefined {
  if (!layers?.length) return undefined
  return {
    layers: layers.map(layer => ({
      id: undefinedIfBlank(layer.id),
      zIndex: layer.zIndex,
      caption: undefinedIfBlank(layer.caption),
      regionRefs: layer.regionRefs ?? []
    }))
  }
}

function convertRelationsFromDto(dto?: RelationsDto): Relation[] | undefined {
  if (!dto?.relations?.length) return undefined
  return dto.relations.map(relation => ({
    id: undefinedIfBlank(relation.id),
    type: undefinedIfBlank(relation.type),
    sourceRegionRef: undefinedIfBlank(relation.sourceRegionRef),
    targetRegionRef: undefinedIfBlank(relation.targetRegionRef),
    custom: undefinedIfBlank(relation.custom),
    comments: undefinedIfBlank(relation.comments),
    labels: convertLabelsFromDto(relation.labels)
  }))
}

function convertRelationsToDto(relations?: Relation[]): RelationsDto | undefined {
  if (!relations?.length) return undefined
  return {
    relations: relations.map(relation => ({
      id: undefinedIfBlank(relation.id),
      type: undefinedIfBlank(relation.type),
      sourceRegionRef: undefinedIfBlank(relation.sourceRegionRef),
      targetRegionRef: undefinedIfBlank(relation.targetRegionRef),
      custom: undefinedIfBlank(relation.custom),
      comments: undefinedIfBlank(relation.comments),
      labels: convertLabelsToDto(relation.labels)
    }))
  }
}

function convertMetadataItemsFromDto(items?: MetadataItemDto[]): import('@/models/editor/document').MetadataItem[] | undefined {
  if (!items?.length) return undefined
  return items.map(item => ({
    type: item.type as any,
    name: undefinedIfBlank(item.name),
    value: item.value ?? '',
    date: undefinedIfBlank(item.date),
    labels: convertLabelsFromDto(item.labels)
  }))
}

function convertMetadataItemsToDto(items?: import('@/models/editor/document').MetadataItem[]): MetadataItemDto[] | undefined {
  if (!items?.length) return undefined
  return items.map(item => ({
    type: item.type,
    name: undefinedIfBlank(item.name),
    value: item.value,
    date: undefinedIfBlank(item.date),
    labels: convertLabelsToDto(item.labels)
  }))
}

export function convertPageDtoToPcGts(dto: PageDto): PcGts {
  const metadata = new Metadata({
    creator: undefinedIfBlank(dto.metadata?.creator),
    created: dto.metadata?.created,
    lastChange: dto.metadata?.lastChange,
    comments: undefinedIfBlank(dto.metadata?.comments),
    externalRef: undefinedIfBlank(dto.metadata?.externalRef),
    userDefined: convertUserDefinedFromDto(dto.metadata?.userDefined),
    items: convertMetadataItemsFromDto(dto.metadata?.items)
  })

  const page = new Page({
    imageFilename: dto.imageFilename ?? '',
    imageWidth: dto.imageWidth,
    imageHeight: dto.imageHeight,
    imageXResolution: dto.imageXResolution,
    imageYResolution: dto.imageYResolution,
    imageResolutionUnit: undefinedIfBlank(dto.imageResolutionUnit) as Page['imageResolutionUnit'],
    border: dto.border ? { coords: convertPolygonFromDto(dto.border)! } : undefined,
    printSpace: dto.printSpace ? { coords: convertPolygonFromDto(dto.printSpace)! } : undefined,
    readingOrder: dto.readingOrder ? convertReadingOrderFromDto(dto.readingOrder) : undefined,
    alternativeImages: convertAlternativeImagesFromDto(dto.alternativeImages),
    labels: convertLabelsFromDto(dto.labels),
    userDefined: convertUserDefinedFromDto(dto.userDefined),
    textStyle: convertTextStyleFromDto(dto.textStyle),
    layers: convertLayersFromDto(dto.layers),
    relations: convertRelationsFromDto(dto.relations),
    regions: dto.regions?.map(convertRegionFromDto) ?? [],
    custom: undefinedIfBlank(dto.custom),
    orientation: dto.orientation,
    type: undefinedIfBlank(dto.type) as Page['type'],
    primaryLanguage: undefinedIfBlank(dto.primaryLanguage),
    secondaryLanguage: undefinedIfBlank(dto.secondaryLanguage),
    primaryScript: undefinedIfBlank(dto.primaryScript),
    secondaryScript: undefinedIfBlank(dto.secondaryScript),
    readingDirection: undefinedIfBlank(dto.readingDirection) as Page['readingDirection'],
    textLineOrder: undefinedIfBlank(dto.textLineOrder) as Page['textLineOrder'],
    conf: dto.confidence
  })

  return new PcGts(metadata, page, dto.pcGtsId)
}

export function convertPcGtsToPageDto(pcGts: PcGts): PageDto {
  const metadata: MetadataDto = {
    creator: undefinedIfBlank(pcGts.metadata.creator),
    created: undefinedIfBlank(pcGts.metadata.created),
    lastChange: undefinedIfBlank(pcGts.metadata.lastChange),
    comments: undefinedIfBlank(pcGts.metadata.comments),
    externalRef: undefinedIfBlank(pcGts.metadata.externalRef),
    userDefined: convertUserDefinedToDto(pcGts.metadata.userDefined),
    items: convertMetadataItemsToDto(pcGts.metadata.items)
  }
  const hasMetadata = Object.values(metadata).some(value => value !== undefined)

  return {
    imageFilename: pcGts.page.imageFilename,
    imageWidth: pcGts.page.imageWidth,
    imageHeight: pcGts.page.imageHeight,
    imageXResolution: pcGts.page.imageXResolution,
    imageYResolution: pcGts.page.imageYResolution,
    imageResolutionUnit: undefinedIfBlank(pcGts.page.imageResolutionUnit),
    metadata: hasMetadata ? metadata : undefined,
    pcGtsId: pcGts.pcGtsId,
    type: undefinedIfBlank(pcGts.page.type),
    custom: undefinedIfBlank(pcGts.page.custom),
    orientation: pcGts.page.orientation,
    primaryLanguage: undefinedIfBlank(pcGts.page.primaryLanguage),
    secondaryLanguage: undefinedIfBlank(pcGts.page.secondaryLanguage),
    primaryScript: undefinedIfBlank(pcGts.page.primaryScript),
    secondaryScript: undefinedIfBlank(pcGts.page.secondaryScript),
    readingDirection: undefinedIfBlank(pcGts.page.readingDirection),
    textLineOrder: undefinedIfBlank(pcGts.page.textLineOrder),
    confidence: pcGts.page.conf,
    border: pcGts.page.border ? convertPolygonToDto(pcGts.page.border.coords) : undefined,
    printSpace: pcGts.page.printSpace ? convertPolygonToDto(pcGts.page.printSpace.coords) : undefined,
    readingOrder: convertReadingOrderToDto(pcGts.page.readingOrder),
    alternativeImages: convertAlternativeImagesToDto(pcGts.page.alternativeImages),
    labels: convertLabelsToDto(pcGts.page.labels),
    userDefined: convertUserDefinedToDto(pcGts.page.userDefined),
    textStyle: convertTextStyleToDto(pcGts.page.textStyle),
    layers: convertLayersToDto(pcGts.page.layers),
    relations: convertRelationsToDto(pcGts.page.relations),
    regions: pcGts.page.regions?.map(convertRegionToDto)
  }
}
