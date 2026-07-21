export interface TreeItemData {
  id: string
  type?: string
  parentId?: string
  parentPolygonId?: string
  label?: string
  points?: Array<{ x: number, y: number }>
  regionKind?: string
  regionSubtype?: string
  regionCustom?: string
}

export interface FlatStructureRow {
  item: TreeItemData
  level: number
  hasChildren: boolean
  isExpanded: boolean
}

const TYPE_ORDER: Record<string, number> = {
  REGION: 0,
  TEXTLINE: 1,
  BASELINE: 2
}

function normalizeType(type?: string): string {
  return (type ?? '').toUpperCase()
}

export function getTreeItemDisplayLabel(item: TreeItemData, mappedLabel?: string | null): string {
  return mappedLabel?.trim() || item.label?.trim() || item.id
}

export function getTreeItemDisplayType(item: TreeItemData): string {
  const regionKind = item.regionKind?.trim()
  if (regionKind) return regionKind

  const displayTypes: Record<string, string> = {
    REGION: 'Region',
    TEXTLINE: 'TextLine',
    BASELINE: 'Baseline'
  }

  const type = item.type?.trim()
  return displayTypes[normalizeType(type)] ?? (type || 'Item')
}

function typeOrderWeight(item: TreeItemData): number {
  return TYPE_ORDER[normalizeType(item.type)] ?? 999
}

function parentIdForItem(item: TreeItemData): string | null {
  return item.parentId ?? item.parentPolygonId ?? null
}

export function compareTreeItems(left: TreeItemData, right: TreeItemData): number {
  const leftWeight = typeOrderWeight(left)
  const rightWeight = typeOrderWeight(right)
  if (leftWeight !== rightWeight) return leftWeight - rightWeight

  const leftLabel = (left.label ?? left.id ?? '').toLowerCase()
  const rightLabel = (right.label ?? right.id ?? '').toLowerCase()
  return leftLabel.localeCompare(rightLabel)
}

export function buildChildrenByParentId(polygons: TreeItemData[], polylines: TreeItemData[]): Map<string, TreeItemData[]> {
  const map = new Map<string, TreeItemData[]>()

  const addChild = (child: TreeItemData) => {
    const parentId = parentIdForItem(child)
    if (!parentId) return

    const children = map.get(parentId)
    if (children) {
      children.push(child)
      return
    }

    map.set(parentId, [child])
  }

  for (const polygon of polygons) {
    addChild(polygon)
  }

  for (const polyline of polylines) {
    addChild(polyline)
  }

  for (const children of map.values()) {
    children.sort(compareTreeItems)
  }

  return map
}

export function flattenStructureRows(
  roots: TreeItemData[],
  childrenByParentId: Map<string, TreeItemData[]>,
  expandedRegions: Set<string>
): FlatStructureRow[] {
  const rows: FlatStructureRow[] = []
  const visited = new Set<string>()

  const visit = (item: TreeItemData, level: number) => {
    if (visited.has(item.id)) return
    visited.add(item.id)

    const children = childrenByParentId.get(item.id) ?? []
    const hasChildren = children.length > 0
    const isExpanded = hasChildren && expandedRegions.has(item.id)

    rows.push({
      item,
      level,
      hasChildren,
      isExpanded
    })

    if (!isExpanded) return

    for (const child of children) {
      visit(child, level + 1)
    }
  }

  for (const root of roots) {
    visit(root, 0)
  }

  return rows
}
