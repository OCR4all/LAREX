import { Polygon, type Region, type RegionKind, type TextContentVariant, type TextLine, type TextRegion } from '@/models/editor'
import type { RegionDto } from '@/types/page-dto'
import {
  convertAlternativeImagesFromDto,
  convertAlternativeImagesToDto,
  convertLabelsFromDto,
  convertLabelsToDto,
  convertPolygonFromDto,
  convertPolygonToDto,
  convertTextStyleFromDto,
  convertTextStyleToDto,
  convertUserDefinedFromDto,
  convertUserDefinedToDto,
  undefinedIfBlank
} from './shared'
import {
  convertTextContentVariantFromDto,
  convertTextContentVariantToDto,
  convertTextLineFromDto,
  convertTextLineToDto
} from './text-converter'

type RegionDtoSource = Region & Partial<{
  textLines: TextLine[]
  textContentVariants: TextContentVariant[]
  type: string
  textColour: string
  bgColour: string
  reverseVideo: boolean
  fontSize: number
  fontFamily: string
  leading: number
  kerning: number
  align: string
  readingDirection: string
  readingOrientation: number
  textLineOrder: string
  indented: boolean
  primaryLanguage: string
  secondaryLanguage: string
  primaryScript: string
  secondaryScript: string
  production: string
}>

export function convertRegionFromDto(dto: RegionDto): Region {
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

export function convertRegionToDto(region: Region): RegionDto {
  const regionDtoSource = region as RegionDtoSource
  return {
    id: region.id,
    kind: region.kind,
    coords: convertPolygonToDto(region.coords),
    textLines: 'textLines' in region ? regionDtoSource.textLines?.map((line: TextLine) => convertTextLineToDto(line)) : undefined,
    textContentVariants: 'textContentVariants' in region ? regionDtoSource.textContentVariants?.map(convertTextContentVariantToDto) : undefined,
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
    type: 'type' in region ? undefinedIfBlank(regionDtoSource.type) : undefined,
    orientation: region.orientation,
    textColour: 'textColour' in region ? undefinedIfBlank(regionDtoSource.textColour) : undefined,
    bgColour: 'bgColour' in region ? undefinedIfBlank(regionDtoSource.bgColour) : undefined,
    reverseVideo: 'reverseVideo' in region ? regionDtoSource.reverseVideo : undefined,
    fontSize: 'fontSize' in region ? regionDtoSource.fontSize : undefined,
    fontFamily: 'fontFamily' in region ? undefinedIfBlank(regionDtoSource.fontFamily) : undefined,
    serif: region.serif,
    monospace: region.monospace,
    xHeight: region.xHeight,
    leading: 'leading' in region ? regionDtoSource.leading : undefined,
    kerning: 'kerning' in region ? regionDtoSource.kerning : undefined,
    align: 'align' in region ? undefinedIfBlank(regionDtoSource.align) : undefined,
    textColourRgb: region.textColourRgb,
    bgColourRgb: region.bgColourRgb,
    readingDirection: undefinedIfBlank(regionDtoSource.readingDirection),
    readingOrientation: 'readingOrientation' in region ? regionDtoSource.readingOrientation : undefined,
    textLineOrder: undefinedIfBlank(regionDtoSource.textLineOrder),
    indented: 'indented' in region ? regionDtoSource.indented : undefined,
    primaryLanguage: undefinedIfBlank(regionDtoSource.primaryLanguage),
    secondaryLanguage: undefinedIfBlank(regionDtoSource.secondaryLanguage),
    primaryScript: undefinedIfBlank(regionDtoSource.primaryScript),
    secondaryScript: undefinedIfBlank(regionDtoSource.secondaryScript),
    production: undefinedIfBlank(regionDtoSource.production),
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
