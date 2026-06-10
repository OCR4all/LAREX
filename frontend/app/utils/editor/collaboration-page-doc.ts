import type {
  PageDto,
  ReadingOrderDto,
  RegionDto,
  RelationDto,
  TextLineDto
} from '@/types/page-dto'

export type CollaborativeRegionNode = {
  data: Omit<RegionDto, 'nestedRegions' | 'textLines'>
  childRegionIds: string[]
  textLineIds: string[]
}

export type CollaborationPageSnapshot = {
  page: Omit<PageDto, 'regions' | 'relations' | 'readingOrder'>
  rootRegionIds: string[]
  regions: Record<string, CollaborativeRegionNode>
  textLines: Record<string, TextLineDto>
  relationIds: string[]
  relations: Record<string, RelationDto>
  readingOrder?: ReadingOrderDto
}

function stableStringify(value: unknown): string {
  return JSON.stringify(value, (_key, current) => {
    if (!current || typeof current !== 'object' || Array.isArray(current)) {
      return current
    }

    return Object.keys(current as Record<string, unknown>)
      .sort()
      .reduce<Record<string, unknown>>((acc, key) => {
        acc[key] = (current as Record<string, unknown>)[key]
        return acc
      }, {})
  })
}

function toRegionNode(region: RegionDto): CollaborativeRegionNode {
  const { nestedRegions: _nestedRegions, textLines: _textLines, ...regionData } = structuredClone(region)
  return {
    data: regionData,
    childRegionIds: (region.nestedRegions ?? []).map(child => child.id),
    textLineIds: (region.textLines ?? []).map(line => line.id)
  }
}

function appendRegionNodes(region: RegionDto, snapshot: CollaborationPageSnapshot) {
  snapshot.regions[region.id] = toRegionNode(region)

  for (const textLine of region.textLines ?? []) {
    snapshot.textLines[textLine.id] = structuredClone(textLine)
  }

  for (const child of region.nestedRegions ?? []) {
    appendRegionNodes(child, snapshot)
  }
}

function buildRegionTree(snapshot: CollaborationPageSnapshot, regionId: string): RegionDto | null {
  const node = snapshot.regions[regionId]
  if (!node) return null

  const nestedRegions = node.childRegionIds
    .map(childId => buildRegionTree(snapshot, childId))
    .filter((region): region is RegionDto => Boolean(region))

  const textLines = node.textLineIds
    .map(textLineId => snapshot.textLines[textLineId])
    .filter((line): line is TextLineDto => Boolean(line))
    .map(line => structuredClone(line))

  return {
    ...structuredClone(node.data),
    nestedRegions: nestedRegions.length > 0 ? nestedRegions : undefined,
    textLines: textLines.length > 0 ? textLines : undefined
  }
}

export function createEmptySnapshot(): CollaborationPageSnapshot {
  return {
    page: {
      imageWidth: 0,
      imageHeight: 0
    },
    rootRegionIds: [],
    regions: {},
    textLines: {},
    relationIds: [],
    relations: {}
  }
}

export function isSnapshotEmpty(snapshot: CollaborationPageSnapshot | null | undefined): boolean {
  if (!snapshot) return true

  return Object.keys(snapshot.regions).length === 0
    && Object.keys(snapshot.textLines).length === 0
    && Object.keys(snapshot.relations).length === 0
    && snapshot.rootRegionIds.length === 0
    && !snapshot.readingOrder
}

export function snapshotFromPageDto(page: PageDto): CollaborationPageSnapshot {
  const {
    regions: _regions,
    relations: _relations,
    readingOrder: _readingOrder,
    ...pageData
  } = structuredClone(page)

  const snapshot: CollaborationPageSnapshot = {
    page: pageData,
    rootRegionIds: (page.regions ?? []).map(region => region.id),
    regions: {},
    textLines: {},
    relationIds: [],
    relations: {},
    readingOrder: page.readingOrder ? structuredClone(page.readingOrder) : undefined
  }

  for (const region of page.regions ?? []) {
    appendRegionNodes(region, snapshot)
  }

  const relationEntries = (page.relations?.relations ?? []).map((relation, index) => {
    const key = relation.id?.trim() || `relation:${index}`
    return { key, data: structuredClone(relation) }
  })

  snapshot.relationIds = relationEntries.map(entry => entry.key)
  for (const relation of relationEntries) {
    snapshot.relations[relation.key] = relation.data
  }

  return snapshot
}

export function snapshotToPageDto(snapshot: CollaborationPageSnapshot): PageDto {
  const regions = snapshot.rootRegionIds
    .map(regionId => buildRegionTree(snapshot, regionId))
    .filter((region): region is RegionDto => Boolean(region))

  const relations = snapshot.relationIds
    .map(key => snapshot.relations[key])
    .filter((relation): relation is RelationDto => Boolean(relation))
    .map(relation => structuredClone(relation))

  return {
    ...structuredClone(snapshot.page),
    regions,
    relations: relations.length > 0 ? { relations } : undefined,
    readingOrder: snapshot.readingOrder ? structuredClone(snapshot.readingOrder) : undefined
  }
}

export function snapshotHash(snapshot: CollaborationPageSnapshot): string {
  return stableStringify(snapshot)
}
