import type { Commander } from '@/commands'
import type { CommandContext } from '@/commands/editor/types'
import { DeletePolygonCommand, DeletePolylineCommand, DeleteSelectedElementsCommand, ChangeRegionLabelCommand, ChangeRegionKindCommand, DuplicateElementCommand, SimplifyPolygonCommand, BufferPolygonCommand, FitToBoundingBoxCommand, ConvexHullCommand, ReparentElementCommand, UpdateReadingOrderCommand, CompoundCommand } from '@/commands'
import { MergeElementsCommand } from '@/commands/editor/merge-elements-command'
import { getEditorSession } from '@/session/editor/editor-session'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import type { ActionTargetSelection } from '@/types/action'
import type { ReadingOrder, ReadingOrderNode, ReadingOrderGroup, RegionRef, RegionKind } from '@/models/editor'
import { ALL_REGION_KINDS, ALL_TEXT_REGION_SUBTYPES, ALL_GRAPHIC_REGION_SUBTYPES, ALL_CHART_REGION_SUBTYPES, canContainTextLines, PolygonType } from '@/models/editor'
import { useEditorStore } from '@/stores/editor/editor.store'
import type { LabelDefinition } from '@/models/editor/labels'
import { getRegionKindDisplayName, getRegionKindIcon } from '@/utils/editor/region-colors'
import { buildMergedCustomForAppliedRegionLabel, clearLarexRegionLabelMetadata, findRegionLabelDefinitionForRegion } from '@/utils/editor/page-label-mapping'
import { createScopedLogger } from '@/services/editor/logger-service'
import type { MergeSettings } from '@/components/editor/slideover/merge-settings.vue'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'

const log = createScopedLogger('EditorCommand')

export interface ContextMenuItem {
  id: string
  label: string
  icon: string
  color?: string
  labelDefinition?: LabelDefinition
  danger?: boolean
  disabled?: boolean
  submenu?: ContextMenuItem[]
}

export type ContextMenuTargetType = 'polygon' | 'polyline' | 'selection' | 'page'

export interface ContextMenuSelection {
  polygonIds: string[]
  polylineIds: string[]
  polygons: RenderablePolygon[]
  polylines: RenderablePolyline[]
}

export interface ContextMenuTarget {
  type: ContextMenuTargetType
  element: RenderablePolygon | RenderablePolyline | null
  selection?: ContextMenuSelection
}

export interface ContextMenuState {
  visible: Ref<boolean>
  x: Ref<number>
  y: Ref<number>
  target: Ref<ContextMenuTarget | null>
  items: Ref<ContextMenuItem[]>
}

/**
 * Type guard for ReadingOrderGroup
 */
function isGroup(node: ReadingOrderNode): node is ReadingOrderGroup {
  return 'elements' in node && Array.isArray((node as ReadingOrderGroup).elements)
}

/**
 * Check if a region is in the reading order
 */
function isRegionInReadingOrder(readingOrder: ReadingOrder | undefined, regionId: string): boolean {
  if (!readingOrder) return false

  function search(node: ReadingOrderNode): boolean {
    if (isGroup(node)) {
      return node.elements.some(child => search(child))
    }
    return (node as RegionRef).regionRef === regionId
  }

  return readingOrder.root.elements.some(element => search(element))
}

/**
 * Add a region to the reading order (append to root)
 */
function addToReadingOrder(readingOrder: ReadingOrder, regionId: string): ReadingOrder {
  const newRef: RegionRef = {
    kind: 'RegionRef',
    id: `ro_${regionId}_${Date.now()}`,
    regionRef: regionId
  }

  return {
    root: {
      ...readingOrder.root,
      elements: [...readingOrder.root.elements, newRef]
    }
  }
}

/**
 * Remove a region from the reading order (recursively searches all groups)
 */
function removeFromReadingOrder(readingOrder: ReadingOrder, regionId: string): ReadingOrder {
  function filterNode(node: ReadingOrderNode): ReadingOrderNode | null {
    if (isGroup(node)) {
      const filteredElements = node.elements
        .map(child => filterNode(child))
        .filter((child): child is ReadingOrderNode => child !== null)

      return {
        ...node,
        elements: filteredElements
      }
    }

    return (node as RegionRef).regionRef === regionId ? null : node
  }

  const filteredElements = readingOrder.root.elements
    .map(element => filterNode(element))
    .filter((element): element is ReadingOrderNode => element !== null)

  return {
    root: {
      ...readingOrder.root,
      elements: filteredElements
    }
  }
}

/**
 * Build submenu items for changing region type.
 * Creates nested submenus for region kinds that have subtypes.
 */
function buildRegionTypeSubmenu(currentKind?: RegionKind): ContextMenuItem[] {
  const items: ContextMenuItem[] = []

  for (const kind of ALL_REGION_KINDS) {
    const isCurrent = kind === currentKind
    const displayName = getRegionKindDisplayName(kind)
    const icon = getRegionKindIcon(kind)

    let submenu: ContextMenuItem[] | undefined

    if (kind === 'TextRegion') {
      submenu = ALL_TEXT_REGION_SUBTYPES.map(subtype => ({
        id: `region-type-${kind}-${subtype}`,
        label: formatSubtypeLabel(subtype),
        icon: 'i-lucide-type'
      }))
    } else if (kind === 'GraphicRegion') {
      submenu = ALL_GRAPHIC_REGION_SUBTYPES.map(subtype => ({
        id: `region-type-${kind}-${subtype}`,
        label: formatSubtypeLabel(subtype),
        icon: 'i-lucide-image'
      }))
    } else if (kind === 'ChartRegion') {
      submenu = ALL_CHART_REGION_SUBTYPES.map(subtype => ({
        id: `region-type-${kind}-${subtype}`,
        label: formatSubtypeLabel(subtype),
        icon: 'i-lucide-bar-chart'
      }))
    }

    items.push({
      id: `region-type-${kind}`,
      label: displayName + (isCurrent ? ' ✓' : ''),
      icon,
      submenu
    })
  }

  return items
}

function isLabelMatchCurrent(label: LabelDefinition, currentKind?: RegionKind, currentSubtype?: string, currentCustom?: string): boolean {
  if (!currentKind) return false
  return Boolean(findRegionLabelDefinitionForRegion([label], {
    regionKind: currentKind,
    regionSubtype: currentSubtype,
    regionCustom: currentCustom
  }))
}

function buildLabelSetSubmenu(labels: LabelDefinition[], currentKind?: RegionKind, currentSubtype?: string, currentCustom?: string): ContextMenuItem[] {
  const ungrouped: ContextMenuItem[] = []
  const grouped = new Map<string, ContextMenuItem[]>()

  for (const label of labels) {
    if (label.scope !== 'region') continue
    const isCurrent = isLabelMatchCurrent(label, currentKind, currentSubtype, currentCustom)
    const fallbackIcon = label.mapping?.pageXml?.regionType
      ? getRegionKindIcon(label.mapping.pageXml.regionType as RegionKind)
      : 'i-lucide-tag'
    const item: ContextMenuItem = {
      id: `label-set-${label.id}`,
      label: label.name + (isCurrent ? ' ✓' : ''),
      icon: fallbackIcon,
      color: label.color,
      labelDefinition: label
    }

    if (label.group) {
      if (!grouped.has(label.group)) grouped.set(label.group, [])
      grouped.get(label.group)!.push(item)
    } else {
      ungrouped.push(item)
    }
  }

  const groupedItems = Array.from(grouped.entries()).map(([group, items]) => ({
    id: `label-group-${group}`,
    label: group,
    icon: 'i-lucide-folder',
    submenu: items
  }))

  return [...ungrouped, ...groupedItems]
}

/**
 * Format a subtype label for display (e.g., "page-number" → "Page Number")
 */
function formatSubtypeLabel(subtype: string): string {
  return subtype
    .split('-')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

function unique(ids: string[]): string[] {
  return Array.from(new Set(ids.filter(Boolean)))
}

function formatElementCount(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`
}

function getCommonRegionState(regions: RenderablePolygon[]): { kind?: RegionKind, subtype?: string, custom?: string } {
  const first = regions[0]
  if (!first) return {}

  const sameKind = regions.every(region => region.regionKind === first.regionKind)
  const sameSubtype = regions.every(region => region.regionSubtype === first.regionSubtype)
  const sameCustom = regions.every(region => region.regionCustom === first.regionCustom)

  return {
    kind: sameKind ? first.regionKind : undefined,
    subtype: sameSubtype ? first.regionSubtype : undefined,
    custom: sameCustom ? first.regionCustom : undefined
  }
}

function parseRegionTypeMenuItemId(itemId: string): { newKind: RegionKind, newSubtype?: string } | null {
  if (!itemId.startsWith('region-type-')) return null

  const parts = itemId.replace('region-type-', '').split('-')
  const kindParts: string[] = []
  const subtypeParts: string[] = []
  let foundRegion = false

  for (const part of parts) {
    if (!foundRegion) {
      kindParts.push(part)
      if (part === 'Region' || part.endsWith('Region')) {
        foundRegion = true
      }
    } else {
      subtypeParts.push(part)
    }
  }

  if (kindParts.length === 0) return null

  return {
    newKind: kindParts.join('') as RegionKind,
    newSubtype: subtypeParts.length > 0 ? subtypeParts.join('-') : undefined
  }
}

/**
 * Composable for managing editor commands and context menus.
 * Handles command execution, undo/redo, and context menu interactions.
 */
export function useEditorCommand(
  commander: Commander,
  canvasId: string,
  polygons: RenderablePolygon[],
  polylines: RenderablePolyline[],
  clearHoverAndSelectionCallback: () => void,
  selectedPolygonIds: Ref<string[]> = ref([]),
  selectedPolylineIds: Ref<string[]> = ref([]),
  openMergeSettingsSlideover?: (kinds: RegionKind[]) => Promise<MergeSettings | null>
) {
  const editorStore = useEditorStore()
  const dialogs = useOverlayDialogs()

  const contextMenuVisible = ref(false)
  const contextMenuX = ref(0)
  const contextMenuY = ref(0)
  const contextMenuTarget = ref<ContextMenuTarget | null>(null)
  const contextMenuItems = ref<ContextMenuItem[]>()

  const pendingBufferPolygon = ref<RenderablePolygon | null>(null)

  const pendingPropertiesTarget = ref<ContextMenuTarget | null>(null)

  /**
   * Apply buffer to pending polygon
   */
  function applyBuffer(distance: number): void {
    if (!pendingBufferPolygon.value) return

    const polygon = pendingBufferPolygon.value
    const elementType = polygon.type === 'region' ? 'region' : 'textline'
    const session = getEditorSession(canvasId)
    const commandCtx: CommandContext | undefined = session ? { canvasId, session } : undefined

    commander.execute(new BufferPolygonCommand({ elementId: polygon.id, elementType, distance }), commandCtx)
    pendingBufferPolygon.value = null
  }

  /**
   * Cancel pending buffer operation
   */
  function cancelBuffer(): void {
    pendingBufferPolygon.value = null
  }

  function closeProperties(): void {
    pendingPropertiesTarget.value = null
  }

  function isRegionInCurrentReadingOrder(regionId: string): boolean {
    const session = getEditorSession(canvasId)
    const readingOrder = session?.document.value?.page?.readingOrder
    return isRegionInReadingOrder(readingOrder, regionId)
  }

  function toggleReadingOrder(regionId: string): void {
    const session = getEditorSession(canvasId)
    if (!session?.document.value?.page) return

    const page = session.document.value.page
    if (!page.readingOrder) {
      page.readingOrder = {
        root: {
          kind: 'OrderedGroup',
          id: 'ro_root',
          elements: []
        }
      }
    }

    const inReadingOrder = isRegionInReadingOrder(page.readingOrder, regionId)
    const nextReadingOrder = inReadingOrder
      ? removeFromReadingOrder(page.readingOrder, regionId)
      : addToReadingOrder(page.readingOrder, regionId)

    const commandCtx: CommandContext = { canvasId, session }
    commander.execute(new UpdateReadingOrderCommand({
      readingOrder: nextReadingOrder
    }), commandCtx)
  }

  function duplicatePolygon(polygonId: string): void {
    const polygon = polygons.find(p => p.id === polygonId)
    if (!polygon) return
    const session = getEditorSession(canvasId)
    const commandCtx: CommandContext | undefined = session ? { canvasId, session } : undefined
    commander.execute(new DuplicateElementCommand({
      elementId: polygon.id,
      elementType: 'polygon',
      parentId: polygon.parentId
    }), commandCtx)
  }

  function duplicatePolyline(polylineId: string): void {
    const polyline = polylines.find(p => p.id === polylineId)
    if (!polyline) return
    const session = getEditorSession(canvasId)
    const commandCtx: CommandContext | undefined = session ? { canvasId, session } : undefined
    commander.execute(new DuplicateElementCommand({
      elementId: polyline.id,
      elementType: 'polyline',
      parentId: polyline.parentId
    }), commandCtx)
  }

  /**
   * Build submenu for reparenting a textline to a different region
   */
  function buildReparentSubmenu(elementId: string, currentParentId?: string): ContextMenuItem[] {
    const validParents = polygons.filter(p =>
      p.type === 'region'
      && p.id !== elementId
      && p.id !== currentParentId
      && !!p.regionKind
      && canContainTextLines(p.regionKind)
    )

    return validParents.map(p => ({
      id: `reparent-to-${p.id}`,
      label: p.label ? `${p.label} (${p.id})` : p.id,
      icon: p.regionKind ? getRegionKindIcon(p.regionKind) : 'i-lucide-square'
    }))
  }

  function buildBulkReparentSubmenu(textlineIds: string[]): ContextMenuItem[] {
    const selected = new Set(textlineIds)
    const currentParents = new Set(
      polygons
        .filter(p => selected.has(p.id))
        .map(p => p.parentId)
        .filter(Boolean)
    )
    const validParents = polygons.filter(p =>
      p.type === 'region'
      && !selected.has(p.id)
      && !(currentParents.size === 1 && currentParents.has(p.id))
      && !!p.regionKind
      && canContainTextLines(p.regionKind)
    )

    return validParents.map(p => ({
      id: `reparent-to-${p.id}`,
      label: p.label ? `${p.label} (${p.id})` : p.id,
      icon: p.regionKind ? getRegionKindIcon(p.regionKind) : 'i-lucide-square'
    }))
  }

  function getPolygonSelectionContext(polygon: RenderablePolygon): ContextMenuSelection | null {
    const ids = unique(selectedPolygonIds.value)
    if (ids.length <= 1 || !ids.includes(polygon.id)) return null

    const selectedPolygons = polygons.filter(p => ids.includes(p.id))
    if (selectedPolygons.length <= 1) return null

    return {
      polygonIds: selectedPolygons.map(p => p.id),
      polylineIds: [],
      polygons: selectedPolygons,
      polylines: []
    }
  }

  function getPolylineSelectionContext(polyline: RenderablePolyline): ContextMenuSelection | null {
    const ids = unique(selectedPolylineIds.value)
    if (ids.length <= 1 || !ids.includes(polyline.id)) return null

    const selectedPolylines = polylines.filter(p => ids.includes(p.id))
    if (selectedPolylines.length <= 1) return null

    return {
      polygonIds: [],
      polylineIds: selectedPolylines.map(p => p.id),
      polygons: [],
      polylines: selectedPolylines
    }
  }

  function getCurrentPageId(): string | null {
    return editorStore.canvases[canvasId]?.pageId ?? null
  }

  function dispatchActionTargetPicked(payload: { targetSelection: ActionTargetSelection, targetSummary: string }): void {
    window.dispatchEvent(new CustomEvent('larex:editor-action-target', { detail: payload }))
  }

  function getActionTargetForPolygon(polygon: RenderablePolygon): { targetSelection: ActionTargetSelection, targetSummary: string } | null {
    const pageId = getCurrentPageId()
    if (!pageId) return null

    if (polygon.type === PolygonType.TEXTLINE) {
      return {
        targetSelection: {
          type: 'TEXT_LINE',
          pages: [{ pageId, regionIds: [], textLineIds: [polygon.id] }]
        },
        targetSummary: `Textline ${polygon.label || polygon.id}`
      }
    }

    return {
      targetSelection: {
        type: 'REGION',
        pages: [{ pageId, regionIds: [polygon.id], textLineIds: [] }]
      },
      targetSummary: `${polygon.label || polygon.regionKind || 'Region'} ${polygon.id}`
    }
  }

  function getActionTargetForSelection(selection: ContextMenuSelection): { targetSelection: ActionTargetSelection, targetSummary: string } | null {
    const pageId = getCurrentPageId()
    if (!pageId || selection.polylineIds.length > 0 || selection.polygonIds.length === 0) return null

    const polygonTypes = new Set(selection.polygons.map(p => p.type))
    if (polygonTypes.size !== 1) return null

    const [polygonType] = Array.from(polygonTypes)
    if (polygonType === PolygonType.TEXTLINE) {
      return {
        targetSelection: {
          type: 'TEXT_LINE',
          pages: [{ pageId, regionIds: [], textLineIds: selection.polygonIds }]
        },
        targetSummary: `${selection.polygonIds.length} ${selection.polygonIds.length === 1 ? 'textline' : 'textlines'}`
      }
    }

    if (polygonType === PolygonType.REGION) {
      return {
        targetSelection: {
          type: 'REGION',
          pages: [{ pageId, regionIds: selection.polygonIds, textLineIds: [] }]
        },
        targetSummary: `${selection.polygonIds.length} ${selection.polygonIds.length === 1 ? 'region' : 'regions'}`
      }
    }

    return null
  }

  function getActionTargetForPage(): { targetSelection: ActionTargetSelection, targetSummary: string } | null {
    const pageId = getCurrentPageId()
    if (!pageId) return null

    return {
      targetSelection: {
        type: 'PAGE',
        pages: [{ pageId, regionIds: [], textLineIds: [] }]
      },
      targetSummary: 'Current page'
    }
  }

  function runActionForContextMenuTarget(target: ContextMenuTarget): void {
    const payload = target.type === 'selection'
      ? (target.selection ? getActionTargetForSelection(target.selection) : null)
      : target.type === 'polygon'
        ? getActionTargetForPolygon(target.element as RenderablePolygon)
        : target.type === 'page'
          ? getActionTargetForPage()
          : null

    if (!payload) return
    dispatchActionTargetPicked(payload)
  }

  function showContextMenuForSelection(event: MouseEvent, clickedElement: RenderablePolygon | RenderablePolyline, selection: ContextMenuSelection): void {
    const polygonCount = selection.polygonIds.length
    const polylineCount = selection.polylineIds.length
    const totalCount = polygonCount + polylineCount
    const selectedPolygons = selection.polygons
    const polygonTypes = new Set(selectedPolygons.map(p => p.type))
    const allRegions = polygonCount > 1 && polygonTypes.size === 1 && selectedPolygons[0]?.type === PolygonType.REGION
    const allTextLines = polygonCount > 1 && polygonTypes.size === 1 && selectedPolygons[0]?.type === PolygonType.TEXTLINE

    contextMenuTarget.value = {
      type: 'selection',
      element: clickedElement,
      selection
    }
    contextMenuX.value = event.clientX
    contextMenuY.value = event.clientY

    const menuItems: ContextMenuItem[] = []

    if (allRegions) {
      const { kind, subtype, custom } = getCommonRegionState(selectedPolygons)
      const labelSet = editorStore.labelSet
      const regionTypeSubmenu = labelSet && labelSet.labels.length > 0
        ? buildLabelSetSubmenu(labelSet.labels, kind, subtype, custom)
        : buildRegionTypeSubmenu(kind)
      menuItems.push({
        id: 'change-region-type',
        label: `Change Type for ${formatElementCount(polygonCount, 'Region')}`,
        icon: 'i-lucide-replace',
        submenu: regionTypeSubmenu
      })

      const session = getEditorSession(canvasId)
      const readingOrder = session?.document.value?.page?.readingOrder
      const selectedRegionIds = selectedPolygons.map(p => p.id)
      if (selectedRegionIds.some(id => !isRegionInReadingOrder(readingOrder, id))) {
        menuItems.push({
          id: 'add-selection-to-reading-order',
          label: 'Add Selection to Reading Order',
          icon: 'i-lucide-book-plus'
        })
      }
      if (selectedRegionIds.some(id => isRegionInReadingOrder(readingOrder, id))) {
        menuItems.push({
          id: 'remove-selection-from-reading-order',
          label: 'Remove Selection from Reading Order',
          icon: 'i-lucide-book-open'
        })
      }
    }

    if (allTextLines) {
      const reparentSubmenu = buildBulkReparentSubmenu(selection.polygonIds)
      if (reparentSubmenu.length > 0) {
        menuItems.push({
          id: 'reparent',
          label: `Move ${formatElementCount(polygonCount, 'Text Line')} to Region`,
          icon: 'i-lucide-move',
          submenu: reparentSubmenu
        })
      }
    }

    if (getActionTargetForSelection(selection)) {
      menuItems.push({
        id: 'run-action',
        label: 'Run Action',
        icon: 'i-lucide-wand-sparkles'
      })
    }

    const mergeState = canMergeSelection(selection.polygonIds, selection.polylineIds)
    if (mergeState.canMerge && mergeState.elementType) {
      menuItems.push({
        id: 'merge-selection',
        label: `Merge ${mergeState.elementType === 'region' ? formatElementCount(polygonCount, 'Region') : formatElementCount(polygonCount, 'Text Line')}`,
        icon: 'i-lucide-merge'
      })
    }

    if (polygonCount > 1 && polylineCount === 0) {
      menuItems.push({
        id: 'shape-tools',
        label: 'Shape Tools',
        icon: 'i-lucide-shapes',
        submenu: [
          { id: 'simplify-selection', label: 'Simplify Selection', icon: 'i-lucide-minimize-2' },
          { id: 'convex-hull-selection', label: 'Convex Hull Selection', icon: 'i-lucide-octagon' },
          { id: 'fit-selection-to-bbox', label: 'Fit Selection to Bounding Boxes', icon: 'i-lucide-square' }
        ]
      })
    }

    menuItems.push(
      {
        id: 'duplicate-selection',
        label: `Duplicate ${formatElementCount(totalCount, 'Element')}`,
        icon: 'i-lucide-copy'
      },
      {
        id: 'delete-selection',
        label: `Delete ${formatElementCount(totalCount, 'Element')}`,
        icon: 'i-lucide-trash-2',
        danger: true
      }
    )

    contextMenuItems.value = menuItems
    contextMenuVisible.value = true
  }

  /**
   * Show context menu for a polygon at the given screen position
   */
  function showContextMenuForPolygon(event: MouseEvent, polygon: RenderablePolygon): void {
    const selection = getPolygonSelectionContext(polygon)
    if (selection) {
      showContextMenuForSelection(event, polygon, selection)
      return
    }

    const isRegion = polygon.type === 'region'
    const isTextLine = polygon.type === 'textline'

    contextMenuTarget.value = {
      type: 'polygon',
      element: polygon
    }
    contextMenuX.value = event.clientX
    contextMenuY.value = event.clientY

    const menuItems: ContextMenuItem[] = []

    if (isRegion) {
      const labelSet = editorStore.labelSet
      const regionTypeSubmenu = labelSet && labelSet.labels.length > 0
        ? buildLabelSetSubmenu(labelSet.labels, polygon.regionKind, polygon.regionSubtype, polygon.regionCustom)
        : buildRegionTypeSubmenu(polygon.regionKind)
      menuItems.push({
        id: 'change-region-type',
        label: 'Change Type',
        icon: 'i-lucide-replace',
        submenu: regionTypeSubmenu
      })
    }

    const session = getEditorSession(canvasId)
    const readingOrder = session?.document.value?.page?.readingOrder
    const inReadingOrder = isRegionInReadingOrder(readingOrder, polygon.id)

    if (isTextLine) {
      const reparentSubmenu = buildReparentSubmenu(polygon.id, polygon.parentId)
      if (reparentSubmenu.length > 0) {
        menuItems.push({
          id: 'reparent',
          label: 'Move to Region',
          icon: 'i-lucide-move',
          submenu: reparentSubmenu
        })
      }
    }

    if (isRegion) {
      menuItems.push({
        id: inReadingOrder ? 'remove-from-reading-order' : 'add-to-reading-order',
        label: inReadingOrder ? 'Remove from Reading Order' : 'Add to Reading Order',
        icon: inReadingOrder ? 'i-lucide-book-open' : 'i-lucide-book-plus'
      })
    }

    if (isRegion || isTextLine) {
      menuItems.push({
        id: 'run-action',
        label: 'Run Action',
        icon: 'i-lucide-wand-sparkles'
      })
    }

    menuItems.push(
      {
        id: 'duplicate',
        label: 'Duplicate',
        icon: 'i-lucide-copy'
      },
      {
        id: 'shape-tools',
        label: 'Shape Tools',
        icon: 'i-lucide-shapes',
        submenu: [
          { id: 'simplify-polygon', label: 'Simplify', icon: 'i-lucide-minimize-2' },
          { id: 'convex-hull', label: 'Convex Hull', icon: 'i-lucide-octagon' },
          { id: 'fit-to-bbox', label: 'Fit to Bounding Box', icon: 'i-lucide-square' },
          { id: 'buffer-polygon', label: 'Expand / Shrink...', icon: 'i-lucide-move-diagonal' }
        ]
      },
      {
        id: 'delete',
        label: 'Delete',
        icon: 'i-lucide-trash-2',
        danger: true
      }
    )

    contextMenuItems.value = menuItems

    contextMenuVisible.value = true
  }

  function showContextMenuForCanvas(event: MouseEvent): void {
    contextMenuTarget.value = {
      type: 'page',
      element: null
    }
    contextMenuX.value = event.clientX
    contextMenuY.value = event.clientY
    contextMenuItems.value = [
      {
        id: 'run-action',
        label: 'Run Action',
        icon: 'i-lucide-wand-sparkles'
      }
    ]
    contextMenuVisible.value = true
  }

  /**
   * Show context menu for a polyline at the given screen position
   */
  function showContextMenuForPolyline(event: MouseEvent, polyline: RenderablePolyline): void {
    const selection = getPolylineSelectionContext(polyline)
    if (selection) {
      showContextMenuForSelection(event, polyline, selection)
      return
    }

    contextMenuTarget.value = {
      type: 'polyline',
      element: polyline
    }
    contextMenuX.value = event.clientX
    contextMenuY.value = event.clientY
    contextMenuItems.value = [
      {
        id: 'duplicate',
        label: 'Duplicate',
        icon: 'i-lucide-copy'
      },
      {
        id: 'delete',
        label: 'Delete Baseline',
        icon: 'i-lucide-trash-2',
        danger: true
      }
    ]

    contextMenuVisible.value = true
  }

  /**
   * Close the context menu
   */
  function closeContextMenu(): void {
    contextMenuVisible.value = false
    contextMenuTarget.value = null
    contextMenuItems.value = []
  }

  function getCommandContext(): CommandContext | undefined {
    const session = getEditorSession(canvasId)
    return session ? { canvasId, session } : undefined
  }

  function clearSelectionRefs(): void {
    selectedPolygonIds.value = []
    selectedPolylineIds.value = []
  }

  async function confirmBulkRegionKindChange(commands: ChangeRegionKindCommand[], newKind: RegionKind): Promise<boolean> {
    const commandCtx = getCommandContext()
    const textLinesToRemove = commands.reduce((sum, command) => sum + command.wouldRemoveTextLines(commandCtx), 0)
    if (textLinesToRemove <= 0) return true

    return await dialogs.confirm({
      title: 'Change Type',
      message: `Converting to ${getRegionKindDisplayName(newKind)} will remove ${textLinesToRemove} text line${textLinesToRemove === 1 ? '' : 's'}. Continue?`,
      confirmLabel: 'Continue',
      cancelLabel: 'Cancel',
      confirmColor: 'warning'
    })
  }

  async function applyLabelDefinitionToSelection(selection: ContextMenuSelection, labelDefinition: LabelDefinition): Promise<void> {
    const mapping = labelDefinition.mapping?.pageXml
    const newKind = mapping?.regionType as RegionKind | undefined
    if (!newKind) return

    const selectedRegions = selection.polygons.filter(p => p.type === PolygonType.REGION)
    if (selectedRegions.length === 0) return

    const commands = selectedRegions.map((polygon) => {
      const newSubtype = newKind === 'TextRegion'
        ? (mapping?.textType === 'custom' ? 'other' : (mapping?.textType || undefined))
        : (mapping?.customSubType || undefined)
      const newCustom = buildMergedCustomForAppliedRegionLabel(polygon.regionCustom, labelDefinition)

      return new ChangeRegionKindCommand({
        regionId: polygon.id,
        newKind,
        newSubtype,
        updateCustom: true,
        newCustom
      })
    })

    if (!(await confirmBulkRegionKindChange(commands, newKind))) return
    commander.execute(new CompoundCommand(commands, `Change type for ${selectedRegions.length} regions`), getCommandContext())
  }

  async function applyRegionTypeToSelection(selection: ContextMenuSelection, itemId: string): Promise<void> {
    const parsed = parseRegionTypeMenuItemId(itemId)
    if (!parsed) return

    const selectedRegions = selection.polygons.filter(p => p.type === PolygonType.REGION)
    if (selectedRegions.length === 0) return

    const commands = selectedRegions.map(polygon => new ChangeRegionKindCommand({
      regionId: polygon.id,
      newKind: parsed.newKind,
      newSubtype: parsed.newSubtype,
      updateCustom: true,
      newCustom: clearLarexRegionLabelMetadata(polygon.regionCustom)
    }))

    if (!(await confirmBulkRegionKindChange(commands, parsed.newKind))) return
    commander.execute(new CompoundCommand(commands, `Change type for ${selectedRegions.length} regions`), getCommandContext())
  }

  async function deleteSelection(selection: ContextMenuSelection): Promise<void> {
    const totalCount = selection.polygonIds.length + selection.polylineIds.length
    if (totalCount === 0) return

    const childParentIds = new Set(selection.polygonIds)
    const childCount = polygons.filter(p => p.parentId && childParentIds.has(p.parentId)).length
      + polylines.filter(p => p.parentId && childParentIds.has(p.parentId)).length

    const confirmed = await dialogs.confirm({
      title: `Delete ${formatElementCount(totalCount, 'Element')}?`,
      message: childCount > 0
        ? `Delete ${totalCount} selected elements and ${childCount} associated child element${childCount === 1 ? '' : 's'}?`
        : `Delete ${totalCount} selected element${totalCount === 1 ? '' : 's'}?`,
      confirmLabel: 'Delete',
      cancelLabel: 'Cancel',
      confirmColor: 'error'
    })
    if (!confirmed) return

    clearHoverAndSelectionCallback()
    clearSelectionRefs()
    commander.execute(new DeleteSelectedElementsCommand({
      polygonIds: selection.polygonIds,
      polylineIds: selection.polylineIds
    }), getCommandContext())
  }

  function duplicateSelection(selection: ContextMenuSelection): void {
    const commands = [
      ...selection.polygons.map(polygon => new DuplicateElementCommand({
        elementId: polygon.id,
        elementType: 'polygon' as const,
        parentId: polygon.parentId
      })),
      ...selection.polylines.map(polyline => new DuplicateElementCommand({
        elementId: polyline.id,
        elementType: 'polyline' as const,
        parentId: polyline.parentId
      }))
    ]

    if (commands.length === 0) return
    commander.execute(new CompoundCommand(commands, `Duplicate ${commands.length} selected elements`), getCommandContext())
  }

  function reparentSelection(selection: ContextMenuSelection, newParentId: string): void {
    const selectedTextLines = selection.polygons.filter(p => p.type === PolygonType.TEXTLINE)
    if (selectedTextLines.length === 0) return

    const commands = selectedTextLines.map(polygon => new ReparentElementCommand({
      elementId: polygon.id,
      elementType: 'textline',
      newParentId
    }))
    commander.execute(new CompoundCommand(commands, `Move ${selectedTextLines.length} text lines to region`), getCommandContext())
  }

  function updateSelectionReadingOrder(selection: ContextMenuSelection, action: 'add' | 'remove'): void {
    const session = getEditorSession(canvasId)
    if (!session?.document.value?.page) return

    const page = session.document.value.page
    if (!page.readingOrder) {
      page.readingOrder = {
        root: {
          kind: 'OrderedGroup',
          id: 'ro_root',
          elements: []
        }
      }
    }

    const selectedRegionIds = selection.polygons
      .filter(p => p.type === PolygonType.REGION)
      .map(p => p.id)

    let nextReadingOrder = page.readingOrder
    for (const regionId of selectedRegionIds) {
      const inReadingOrder = isRegionInReadingOrder(nextReadingOrder, regionId)
      if (action === 'add' && !inReadingOrder) {
        nextReadingOrder = addToReadingOrder(nextReadingOrder, regionId)
      } else if (action === 'remove' && inReadingOrder) {
        nextReadingOrder = removeFromReadingOrder(nextReadingOrder, regionId)
      }
    }

    commander.execute(new UpdateReadingOrderCommand({
      readingOrder: nextReadingOrder
    }), { canvasId, session })
  }

  function executePolygonSelectionShapeCommand(
    selection: ContextMenuSelection,
    factory: (polygon: RenderablePolygon) => SimplifyPolygonCommand | ConvexHullCommand | FitToBoundingBoxCommand,
    description: string
  ): void {
    if (selection.polygons.length === 0 || selection.polylineIds.length > 0) return
    const commands = selection.polygons.map(factory)
    commander.execute(new CompoundCommand(commands, description), getCommandContext())
  }

  async function mergeSelection(selection: ContextMenuSelection): Promise<void> {
    const result = await mergeSelected(selection.polygonIds, openMergeSettingsSlideover)
    if (result) clearSelectionRefs()
  }

  /**
   * Handle context menu item selection
   */
  async function handleContextMenuSelect(item: ContextMenuItem): Promise<void> {
    if (!contextMenuTarget.value) return

    const commandCtx = getCommandContext()

    const target = contextMenuTarget.value

    if (target.type === 'selection' && target.selection) {
      const selection = target.selection

      if (item.labelDefinition) {
        await applyLabelDefinitionToSelection(selection, item.labelDefinition)
        return
      }

      if (item.id.startsWith('region-type-')) {
        await applyRegionTypeToSelection(selection, item.id)
        return
      }

      if (item.id.startsWith('reparent-to-')) {
        reparentSelection(selection, item.id.replace('reparent-to-', ''))
        return
      }

      switch (item.id) {
        case 'run-action':
          runActionForContextMenuTarget(target)
          break
        case 'delete-selection':
          await deleteSelection(selection)
          break
        case 'duplicate-selection':
          duplicateSelection(selection)
          break
        case 'merge-selection':
          await mergeSelection(selection)
          break
        case 'add-selection-to-reading-order':
          updateSelectionReadingOrder(selection, 'add')
          break
        case 'remove-selection-from-reading-order':
          updateSelectionReadingOrder(selection, 'remove')
          break
        case 'simplify-selection':
          executePolygonSelectionShapeCommand(
            selection,
            (polygon) => {
              const elementType = polygon.type === 'region' ? 'region' : 'textline'
              return new SimplifyPolygonCommand({ elementId: polygon.id, elementType })
            },
            `Simplify ${selection.polygons.length} selected polygons`
          )
          break
        case 'convex-hull-selection':
          executePolygonSelectionShapeCommand(
            selection,
            (polygon) => {
              const elementType = polygon.type === 'region' ? 'region' : 'textline'
              return new ConvexHullCommand({ elementId: polygon.id, elementType })
            },
            `Convex hull for ${selection.polygons.length} selected polygons`
          )
          break
        case 'fit-selection-to-bbox':
          executePolygonSelectionShapeCommand(
            selection,
            (polygon) => {
              const elementType = polygon.type === 'region' ? 'region' : 'textline'
              return new FitToBoundingBoxCommand({ elementId: polygon.id, elementType })
            },
            `Fit ${selection.polygons.length} selected polygons to bounding boxes`
          )
          break
      }
      return
    }

    if (item.labelDefinition && target.type === 'polygon') {
      const polygon = target.element as RenderablePolygon
      const mapping = item.labelDefinition.mapping?.pageXml
      const newKind = mapping?.regionType as RegionKind | undefined
      if (!newKind) return
      const newSubtype = newKind === 'TextRegion'
        ? (mapping?.textType === 'custom' ? 'other' : (mapping?.textType || undefined))
        : (mapping?.customSubType || undefined)
      const newCustom = buildMergedCustomForAppliedRegionLabel(polygon.regionCustom, item.labelDefinition)

      const changeKindCommand = new ChangeRegionKindCommand({
        regionId: polygon.id,
        newKind,
        newSubtype,
        updateCustom: true,
        newCustom
      })

      const textLinesToRemove = changeKindCommand.wouldRemoveTextLines(commandCtx)
      if (textLinesToRemove > 0) {
        const confirmed = await dialogs.confirm({
          title: 'Change Type',
          message: `Converting to ${getRegionKindDisplayName(newKind)} will remove ${textLinesToRemove} text line${textLinesToRemove === 1 ? '' : 's'}. Continue?`,
          confirmLabel: 'Continue',
          cancelLabel: 'Cancel',
          confirmColor: 'warning'
        })
        if (!confirmed) return
      }

      commander.execute(changeKindCommand, commandCtx)
      return
    }

    if (item.id.startsWith('region-type-')) {
      if (target.type === 'polygon') {
        const polygon = target.element as RenderablePolygon
        const parsed = parseRegionTypeMenuItemId(item.id)
        if (!parsed) return

        const changeKindCommand = new ChangeRegionKindCommand({
          regionId: polygon.id,
          newKind: parsed.newKind,
          newSubtype: parsed.newSubtype,
          updateCustom: true,
          newCustom: clearLarexRegionLabelMetadata(polygon.regionCustom)
        })

        const textLinesToRemove = changeKindCommand.wouldRemoveTextLines(commandCtx)
        if (textLinesToRemove > 0) {
          const confirmed = await dialogs.confirm({
            title: 'Change Type',
            message: `Converting to ${getRegionKindDisplayName(parsed.newKind)} will remove ${textLinesToRemove} text line${textLinesToRemove === 1 ? '' : 's'}. Continue?`,
            confirmLabel: 'Continue',
            cancelLabel: 'Cancel',
            confirmColor: 'warning'
          })
          if (!confirmed) return
        }

        commander.execute(changeKindCommand, commandCtx)
      }
      return
    }

    if (item.id.startsWith('label-')) {
      if (target.type === 'polygon') {
        const polygon = target.element as RenderablePolygon
        const labelName = item.label

        const changeLabelCommand = new ChangeRegionLabelCommand({
          regionId: polygon.id,
          newLabel: labelName
        })
        commander.execute(changeLabelCommand, commandCtx)
      }
      return
    }

    if (item.id.startsWith('reparent-to-')) {
      if (target.type === 'polygon') {
        const polygon = target.element as RenderablePolygon
        const newParentId = item.id.replace('reparent-to-', '')
        commander.execute(new ReparentElementCommand({
          elementId: polygon.id,
          elementType: 'textline',
          newParentId
        }), commandCtx)
      }
      return
    }

    switch (item.id) {
      case 'run-action':
        runActionForContextMenuTarget(target)
        break
      case 'delete':
        if (target.type === 'polygon') {
          await deletePolygon((target.element as RenderablePolygon).id)
        } else if (target.type === 'polyline') {
          await deletePolyline((target.element as RenderablePolyline).id)
        }
        break
      case 'add-to-reading-order':
        if (target.type === 'polygon') {
          addPolygonToReadingOrder((target.element as RenderablePolygon).id)
        }
        break
      case 'remove-from-reading-order':
        if (target.type === 'polygon') {
          removePolygonFromReadingOrder((target.element as RenderablePolygon).id)
        }
        break
      case 'duplicate':
        if (target.type === 'polygon') {
          const polygon = target.element as RenderablePolygon
          const cmd = new DuplicateElementCommand({
            elementId: polygon.id,
            elementType: 'polygon',
            parentId: polygon.parentId
          })
          commander.execute(cmd, commandCtx)
        } else if (target.type === 'polyline') {
          const polyline = target.element as RenderablePolyline
          const cmd = new DuplicateElementCommand({
            elementId: polyline.id,
            elementType: 'polyline',
            parentId: polyline.parentId
          })
          commander.execute(cmd, commandCtx)
        }
        break
      case 'simplify-polygon':
        if (target.type === 'polygon') {
          const polygon = target.element as RenderablePolygon
          const elementType = polygon.type === 'region' ? 'region' : 'textline'
          commander.execute(new SimplifyPolygonCommand({ elementId: polygon.id, elementType }), commandCtx)
        }
        break
      case 'convex-hull':
        if (target.type === 'polygon') {
          const polygon = target.element as RenderablePolygon
          const elementType = polygon.type === 'region' ? 'region' : 'textline'
          commander.execute(new ConvexHullCommand({ elementId: polygon.id, elementType }), commandCtx)
        }
        break
      case 'fit-to-bbox':
        if (target.type === 'polygon') {
          const polygon = target.element as RenderablePolygon
          const elementType = polygon.type === 'region' ? 'region' : 'textline'
          commander.execute(new FitToBoundingBoxCommand({ elementId: polygon.id, elementType }), commandCtx)
        }
        break
      case 'buffer-polygon':
        if (target.type === 'polygon') {
          pendingBufferPolygon.value = target.element as RenderablePolygon
        }
        break
    }
  }

  /**
   * Add a polygon to the reading order
   */
  function addPolygonToReadingOrder(polygonId: string): boolean {
    const session = getEditorSession(canvasId)
    if (!session?.document.value?.page) return false

    const page = session.document.value.page

    if (!page.readingOrder) {
      page.readingOrder = {
        root: {
          kind: 'OrderedGroup',
          id: 'ro_root',
          elements: []
        }
      }
    }

    if (isRegionInReadingOrder(page.readingOrder, polygonId)) {
      return false
    }

    const commandCtx: CommandContext = { canvasId, session }
    commander.execute(new UpdateReadingOrderCommand({
      readingOrder: addToReadingOrder(page.readingOrder, polygonId)
    }), commandCtx)

    log.debug('Added region to reading order:', polygonId)
    return true
  }

  /**
   * Remove a polygon from the reading order
   */
  function removePolygonFromReadingOrder(polygonId: string): void {
    const session = getEditorSession(canvasId)
    if (!session?.document.value?.page?.readingOrder) return

    const page = session.document.value.page
    if (!page.readingOrder) return

    const commandCtx: CommandContext = { canvasId, session }
    commander.execute(new UpdateReadingOrderCommand({
      readingOrder: removeFromReadingOrder(page.readingOrder, polygonId)
    }), commandCtx)

    log.debug('Removed region from reading order:', polygonId)
  }

  /**
   * Delete a polygon (with confirmation dialog)
   */
  async function deletePolygon(polygonId: string): Promise<void> {
    const polygon = polygons.find(p => p.id === polygonId)
    if (!polygon) return

    const hasPolygonChildren = polygons.some(p => p.parentId === polygonId)
    const hasPolylineChildren = polylines.some(p => p.parentId === polygonId)
    const hasChildren = hasPolygonChildren || hasPolylineChildren

    const confirmed = await dialogs.confirm({
      title: hasChildren ? 'Delete Region and Children?' : 'Delete Region?',
      message: hasChildren
        ? `Are you sure you want to delete "${polygon.label}"? This will also delete all associated textlines and baselines.`
        : `Are you sure you want to delete "${polygon.label}"?`,
      confirmLabel: 'Delete',
      cancelLabel: 'Cancel',
      confirmColor: 'error'
    })
    if (!confirmed) return

    clearHoverAndSelectionCallback()

    const deleteCommand = new DeletePolygonCommand({ polygonId })
    const session = getEditorSession(canvasId)
    const commandCtx: CommandContext | undefined = session ? { canvasId, session } : undefined
    commander.execute(deleteCommand, commandCtx)
  }

  /**
   * Delete a polyline (with confirmation dialog)
   */
  async function deletePolyline(polylineId: string): Promise<void> {
    const polyline = polylines.find(p => p.id === polylineId)
    if (!polyline) return

    const confirmed = await dialogs.confirm({
      title: 'Delete Baseline?',
      message: `Are you sure you want to delete baseline "${polyline.label}"?`,
      confirmLabel: 'Delete',
      cancelLabel: 'Cancel',
      confirmColor: 'error'
    })
    if (!confirmed) return

    clearHoverAndSelectionCallback()

    const deleteCommand = new DeletePolylineCommand({ polylineId })
    const session = getEditorSession(canvasId)
    const commandCtx: CommandContext | undefined = session ? { canvasId, session } : undefined
    commander.execute(deleteCommand, commandCtx)
  }

  /**
   * Check if merge is possible (>1 same-type elements selected)
   */
  function canMergeSelection(selectedPolygonIds: string[], selectedPolylineIds: string[]): { canMerge: boolean, elementType: 'region' | 'textline' | null } {
    if (selectedPolylineIds.length > 0) return { canMerge: false, elementType: null }
    if (selectedPolygonIds.length < 2) return { canMerge: false, elementType: null }

    const selectedPolygons = polygons.filter(p => selectedPolygonIds.includes(p.id))
    if (selectedPolygons.length < 2) return { canMerge: false, elementType: null }

    const types = new Set(selectedPolygons.map(p => p.type))
    if (types.size !== 1) return { canMerge: false, elementType: null }

    const type = selectedPolygons[0]?.type
    if (type === PolygonType.REGION) return { canMerge: true, elementType: 'region' }
    if (type === PolygonType.TEXTLINE) return { canMerge: true, elementType: 'textline' }

    return { canMerge: false, elementType: null }
  }

  /**
   * Get unique region kinds from selected polygons
   */
  function getSelectedRegionKinds(selectedPolygonIds: string[]): RegionKind[] {
    const selectedPolygons = polygons.filter(p => selectedPolygonIds.includes(p.id) && p.type === PolygonType.REGION)
    const kinds = new Set(selectedPolygons.map(p => p.regionKind).filter((k): k is RegionKind => !!k))
    return Array.from(kinds)
  }

  /**
   * Merge selected elements
   */
  async function mergeSelected(
    selectedPolygonIds: string[],
    mergeSettingsCallback?: (kinds: RegionKind[]) => Promise<MergeSettings | null>
  ): Promise<{ id: string } | undefined> {
    const { canMerge, elementType } = canMergeSelection(selectedPolygonIds, [])
    if (!canMerge || !elementType) return undefined

    const session = getEditorSession(canvasId)
    const commandCtx: CommandContext | undefined = session ? { canvasId, session } : undefined

    let targetKind: RegionKind | undefined
    let mergeChildren = true

    if (elementType === 'region') {
      const kinds = getSelectedRegionKinds(selectedPolygonIds)
      if (kinds.length > 1) {
        if (mergeSettingsCallback) {
          const settings = await mergeSettingsCallback(kinds)
          if (!settings) return undefined // User cancelled
          targetKind = settings.targetKind
          mergeChildren = settings.mergeChildren
        } else {
          targetKind = kinds[0]
        }
      } else {
        targetKind = kinds[0]
      }
    }

    const command = new MergeElementsCommand({
      elementIds: selectedPolygonIds,
      elementType,
      targetKind,
      mergeChildren
    })

    clearHoverAndSelectionCallback()
    return commander.execute(command, commandCtx)
  }

  return {
    contextMenuVisible,
    contextMenuX,
    contextMenuY,
    contextMenuTarget,
    contextMenuItems,

    pendingBufferPolygon,
    applyBuffer,
    cancelBuffer,

    pendingPropertiesTarget,
    closeProperties,
    isRegionInCurrentReadingOrder,
    toggleReadingOrder,
    addPolygonToReadingOrder,
    duplicatePolygon,
    duplicatePolyline,

    showContextMenuForPolygon,
    showContextMenuForPolyline,
    showContextMenuForCanvas,
    closeContextMenu,
    handleContextMenuSelect,
    deletePolygon,
    deletePolyline,
    canMergeSelection,
    getSelectedRegionKinds,
    mergeSelected
  }
}
