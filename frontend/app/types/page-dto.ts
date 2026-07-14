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
}
