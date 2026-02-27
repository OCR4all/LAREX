/**
 * Service for converting between backend PageDto and frontend PcGts formats.
 *
 * Backend now sends world coordinates as [x, y] arrays in the range [-1, 1].
 * Frontend uses [number, number][] for polygon points.
 * 
 * Coordinate conversion (pixel ↔ world) is handled by the backend.
 * This service only handles structural mapping between DTO and model types.
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
  type ReadingOrder,
  type Region,
  type TextRegion,
  type RegionKind
} from '@/models/editor'

export type PointDto = [number, number]

export interface PolygonDto {
  points: PointDto[]
  confidence?: number
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

export interface GlyphDto {
  id: string
  coords?: PolygonDto
  textContentVariants?: TextContentVariantDto[]
  ligature?: boolean
  symbol?: boolean
  script?: string
  confidence?: number
  custom?: string
  comments?: string
}

export interface WordDto {
  id: string
  coords?: PolygonDto
  textContentVariants?: TextContentVariantDto[]
  glyphs?: GlyphDto[]
  language?: string
  script?: string
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

export interface RegionDto {
  id: string
  kind: string
  coords?: PolygonDto
  textLines?: TextLineDto[]
  textContentVariants?: TextContentVariantDto[]
  type?: string
  orientation?: number
  textColour?: string
  bgColour?: string
  reverseVideo?: boolean
  fontSize?: number
  fontFamily?: string
  leading?: number
  kerning?: number
  readingDirection?: string
  readingOrientation?: number
  textLineOrder?: string
  indented?: boolean
  primaryLanguage?: string
  secondaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  production?: string
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
  caption?: string
  regionRef?: string
  members?: GroupMemberDto[]
  custom?: string
  comments?: string
}

export interface ReadingOrderDto {
  root?: GroupDto
  confidence?: number
}

export interface MetadataDto {
  creator?: string
  created?: string
  lastChange?: string
  comments?: string
  externalRef?: string
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
  formatVersion?: string
  labelIds?: string[]
}

/**
 * Convert backend PolygonDto to frontend Polygon.
 * Points are already in world coordinates from the backend.
 */
function convertPolygonFromDto(dto?: PolygonDto): Polygon | undefined {
  if (!dto || !dto.points) return undefined
  return new Polygon(dto.points)
}

/**
 * Convert frontend Polygon to backend PolygonDto.
 * Points are already in world coordinates for the backend.
 */
function convertPolygonToDto(polygon?: Polygon): PolygonDto | undefined {
  if (!polygon) return undefined
  return {
    points: polygon.points,
    confidence: undefined // Polygon class doesn't have confidence at top level
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

/**
 * Convert backend TextContentVariantDto to frontend TextContentVariant
 */
function convertTextContentVariantFromDto(dto: TextContentVariantDto): TextContentVariant {
  return {
    unicode: dto.unicode,
    plainText: dto.plainText,
    index: dto.index,
    confidence: dto.confidence,
    comments: dto.comments,
    dataType: dto.dataType,
    dataTypeDetails: dto.dataTypeDetails
  }
}

/**
 * Convert frontend TextContentVariant to backend TextContentVariantDto
 */
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

/**
 * Convert backend GlyphDto to frontend Glyph
 */
function convertGlyphFromDto(dto: GlyphDto): Glyph {
  const textContentVariants = dto.textContentVariants?.map(convertTextContentVariantFromDto)
  const unicode = textContentVariants?.[0]?.unicode
  return new Glyph(
    dto.id,
    convertPolygonFromDto(dto.coords)!,
    unicode,
    dto.ligature,
    dto.symbol,
    dto.script,
    undefined, // production
    dto.confidence,
    textContentVariants ? { textContentVariants } : undefined
  )
}

/**
 * Convert frontend Glyph to backend GlyphDto
 */
function convertGlyphToDto(glyph: Glyph): GlyphDto {
  const glyphMetadata = glyph.metadata as { textContentVariants?: TextContentVariant[] } | undefined
  const variantsFromMetadata = Array.isArray(glyphMetadata?.textContentVariants)
    ? glyphMetadata?.textContentVariants.map(convertTextContentVariantToDto)
    : undefined
  const glyphVariants = variantsFromMetadata
    ?? (glyph.unicode ? [{ unicode: glyph.unicode, confidence: glyph.confidence }] : undefined)

  return {
    id: glyph.id,
    coords: convertPolygonToDto(glyph.coords),
    textContentVariants: glyphVariants,
    ligature: glyph.ligature,
    symbol: glyph.symbol,
    script: glyph.script,
    confidence: glyph.confidence
  }
}

/**
 * Convert backend WordDto to frontend Word
 */
function convertWordFromDto(dto: WordDto): Word {
  return new Word(
    dto.id,
    convertPolygonFromDto(dto.coords)!,
    dto.textContentVariants?.map(convertTextContentVariantFromDto),
    dto.glyphs?.map(g => convertGlyphFromDto(g)),
    undefined, // styleRefs
    undefined, // processingRefs
    dto.confidence,
    dto.language,
    dto.script
  )
}

/**
 * Convert frontend Word to backend WordDto
 */
function convertWordToDto(word: Word): WordDto {
  return {
    id: word.id,
    coords: convertPolygonToDto(word.coords),
    textContentVariants: word.textContentVariants?.map(convertTextContentVariantToDto),
    glyphs: word.glyphs?.map(g => convertGlyphToDto(g)),
    language: word.language,
    script: word.script,
    confidence: word.confidence
  }
}

/**
 * Convert backend TextLineDto to frontend TextLine
 */
function convertTextLineFromDto(dto: TextLineDto): TextLine {
  return new TextLine({
    id: dto.id,
    coords: convertPolygonFromDto(dto.coords)!,
    baseline: dto.baseline ? { points: new Polyline(dto.baseline.points), conf: dto.baseline.confidence } : undefined,
    textContentVariants: dto.textContentVariants?.map(convertTextContentVariantFromDto),
    words: dto.words?.map(w => convertWordFromDto(w)),
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

/**
 * Convert frontend TextLine to backend TextLineDto
 */
function convertTextLineToDto(line: TextLine): TextLineDto {
  return {
    id: line.id,
    coords: convertPolygonToDto(line.coords),
    baseline: line.baseline?.points ? {
      points: line.baseline.points.points,
      confidence: line.baseline.conf
    } : undefined,
    textContentVariants: line.textContentVariants?.map(convertTextContentVariantToDto),
    words: line.words?.map(w => convertWordToDto(w)),
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

/**
 * Convert backend RegionDto to frontend Region
 */
function convertRegionFromDto(dto: RegionDto): Region {
  const baseRegion = {
    id: dto.id,
    kind: dto.kind as RegionKind,
    coords: convertPolygonFromDto(dto.coords)!,
    custom: undefinedIfBlank(dto.custom),
    comments: undefinedIfBlank(dto.comments),
    continuation: dto.continuation,
    confidence: dto.confidence,
    regions: dto.nestedRegions?.map(r => convertRegionFromDto(r))
  }

  if (dto.kind === 'TextRegion') {
    return {
      ...baseRegion,
      kind: 'TextRegion',
      textLines: dto.textLines?.map(tl => convertTextLineFromDto(tl)),
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

/**
 * Convert frontend Region to backend RegionDto
 */
function convertRegionToDto(region: Region): RegionDto {
  return {
    id: region.id,
    kind: region.kind,
    coords: convertPolygonToDto(region.coords),
    textLines: 'textLines' in region ? (region as any).textLines?.map((tl: TextLine) => convertTextLineToDto(tl)) : undefined,
    textContentVariants: 'textContentVariants' in region ? (region as any).textContentVariants?.map(convertTextContentVariantToDto) : undefined,
    type: 'type' in region ? undefinedIfBlank((region as any).type) : undefined,
    orientation: region.orientation,
    textColour: 'textColour' in region ? undefinedIfBlank((region as any).textColour) : undefined,
    bgColour: 'bgColour' in region ? undefinedIfBlank((region as any).bgColour) : undefined,
    reverseVideo: 'reverseVideo' in region ? (region as any).reverseVideo : undefined,
    fontSize: 'fontSize' in region ? (region as any).fontSize : undefined,
    fontFamily: 'fontFamily' in region ? undefinedIfBlank((region as any).fontFamily) : undefined,
    leading: 'leading' in region ? (region as any).leading : undefined,
    kerning: 'kerning' in region ? (region as any).kerning : undefined,
    readingDirection: undefinedIfBlank(region.readingDirection),
    readingOrientation: 'readingOrientation' in region ? (region as any).readingOrientation : undefined,
    textLineOrder: undefinedIfBlank(region.textLineOrder),
    indented: 'indented' in region ? (region as any).indented : undefined,
    primaryLanguage: undefinedIfBlank(region.primaryLanguage),
    secondaryLanguage: undefinedIfBlank(region.secondaryLanguage),
    primaryScript: undefinedIfBlank(region.primaryScript),
    secondaryScript: undefinedIfBlank(region.secondaryScript),
    production: undefinedIfBlank(region.production),
    nestedRegions: region.regions?.map(r => convertRegionToDto(r)),
    custom: undefinedIfBlank(region.custom),
    comments: undefinedIfBlank(region.comments),
    continuation: region.continuation,
    confidence: region.confidence
  }
}

/**
 * Convert backend ReadingOrderDto to frontend ReadingOrder
 */
function convertReadingOrderFromDto(dto: ReadingOrderDto): ReadingOrder | undefined {
  if (!dto.root) return undefined

  const convertGroupDto = (groupDto: GroupDto, ordered: boolean): ReadingOrderGroup => {
    const elements: ReadingOrderNode[] = []

    if (groupDto.members) {
      for (const member of groupDto.members) {
        if (member.type === 'regionRef') {
          const refDto = member as RegionRefDto
          elements.push({
            kind: refDto.index !== undefined ? 'RegionRefIndexed' : 'RegionRef',
            id: refDto.id ?? `ref-${refDto.regionRef}`,
            regionRef: refDto.regionRef ?? '',
            index: refDto.index
          })
        } else if (member.type === 'nestedGroup') {
          const nestedDto = member as NestedGroupDto
          elements.push(convertGroupDto(nestedDto.group, nestedDto.group.ordered ?? true))
        }
      }
    }

    return {
      kind: ordered ? 'OrderedGroup' : 'UnorderedGroup',
      id: groupDto.id ?? 'group-root',
      regionRef: groupDto.regionRef,
      elements
    } as ReadingOrderGroup
  }

  return {
    root: convertGroupDto(dto.root, dto.root.ordered ?? true)
  }
}

type ReadingOrderNode = import('@/models/editor/reading-order').ReadingOrderNode
type ReadingOrderGroup = import('@/models/editor/reading-order').ReadingOrderGroup

/**
 * Convert frontend ReadingOrder to backend ReadingOrderDto
 */
function convertReadingOrderToDto(ro?: ReadingOrder): ReadingOrderDto | undefined {
  if (!ro || !ro.root) return undefined

  const convertGroup = (group: ReadingOrderGroup): GroupDto => {
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
          group: convertGroup(element as ReadingOrderGroup)
        } as NestedGroupDto)
      }
    }

    return {
      id: group.id,
      ordered,
      regionRef: group.regionRef,
      members
    }
  }

  return {
    root: convertGroup(ro.root),
    confidence: undefined
  }
}

/**
 * Convert backend PageDto to frontend PcGts
 */
export function convertPageDtoToPcGts(dto: PageDto): PcGts {
  const metadata = new Metadata({
    creator: undefinedIfBlank(dto.metadata?.creator),
    created: dto.metadata?.created,
    lastChange: dto.metadata?.lastChange,
    comments: undefinedIfBlank(dto.metadata?.comments),
    externalRef: undefinedIfBlank(dto.metadata?.externalRef)
  })

  const page = new Page({
    imageFilename: dto.imageFilename ?? '',
    imageWidth: dto.imageWidth,
    imageHeight: dto.imageHeight,
    imageXResolution: dto.imageXResolution,
    imageYResolution: dto.imageYResolution,
    border: dto.border ? { coords: convertPolygonFromDto(dto.border)! } : undefined,
    printSpace: dto.printSpace ? { coords: convertPolygonFromDto(dto.printSpace)! } : undefined,
    regions: dto.regions?.map(r => convertRegionFromDto(r)) ?? [],
    readingOrder: dto.readingOrder ? convertReadingOrderFromDto(dto.readingOrder) : undefined,
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

/**
 * Convert frontend PcGts to backend PageDto
 */
export function convertPcGtsToPageDto(pcGts: PcGts): PageDto {
  const metadata = {
    creator: undefinedIfBlank(pcGts.metadata.creator),
    created: undefinedIfBlank(pcGts.metadata.created),
    lastChange: undefinedIfBlank(pcGts.metadata.lastChange),
    comments: undefinedIfBlank(pcGts.metadata.comments),
    externalRef: undefinedIfBlank(pcGts.metadata.externalRef)
  }
  const hasMetadata = Object.values(metadata).some(value => value !== undefined)

  return {
    imageFilename: pcGts.page.imageFilename,
    imageWidth: pcGts.page.imageWidth,
    imageHeight: pcGts.page.imageHeight,
    imageXResolution: pcGts.page.imageXResolution,
    imageYResolution: pcGts.page.imageYResolution,
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
    regions: pcGts.page.regions?.map(r => convertRegionToDto(r)),
    readingOrder: convertReadingOrderToDto(pcGts.page.readingOrder)
  }
}
