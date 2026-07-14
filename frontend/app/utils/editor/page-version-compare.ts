import type { PageDto, PolygonDto, RegionDto, TextLineDto } from '@/types/page-dto'

export type PageVersionCompareSource = 'version' | 'action-result'
export type PageVersionCompareElementKind = 'region' | 'textline' | 'baseline'
export type PageVersionCompareChangeType = 'added' | 'removed' | 'geometry' | 'text' | 'metadata'

export interface PageVersionCompareElement {
  id: string
  kind: PageVersionCompareElementKind
  parentId?: string
  label: string
  points?: [number, number][]
  geometrySignature?: string
  text?: string
  metadataSignature?: string
}

export interface PageVersionCompareChange {
  id: string
  kind: PageVersionCompareElementKind
  changeType: PageVersionCompareChangeType
  current?: PageVersionCompareElement
  compared?: PageVersionCompareElement
}

export interface PageVersionCompareSummary {
  changes: PageVersionCompareChange[]
  counts: Record<PageVersionCompareElementKind, Record<PageVersionCompareChangeType, number>>
}

export interface PageVersionCompareChangeGroup {
  key: string
  id: string
  kind: PageVersionCompareElementKind
  changeTypes: PageVersionCompareChangeType[]
  changes: PageVersionCompareChange[]
  current?: PageVersionCompareElement
  compared?: PageVersionCompareElement
}

const CHANGE_TYPES: PageVersionCompareChangeType[] = ['added', 'removed', 'geometry', 'text', 'metadata']
const ELEMENT_KINDS: PageVersionCompareElementKind[] = ['region', 'textline', 'baseline']

export function createEmptyCompareCounts(): PageVersionCompareSummary['counts'] {
  return ELEMENT_KINDS.reduce((acc, kind) => {
    acc[kind] = CHANGE_TYPES.reduce((inner, type) => {
      inner[type] = 0
      return inner
    }, {} as Record<PageVersionCompareChangeType, number>)
    return acc
  }, {} as PageVersionCompareSummary['counts'])
}

export function comparePageVersions(
  current: PageDto,
  compared: PageDto,
  options: { gtIndex?: number } = {}
): PageVersionCompareSummary {
  const currentElements = collectComparableElements(current, options.gtIndex)
  const comparedElements = collectComparableElements(compared, options.gtIndex)
  const ids = new Set([...currentElements.keys(), ...comparedElements.keys()])
  const changes: PageVersionCompareChange[] = []
  const counts = createEmptyCompareCounts()

  for (const id of [...ids].sort()) {
    const currentElement = currentElements.get(id)
    const comparedElement = comparedElements.get(id)

    let changeType: PageVersionCompareChangeType | null = null
    if (currentElement && !comparedElement) {
      changeType = 'added'
    } else if (!currentElement && comparedElement) {
      changeType = 'removed'
    } else if (currentElement && comparedElement) {
      const matchedChangeTypes: PageVersionCompareChangeType[] = []
      if (currentElement.geometrySignature !== comparedElement.geometrySignature) {
        matchedChangeTypes.push('geometry')
      }
      if (currentElement.kind === 'textline' && normalizeText(currentElement.text) !== normalizeText(comparedElement.text)) {
        matchedChangeTypes.push('text')
      }
      if (
        currentElement.parentId !== comparedElement.parentId
        || currentElement.metadataSignature !== comparedElement.metadataSignature
      ) {
        matchedChangeTypes.push('metadata')
      }

      for (const matchedChangeType of matchedChangeTypes) {
        counts[currentElement.kind][matchedChangeType] += 1
        changes.push({
          id,
          kind: currentElement.kind,
          changeType: matchedChangeType,
          current: currentElement,
          compared: comparedElement
        })
      }
      continue
    }

    if (!changeType) continue
    const kind = currentElement?.kind ?? comparedElement?.kind
    if (!kind) continue
    counts[kind][changeType] += 1
    changes.push({
      id,
      kind,
      changeType,
      current: currentElement,
      compared: comparedElement
    })
  }

  return { changes, counts }
}

export function collectComparableElements(page: PageDto, gtIndex = 0): Map<string, PageVersionCompareElement> {
  const elements = new Map<string, PageVersionCompareElement>()
  const dimensions = { width: page.imageWidth, height: page.imageHeight }
  for (const region of page.regions ?? []) {
    collectRegion(region, elements, gtIndex, dimensions)
  }
  return elements
}

export function groupPageVersionChanges(changes: PageVersionCompareChange[]): PageVersionCompareChangeGroup[] {
  const groups = new Map<string, PageVersionCompareChangeGroup>()

  for (const change of changes) {
    const key = `${change.kind}:${change.id}`
    const existing = groups.get(key)
    if (existing) {
      existing.changes.push(change)
      if (!existing.changeTypes.includes(change.changeType)) {
        existing.changeTypes.push(change.changeType)
      }
      existing.current ??= change.current
      existing.compared ??= change.compared
      continue
    }

    groups.set(key, {
      key,
      id: change.id,
      kind: change.kind,
      changeTypes: [change.changeType],
      changes: [change],
      current: change.current,
      compared: change.compared
    })
  }

  return [...groups.values()]
}

export function textDiffSegments(current: string, compared: string): Array<{ value: string, status: 'same' | 'current' | 'compared' }> {
  const currentTokens = tokenize(current)
  const comparedTokens = tokenize(compared)
  const table = buildLcsTable(currentTokens, comparedTokens)
  const segments: Array<{ value: string, status: 'same' | 'current' | 'compared' }> = []
  let i = 0
  let j = 0

  while (i < currentTokens.length && j < comparedTokens.length) {
    if (currentTokens[i] === comparedTokens[j]) {
      pushSegment(segments, currentTokens[i]!, 'same')
      i += 1
      j += 1
    } else if (table[i + 1]![j]! >= table[i]![j + 1]!) {
      pushSegment(segments, currentTokens[i]!, 'current')
      i += 1
    } else {
      pushSegment(segments, comparedTokens[j]!, 'compared')
      j += 1
    }
  }
  while (i < currentTokens.length) {
    pushSegment(segments, currentTokens[i]!, 'current')
    i += 1
  }
  while (j < comparedTokens.length) {
    pushSegment(segments, comparedTokens[j]!, 'compared')
    j += 1
  }

  return segments
}

function collectRegion(
  region: RegionDto,
  elements: Map<string, PageVersionCompareElement>,
  gtIndex: number,
  dimensions: { width: number, height: number },
  parentId?: string
) {
  const points = normalizePoints(region.coords)
  elements.set(region.id, {
    id: region.id,
    kind: 'region',
    parentId,
    label: regionLabel(region),
    points,
    geometrySignature: geometrySignature(points, dimensions),
    metadataSignature: metadataSignature(region, [
      'kind',
      'alternativeImages',
      'labels',
      'userDefined',
      'roles',
      'grid',
      'textStyle',
      'type',
      'orientation',
      'textColour',
      'bgColour',
      'reverseVideo',
      'fontSize',
      'fontFamily',
      'serif',
      'monospace',
      'xHeight',
      'leading',
      'kerning',
      'align',
      'textColourRgb',
      'bgColourRgb',
      'readingDirection',
      'readingOrientation',
      'textLineOrder',
      'indented',
      'primaryLanguage',
      'secondaryLanguage',
      'primaryScript',
      'secondaryScript',
      'production',
      'numColours',
      'embText',
      'colourDepth',
      'lineColour',
      'lineSeparators',
      'rows',
      'columns',
      'colour',
      'penColour',
      'borderPresent',
      'custom',
      'comments',
      'continuation',
      'confidence'
    ])
  })

  for (const textLine of region.textLines ?? []) {
    collectTextLine(textLine, elements, gtIndex, dimensions, region.id)
  }
  for (const child of region.nestedRegions ?? []) {
    collectRegion(child, elements, gtIndex, dimensions, region.id)
  }
}

function collectTextLine(
  textLine: TextLineDto,
  elements: Map<string, PageVersionCompareElement>,
  gtIndex: number,
  dimensions: { width: number, height: number },
  parentId: string
) {
  const text = textLineText(textLine, gtIndex)
  const points = normalizePoints(textLine.coords)
  elements.set(textLine.id, {
    id: textLine.id,
    kind: 'textline',
    parentId,
    label: `Textline ${textLine.id}`,
    points,
    geometrySignature: geometrySignature(points, dimensions),
    text,
    metadataSignature: metadataSignature(textLine, [
      'alternativeImages',
      'labels',
      'userDefined',
      'textStyle',
      'bold',
      'italic',
      'underlined',
      'underlineStyle',
      'subscript',
      'superscript',
      'strikethrough',
      'smallCaps',
      'letterSpaced',
      'primaryLanguage',
      'primaryScript',
      'secondaryScript',
      'readingDirection',
      'production',
      'custom',
      'comments',
      'index',
      'confidence'
    ])
  })

  if (textLine.baseline) {
    const baselineId = `baseline:${textLine.id}`
    const baselinePoints = normalizePoints(textLine.baseline)
    elements.set(baselineId, {
      id: baselineId,
      kind: 'baseline',
      parentId: textLine.id,
      label: `Baseline ${textLine.id}`,
      points: baselinePoints,
      geometrySignature: geometrySignature(baselinePoints, dimensions),
      metadataSignature: textLine.baseline.confidence == null ? '' : String(textLine.baseline.confidence)
    })
  }
}

function regionLabel(region: RegionDto): string {
  const type = region.type ? `:${region.type}` : ''
  return `${region.kind}${type} ${region.id}`
}

function textLineText(textLine: TextLineDto, gtIndex: number): string {
  const variants = textLine.textContentVariants ?? []
  const exact = variants.find(variant => variant.index === gtIndex)
  const fallback = variants.find(variant => variant.index === 0) ?? variants[0]
  return exact?.unicode ?? exact?.plainText ?? fallback?.unicode ?? fallback?.plainText ?? ''
}

function normalizePoints(polygon?: PolygonDto): [number, number][] | undefined {
  if (!polygon?.points?.length) return undefined
  return polygon.points.map(point => [point[0], point[1]])
}

function geometrySignature(
  points: [number, number][] | undefined,
  dimensions: { width: number, height: number }
): string {
  if (!points?.length) return ''
  if (dimensions.width > 0 && dimensions.height > 0) {
    return points
      .map(([x, y]) => `${worldToPixelX(x, dimensions.width)},${worldToPixelY(y, dimensions.height)}`)
      .join(' ')
  }
  return points.map(([x, y]) => `${roundCoord(x)},${roundCoord(y)}`).join(' ')
}

function worldToPixelX(worldX: number, imageWidth: number): number {
  return Math.round(((worldX + 1) / 2) * imageWidth)
}

function worldToPixelY(worldY: number, imageHeight: number): number {
  return Math.round(((1 - worldY) / 2) * imageHeight)
}

function roundCoord(value: number): string {
  return Number.isFinite(value) ? value.toFixed(5) : '0.00000'
}

function metadataSignature(source: object, includedKeys: string[]): string {
  const copy: Record<string, unknown> = {}
  const sourceRecord = source as Record<string, unknown>
  for (const key of includedKeys) {
    if (key in sourceRecord) {
      const normalized = normalizeMetadataValue(sourceRecord[key])
      if (normalized !== undefined) {
        copy[key] = normalized
      }
    }
  }
  return JSON.stringify(sortObject(copy))
}

function normalizeMetadataValue(value: unknown): unknown {
  if (value === undefined || value === null) return undefined

  if (Array.isArray(value)) {
    const normalizedItems = value
      .map(normalizeMetadataValue)
      .filter(item => item !== undefined)
    return normalizedItems.length > 0 ? normalizedItems : undefined
  }

  if (typeof value === 'object') {
    const normalizedObject: Record<string, unknown> = {}
    for (const [key, nestedValue] of Object.entries(value as Record<string, unknown>)) {
      const normalized = normalizeMetadataValue(nestedValue)
      if (normalized !== undefined) {
        normalizedObject[key] = normalized
      }
    }
    return Object.keys(normalizedObject).length > 0 ? normalizedObject : undefined
  }

  return value
}

function sortObject(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortObject)
  if (!value || typeof value !== 'object') return value
  return Object.keys(value as Record<string, unknown>).sort().reduce((acc, key) => {
    acc[key] = sortObject((value as Record<string, unknown>)[key])
    return acc
  }, {} as Record<string, unknown>)
}

function normalizeText(value?: string): string {
  return (value ?? '').replace(/\s+/g, ' ').trim()
}

function tokenize(value: string): string[] {
  return value.match(/\s+|[^\s]+/g) ?? []
}

function buildLcsTable(left: string[], right: string[]): number[][] {
  const table = Array.from({ length: left.length + 1 }, () => Array.from({ length: right.length + 1 }, () => 0))
  for (let i = left.length - 1; i >= 0; i -= 1) {
    for (let j = right.length - 1; j >= 0; j -= 1) {
      table[i]![j] = left[i] === right[j]
        ? table[i + 1]![j + 1]! + 1
        : Math.max(table[i + 1]![j]!, table[i]![j + 1]!)
    }
  }
  return table
}

function pushSegment(
  segments: Array<{ value: string, status: 'same' | 'current' | 'compared' }>,
  value: string,
  status: 'same' | 'current' | 'compared'
) {
  const previous = segments.at(-1)
  if (previous?.status === status) {
    previous.value += value
  } else {
    segments.push({ value, status })
  }
}
