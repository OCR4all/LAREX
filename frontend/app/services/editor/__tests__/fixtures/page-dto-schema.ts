import type {
  AlternativeImageDto,
  GlyphDto,
  GraphemeElementDto,
  GraphemesDto,
  GridAddPointsDto,
  GridDto,
  GridPointsDto,
  GroupDto,
  LabelDto,
  LabelsDto,
  LayerDto,
  LayersDto,
  MetadataDto,
  MetadataItemDto,
  NestedGroupDto,
  PageDto,
  PolygonDto,
  ReadingOrderDto,
  RegionDto,
  RegionRefDto,
  RelationDto,
  RelationsDto,
  RolesDto,
  TableCellRoleDto,
  TextContentVariantDto,
  TextLineDto,
  TextStyleDto,
  UserAttributeDto,
  UserDefinedDto,
  WordDto
} from '@/types/page-dto'

type DtoName
  = | 'PageDto' | 'MetadataDto' | 'MetadataItemDto' | 'PolygonDto' | 'AlternativeImageDto' | 'LabelsDto' | 'LabelDto'
    | 'UserDefinedDto' | 'UserAttributeDto' | 'TextStyleDto' | 'RegionDto' | 'RolesDto' | 'TableCellRoleDto' | 'GridDto'
    | 'GridPointsDto' | 'GridAddPointsDto' | 'TextLineDto' | 'WordDto' | 'GlyphDto' | 'GraphemesDto' | 'GraphemeElementDto'
    | 'TextContentVariantDto' | 'ReadingOrderDto' | 'GroupDto' | 'GroupMemberDto' | 'RegionRefDto' | 'NestedGroupDto'
    | 'LayersDto' | 'LayerDto' | 'RelationsDto' | 'RelationDto'

type Fields<T> = Record<keyof T, true | DtoName>

const FIELDS = {
  PageDto: {
    imageFilename: true, imageWidth: true, imageHeight: true, imageXResolution: true, imageYResolution: true,
    imageResolutionUnit: true, metadata: 'MetadataDto', pcGtsId: true, type: true, custom: true, orientation: true,
    primaryLanguage: true, secondaryLanguage: true, primaryScript: true, secondaryScript: true, readingDirection: true,
    textLineOrder: true, confidence: true, border: 'PolygonDto', printSpace: 'PolygonDto', regions: 'RegionDto',
    readingOrder: 'ReadingOrderDto', alternativeImages: 'AlternativeImageDto', labels: 'LabelsDto',
    userDefined: 'UserDefinedDto', textStyle: 'TextStyleDto', layers: 'LayersDto', relations: 'RelationsDto',
    formatVersion: true, comments: true, customLanguages: true, customScripts: true
  } satisfies Fields<PageDto>,
  MetadataDto: {
    creator: true, created: true, lastChange: true, comments: true, externalRef: true, userDefined: 'UserDefinedDto',
    items: 'MetadataItemDto'
  } satisfies Fields<MetadataDto>,
  MetadataItemDto: {
    type: true, name: true, value: true, date: true, labels: 'LabelsDto'
  } satisfies Fields<MetadataItemDto>,
  PolygonDto: { points: true, confidence: true } satisfies Fields<PolygonDto>,
  AlternativeImageDto: { filename: true, comments: true, confidence: true } satisfies Fields<AlternativeImageDto>,
  LabelsDto: {
    externalModel: true, externalId: true, prefix: true, comments: true, labels: 'LabelDto'
  } satisfies Fields<LabelsDto>,
  LabelDto: { value: true, type: true, comments: true } satisfies Fields<LabelDto>,
  UserDefinedDto: { attributes: 'UserAttributeDto' } satisfies Fields<UserDefinedDto>,
  UserAttributeDto: { name: true, description: true, type: true, value: true } satisfies Fields<UserAttributeDto>,
  TextStyleDto: {
    fontFamily: true, serif: true, monospace: true, fontSize: true, xHeight: true, kerning: true, textColour: true,
    textColourRgb: true, bgColour: true, bgColourRgb: true, reverseVideo: true, bold: true, italic: true,
    underlined: true, underlineStyle: true, subscript: true, superscript: true, strikethrough: true, smallCaps: true,
    letterSpaced: true
  } satisfies Fields<TextStyleDto>,
  RegionDto: {
    id: true, kind: true, coords: 'PolygonDto', textLines: 'TextLineDto', textContentVariants: 'TextContentVariantDto',
    alternativeImages: 'AlternativeImageDto', labels: 'LabelsDto', userDefined: 'UserDefinedDto', roles: 'RolesDto',
    grid: 'GridDto', textStyle: 'TextStyleDto', type: true, orientation: true, textColour: true, bgColour: true,
    reverseVideo: true, fontSize: true, fontFamily: true, serif: true, monospace: true, xHeight: true, leading: true,
    kerning: true, align: true, textColourRgb: true, bgColourRgb: true, readingDirection: true,
    readingOrientation: true, textLineOrder: true, indented: true, primaryLanguage: true, secondaryLanguage: true,
    primaryScript: true, secondaryScript: true, production: true, numColours: true, embText: true, colourDepth: true,
    lineColour: true, lineSeparators: true, rows: true, columns: true, colour: true, penColour: true,
    borderPresent: true, nestedRegions: 'RegionDto', confidence: true, custom: true, comments: true,
    continuation: true, mirrored: true, customLanguages: true, customScripts: true
  } satisfies Fields<RegionDto>,
  RolesDto: { tableCellRole: 'TableCellRoleDto' } satisfies Fields<RolesDto>,
  TableCellRoleDto: {
    rowIndex: true, columnIndex: true, rowSpan: true, colSpan: true, header: true
  } satisfies Fields<TableCellRoleDto>,
  GridDto: { rows: 'GridPointsDto', addPoints: 'GridAddPointsDto' } satisfies Fields<GridDto>,
  GridAddPointsDto: { row: true, col: true, left: true, top: true, leftPoints: 'PolygonDto', topPoints: 'PolygonDto' } satisfies Fields<GridAddPointsDto>,
  GridPointsDto: { index: true, points: 'PolygonDto' } satisfies Fields<GridPointsDto>,
  TextLineDto: {
    id: true, coords: 'PolygonDto', baseline: 'PolygonDto', textContentVariants: 'TextContentVariantDto',
    words: 'WordDto', alternativeImages: 'AlternativeImageDto', labels: 'LabelsDto', userDefined: 'UserDefinedDto',
    textStyle: 'TextStyleDto', bold: true, italic: true, underlined: true, underlineStyle: true, subscript: true,
    superscript: true, strikethrough: true, smallCaps: true, letterSpaced: true, primaryLanguage: true,
    primaryScript: true, secondaryScript: true, readingDirection: true, production: true, custom: true,
    comments: true, index: true, confidence: true, orientation: true, mirrored: true, secondaryLanguage: true,
    customLanguages: true, customScripts: true, ascentLine: 'PolygonDto', meanLine: 'PolygonDto',
    descentLine: 'PolygonDto'
  } satisfies Fields<TextLineDto>,
  WordDto: {
    id: true, coords: 'PolygonDto', textContentVariants: 'TextContentVariantDto', glyphs: 'GlyphDto',
    alternativeImages: 'AlternativeImageDto', labels: 'LabelsDto', userDefined: 'UserDefinedDto',
    textStyle: 'TextStyleDto', language: true, primaryScript: true, secondaryScript: true, script: true,
    readingDirection: true, production: true, confidence: true, custom: true, comments: true, orientation: true,
    mirrored: true, customLanguages: true, customScripts: true
  } satisfies Fields<WordDto>,
  GlyphDto: {
    id: true, coords: 'PolygonDto', textContentVariants: 'TextContentVariantDto',
    alternativeImages: 'AlternativeImageDto', labels: 'LabelsDto', userDefined: 'UserDefinedDto',
    textStyle: 'TextStyleDto', graphemes: 'GraphemesDto', ligature: true, symbol: true, script: true,
    production: true, confidence: true, custom: true, comments: true, orientation: true, mirrored: true,
    customScript: true
  } satisfies Fields<GlyphDto>,
  GraphemesDto: { elements: 'GraphemeElementDto' } satisfies Fields<GraphemesDto>,
  GraphemeElementDto: {
    kind: true, id: true, index: true, charType: true, ligature: true, custom: true, comments: true,
    coords: 'PolygonDto', textContentVariants: 'TextContentVariantDto', labels: 'LabelsDto',
    members: 'GraphemeElementDto'
  } satisfies Fields<GraphemeElementDto>,
  TextContentVariantDto: {
    unicode: true, plainText: true, index: true, confidence: true, comments: true, dataType: true,
    dataTypeDetails: true
  } satisfies Fields<TextContentVariantDto>,
  ReadingOrderDto: { root: 'GroupDto', confidence: true } satisfies Fields<ReadingOrderDto>,
  GroupDto: {
    id: true, ordered: true, index: true, caption: true, groupType: true, regionRef: true, members: 'GroupMemberDto',
    continuation: true, userDefined: 'UserDefinedDto', labels: 'LabelsDto', custom: true, comments: true
  } satisfies Fields<GroupDto>,
  GroupMemberDto: {},
  RegionRefDto: { type: true, id: true, regionRef: true, index: true } satisfies Fields<RegionRefDto>,
  NestedGroupDto: { type: true, group: 'GroupDto' } satisfies Fields<NestedGroupDto>,
  LayersDto: { layers: 'LayerDto' } satisfies Fields<LayersDto>,
  LayerDto: { id: true, zIndex: true, caption: true, regionRefs: true } satisfies Fields<LayerDto>,
  RelationsDto: { relations: 'RelationDto' } satisfies Fields<RelationsDto>,
  RelationDto: {
    id: true, type: true, sourceRegionRef: true, targetRegionRef: true, custom: true, comments: true,
    labels: 'LabelsDto'
  } satisfies Fields<RelationDto>
} satisfies Record<DtoName, Record<string, true | DtoName>>

export interface DtoWalk {
  present: Map<string, Set<string>>
  unknown: Set<string>
}

export function walkPageDto(page: unknown): DtoWalk {
  const walk: DtoWalk = { present: new Map(), unknown: new Set() }
  visit(page, 'PageDto', walk)
  return walk
}

export function missingFields(walk: DtoWalk): string[] {
  const missing: string[] = []
  for (const [name, fields] of Object.entries(FIELDS)) {
    if (name === 'GroupMemberDto') continue
    const present = walk.present.get(name) ?? new Set()
    for (const field of Object.keys(fields)) {
      if (!present.has(field)) missing.push(`${name}.${field}`)
    }
  }
  return missing
}

function visit(value: unknown, name: DtoName, walk: DtoWalk) {
  if (value === undefined || value === null) return
  if (Array.isArray(value)) {
    value.forEach(item => visit(item, name, walk))
    return
  }
  const record = value as Record<string, unknown>
  const resolved: DtoName = name === 'GroupMemberDto'
    ? record.type === 'regionRef' ? 'RegionRefDto' : 'NestedGroupDto'
    : name
  const fields = FIELDS[resolved] as Record<string, true | DtoName>
  const present = walk.present.get(resolved) ?? new Set<string>()
  walk.present.set(resolved, present)
  for (const [key, child] of Object.entries(record)) {
    if (child === undefined) continue
    const field = fields[key]
    if (field === undefined) {
      walk.unknown.add(`${resolved}.${key}`)
      continue
    }
    present.add(key)
    if (field !== true) visit(child, field, walk)
  }
}

export function withoutFields(value: unknown, fields: Set<string>, name: DtoName = 'PageDto'): unknown {
  if (value === undefined || value === null) return value
  if (Array.isArray(value)) return value.map(item => withoutFields(item, fields, name))
  const record = value as Record<string, unknown>
  const resolved: DtoName = name === 'GroupMemberDto'
    ? record.type === 'regionRef' ? 'RegionRefDto' : 'NestedGroupDto'
    : name
  const schema = FIELDS[resolved] as Record<string, true | DtoName>
  const result: Record<string, unknown> = {}
  for (const [key, child] of Object.entries(record)) {
    if (fields.has(`${resolved}.${key}`)) continue
    const field = schema[key]
    result[key] = field === undefined || field === true ? child : withoutFields(child, fields, field)
  }
  return result
}

export function jsonDifferences(expected: unknown, actual: unknown): string[] {
  const differences: string[] = []
  compare(JSON.parse(JSON.stringify(expected)), JSON.parse(JSON.stringify(actual)), '', differences)
  return differences
}

function compare(expected: unknown, actual: unknown, path: string, differences: string[]) {
  if (Array.isArray(expected) && Array.isArray(actual)) {
    for (let i = 0; i < Math.max(expected.length, actual.length); i++) {
      compare(expected[i], actual[i], `${path}[${i}]`, differences)
    }
    return
  }
  if (isObject(expected) && isObject(actual)) {
    const keys = new Set([...Object.keys(expected), ...Object.keys(actual)])
    for (const key of keys) {
      compare(expected[key], actual[key], path ? `${path}.${key}` : key, differences)
    }
    return
  }
  if (JSON.stringify(expected) !== JSON.stringify(actual)) {
    differences.push(`${path}: ${describe(expected)} -> ${describe(actual)}`)
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function describe(value: unknown): string {
  if (value === undefined) return 'absent'
  if (Array.isArray(value)) return '[…]'
  if (isObject(value)) return '{…}'
  return JSON.stringify(value)
}
