import type { Commander } from '@/commands'
import type { CommandContext } from '@/commands/editor/types'
import { DeletePolygonCommand, DeletePolylineCommand, ChangeRegionLabelCommand, ChangeRegionKindCommand, DuplicateElementCommand, SimplifyPolygonCommand, BufferPolygonCommand, FitToBoundingBoxCommand, ConvexHullCommand, ReparentElementCommand, UpdateReadingOrderCommand } from '@/commands'
import { MergeElementsCommand } from '@/commands/editor/merge-elements-command'
import { getEditorSession } from '@/session/editor/editor-session'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
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

export type ContextMenuTargetType = 'polygon' | 'polyline'

export interface ContextMenuTarget {
  type: ContextMenuTargetType
  element: RenderablePolygon | RenderablePolyline
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

/**
 * Composable for managing editor commands and context menus.
 * Handles command execution, undo/redo, and context menu interactions.
 */
export function useEditorCommand(
  commander: Commander,
  canvasId: string,
  polygons: RenderablePolygon[],
  polylines: RenderablePolyline[],
  clearHoverAndSelectionCallback: () => void
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

  /**
   * Show context menu for a polygon at the given screen position
   */
  function showContextMenuForPolygon(event: MouseEvent, polygon: RenderablePolygon): void {
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

  /**
   * Show context menu for a polyline at the given screen position
   */
  function showContextMenuForPolyline(event: MouseEvent, polyline: RenderablePolyline): void {
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

  /**
   * Handle context menu item selection
   */
  async function handleContextMenuSelect(item: ContextMenuItem): Promise<void> {
    if (!contextMenuTarget.value) return

    const session = getEditorSession(canvasId)
    const commandCtx: CommandContext | undefined = session ? { canvasId, session } : undefined

    const target = contextMenuTarget.value

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

        const parts = item.id.replace('region-type-', '').split('-')
        const newKind: RegionKind = kindParts.join('') as RegionKind
        let newSubtype: string | undefined

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

        if (subtypeParts.length > 0) {
          newSubtype = subtypeParts.join('-')
        }

        const changeKindCommand = new ChangeRegionKindCommand({
          regionId: polygon.id,
          newKind,
          newSubtype,
          updateCustom: true,
          newCustom: clearLarexRegionLabelMetadata(polygon.regionCustom)
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
  function addPolygonToReadingOrder(polygonId: string): void {
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

    const commandCtx: CommandContext = { canvasId, session }
    commander.execute(new UpdateReadingOrderCommand({
      readingOrder: addToReadingOrder(page.readingOrder, polygonId)
    }), commandCtx)

    log.debug('Added region to reading order:', polygonId)
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
    openMergeSettingsSlideover: (kinds: RegionKind[]) => Promise<MergeSettings | null>
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
        const settings = await openMergeSettingsSlideover(kinds)
        if (!settings) return undefined // User cancelled
        targetKind = settings.targetKind
        mergeChildren = settings.mergeChildren
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
    duplicatePolygon,
    duplicatePolyline,

    showContextMenuForPolygon,
    showContextMenuForPolyline,
    closeContextMenu,
    handleContextMenuSelect,
    deletePolygon,
    deletePolyline,
    canMergeSelection,
    getSelectedRegionKinds,
    mergeSelected
  }
}
