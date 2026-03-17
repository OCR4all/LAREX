<script setup lang="ts">
import { useEditorStore } from '@/stores/editor/editor.store'
import { DRAWING_MODES, VIEW_MODES } from '@/composables/editor/use-canvas-control'
import { getTooltipProps } from '@/composables/editor/use-keyboard-shortcuts'
import { useVirtualKeyboardAvailability } from '@/composables/use-virtual-keyboards'
import { PolygonType } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import type { TabsItem } from '@nuxt/ui'
import { ensureEditorSession, getEditorSession } from '@/session/editor/editor-session'

const editorStore = useEditorStore()

const emit = defineEmits<{
  merge: []
}>()

const baseEditorModeItems = [
  {
    label: 'Layout',
    value: 'layout',
    icon: 'i-lucide-layout-dashboard',
    tooltip: getTooltipProps('layoutMode')
  },
  {
    label: 'Text',
    value: 'text',
    icon: 'i-lucide-text-initial',
    tooltip: getTooltipProps('textMode')
  }
] as const

const editorModeItems = computed<TabsItem[]>(() =>
  baseEditorModeItems.map(({ label, tooltip, ...rest }) => ({
    ...rest,
    tooltip,
    ...(!isVertical.value && { label })
  }))
)

const viewModeItems = ref<TabsItem[]>([
  {
    value: VIEW_MODES.DEFAULT,
    icon: 'i-lucide-layers',
    tooltip: getTooltipProps('defaultView')
  },
  {
    value: VIEW_MODES.TEXTLINE,
    icon: 'i-lucide-type',
    tooltip: getTooltipProps('textlineView')
  },
  {
    value: VIEW_MODES.BASELINE,
    icon: 'i-lucide-baseline',
    tooltip: getTooltipProps('baselineView')
  }
])

const toolbarLayoutItems = ref([
  {
    label: 'Floating',
    icon: 'i-lucide-panel-bottom-dashed',
    onSelect() {
      editorStore.setToolbarLayout('floating')
    }
  },
  {
    label: 'Top (Docked)',
    icon: 'i-lucide-panel-top',
    onSelect() {
      editorStore.setToolbarLayout('docked-top')
    }
  },
  {
    label: 'Bottom (Docked)',
    icon: 'i-lucide-panel-bottom',
    onSelect() {
      editorStore.setToolbarLayout('docked-bottom')
    }
  },
  {
    label: 'Left (Docked)',
    icon: 'i-lucide-panel-left',
    onSelect() {
      editorStore.setToolbarLayout('docked-left')
    }
  },
  {
    label: 'Right (Docked)',
    icon: 'i-lucide-panel-right',
    onSelect() {
      editorStore.setToolbarLayout('docked-right')
    }
  }
])

const toolbarLayoutIcon = computed(() => {
  switch (editorStore.toolbarLayout) {
    case 'floating':
      return 'i-lucide-panel-bottom-dashed'
    case 'docked-top':
      return 'i-lucide-panel-top'
    case 'docked-bottom':
      return 'i-lucide-panel-bottom'
    case 'docked-left':
      return 'i-lucide-panel-left'
    case 'docked-right':
      return 'i-lucide-panel-right'
    default:
      return 'i-lucide-panel-top'
  }
})

const props = defineProps({
  canvasId: {
    type: String,
    default: null
  }
})

const isFloating = computed(() => {
  return editorStore.toolbarLayout === 'floating'
})

const isVertical = computed(() => {
  return ['docked-left', 'docked-right'].includes(editorStore.toolbarLayout)
})

const toolbarStyle = computed(() => {
  switch (editorStore.toolbarLayout) {
    case 'floating':
      return 'fixed bottom-0.5 left-1/2 -translate-x-1/2 z-50 border border-default rounded-sm shadow-2xl'

    case 'docked-top':
      return 'row-start-1 col-span-full border-b border-default'

    case 'docked-bottom':
      return 'row-start-2 col-span-full border-t border-default'

    case 'docked-left':
      return 'col-start-1 row-span-full h-full border-r border-default'

    case 'docked-right':
      return 'col-start-2 row-span-full h-full border-l border-default'

    default:
      return 'row-start-1 col-span-full'
  }
})

const currentCanvasId = computed(() => props.canvasId ?? editorStore.activeCanvasId)

const effectiveUiMode = computed(() => editorStore.effectiveUiMode(currentCanvasId.value))

const perPanelUiModeModel = computed({
  get: () => editorStore.uiModeScope === 'per-canvas',
  set: next => editorStore.setUiModeScope(next ? 'per-canvas' : 'global')
})

const currentCanvasState = computed(() => {
  const id = currentCanvasId.value
  if (!id) return undefined
  if (import.meta.client) {
    return ensureEditorSession(id).controls.value ?? undefined
  }
  return getEditorSession(id)?.controls.value ?? undefined
})

const drawingMode = computed(() => currentCanvasState.value?.drawingMode?.value || DRAWING_MODES.SELECT)
const selectedPolygonIndex = computed(() => currentCanvasState.value?.selectedPolygonIndex?.value ?? -1)
const canUndo = computed(() => currentCanvasState.value?.canUndo?.value || false)
const canRedo = computed(() => currentCanvasState.value?.canRedo?.value || false)

const selectedRegionType = computed({
  get: () => currentCanvasState.value?.regionType?.value ?? PolygonType.REGION,
  set: (value) => {
    if (!value) return
    currentCanvasState.value?.setRegionType?.(value)
  }
})

const selectedViewMode = computed({
  get: () => currentCanvasState.value?.viewMode?.value ?? VIEW_MODES.DEFAULT,
  set: (mode) => {
    if (!mode) return
    currentCanvasState.value?.setViewMode?.(mode)
  }
})

const historyItems = ref([])

const historyDropdownItems = computed(() => {
  const currentIndex = currentCanvasState.value?.historyState?.currentIndex ?? -1

  if (historyItems.value.length === 0) {
    return [{ label: 'No commands in history', disabled: true }]
  }

  const initialStateItem = {
    label: `0. Initial state${currentIndex === -1 ? ' →' : ''}`,
    disabled: currentIndex === -1,
    onSelect: () => handleHistoryItemClick(-1)
  }

  const commandItems = historyItems.value.map(item => ({
    label: `${item.index + 1}. ${item.description}${item.isCurrent ? ' →' : ''}`,
    disabled: item.index === currentIndex,
    onSelect: () => handleHistoryItemClick(item.index)
  }))

  return [initialStateItem, ...commandItems]
})

const updateHistoryItems = () => {
  const commander = currentCanvasState.value?.commander
  historyItems.value = commander ? commander.getDetailedHistory() : []
}

const handleHistoryItemClick = (targetIndex) => {
  if (currentCanvasState.value?.jumpToHistory) {
    currentCanvasState.value.jumpToHistory(targetIndex)
    updateHistoryItems()
  }
}

const isSelectMode = computed(() => drawingMode.value === DRAWING_MODES.SELECT)
const isMoveMode = computed(() => drawingMode.value === DRAWING_MODES.MOVE)
const isDrawingMode = computed(() => drawingMode.value !== DRAWING_MODES.SELECT && drawingMode.value !== DRAWING_MODES.MOVE)
const isPolygonMode = computed(() => drawingMode.value === DRAWING_MODES.POLYGON)
const isRectangleMode = computed(() => drawingMode.value === DRAWING_MODES.RECTANGLE)
const isPolylineMode = computed(() => drawingMode.value === DRAWING_MODES.POLYLINE)
const isCutLineMode = computed(() => drawingMode.value === DRAWING_MODES.CUT_LINE)
const isCutPolygonMode = computed(() => drawingMode.value === DRAWING_MODES.CUT_POLYGON)
const isCutRectangleMode = computed(() => drawingMode.value === DRAWING_MODES.CUT_RECTANGLE)
const isCutMode = computed(() => isCutLineMode.value || isCutPolygonMode.value || isCutRectangleMode.value)

const isRegionTypeRegion = computed(() => selectedRegionType.value === PolygonType.REGION)
const isRegionTypeTextline = computed(() => selectedRegionType.value === PolygonType.TEXTLINE)
const isRegionTypeBaseline = computed(() => selectedRegionType.value === PolygonType.BASELINE)

const isTextUiMode = computed(() => effectiveUiMode.value === 'text')

const editorModeModel = computed({
  get: () => effectiveUiMode.value,
  set: (mode: 'layout' | 'text') => {
    editorStore.setUiMode(mode, currentCanvasId.value)
  }
})

const selectedPolygon = computed<RenderablePolygon | undefined>(() => {
  const list = currentCanvasState.value?.polygons as RenderablePolygon[] | undefined
  const idx = selectedPolygonIndex.value
  if (!list || idx < 0 || idx >= list.length) return undefined
  return list[idx]
})

const isSelectedRegion = computed(() => selectedPolygon.value?.type === PolygonType.REGION)
const isSelectedTextline = computed(() => selectedPolygon.value?.type === PolygonType.TEXTLINE)
/** Check if selected region is a TextRegion (can contain TextLines) */
const isSelectedTextRegion = computed(() =>
  isSelectedRegion.value && selectedPolygon.value?.regionKind === 'TextRegion'
)

const selectedTextlineHasBaseline = computed(() => {
  if (!isSelectedTextline.value) return false
  const textlineId = selectedPolygon.value?.id
  if (!textlineId) return false
  const baselines = (currentCanvasState.value?.polylines as RenderablePolyline[] | undefined) ?? []
  return baselines.some(b => b.parentId === textlineId)
})

const canCreateRegion = computed(() => !isSelectedTextline.value)
/**
 * TextLines can be created:
 * 1. When a TextRegion is selected (traditional mode)
 * 2. When in Textline view mode (auto-parent mode - will create helper region if needed)
 */
const canCreateTextline = computed(() =>
  isSelectedTextRegion.value || selectedViewMode.value === VIEW_MODES.TEXTLINE
)
/**
 * Baselines can be created:
 * 1. When a TextLine is selected and has no baseline yet (traditional mode)
 * 2. When in Baseline view mode (auto-parent mode - will create helper textline/region if needed)
 */
const canCreateBaseline = computed(() =>
  (isSelectedTextline.value && !selectedTextlineHasBaseline.value) || selectedViewMode.value === VIEW_MODES.BASELINE
)

const selectedPolygonIds = computed(() => currentCanvasState.value?.selectedPolygonIds?.value ?? [])
const selectedPolylineIds = computed(() => currentCanvasState.value?.selectedPolylineIds?.value ?? [])

const canMerge = computed(() => {
  if (selectedPolylineIds.value.length > 0) return false
  if (selectedPolygonIds.value.length < 2) return false

  const polygonList = currentCanvasState.value?.polygons as RenderablePolygon[] | undefined
  if (!polygonList) return false

  const selectedPolygons = polygonList.filter(p => selectedPolygonIds.value.includes(p.id))
  if (selectedPolygons.length < 2) return false

  const types = new Set(selectedPolygons.map(p => p.type))
  return types.size === 1 && (types.has(PolygonType.REGION) || types.has(PolygonType.TEXTLINE))
})

const handleMerge = () => {
  if (!canMerge.value) return
  emit('merge')
}

function canActivateEntry(entry: 'region' | 'textline' | 'baseline') {
  if (entry === 'region') return canCreateRegion.value
  if (entry === 'textline') return canCreateTextline.value
  return canCreateBaseline.value
}

type ShapeOption = 'polygon' | 'rectangle' | 'polyline'

const preferredShapeByEntry = reactive<{ region: ShapeOption, textline: ShapeOption }>({
  region: 'polygon',
  textline: 'polygon'
})

function getPrimaryShapeForEntry(entry: 'region' | 'textline' | 'baseline'): ShapeOption {
  if (entry === 'baseline') return 'polyline'

  return entry === 'region' ? preferredShapeByEntry.region : preferredShapeByEntry.textline
}

function getIconForShape(option: ShapeOption): string {
  if (option === 'rectangle') return 'i-lucide-square'
  if (option === 'polyline') return 'i-lucide-activity'
  return 'i-lucide-pen-tool'
}

const handleToggleSelectMode = () => {
  if (currentCanvasState.value?.toggleSelectMode) {
    currentCanvasState.value.toggleSelectMode()
  }
}

const handleToggleMoveMode = () => {
  if (currentCanvasState.value?.toggleMoveMode) {
    currentCanvasState.value.toggleMoveMode()
  }
}

const handleUndo = () => {
  if (currentCanvasState.value?.handleUndo) {
    currentCanvasState.value.handleUndo()
    updateHistoryItems()
  }
}

const handleRedo = () => {
  if (currentCanvasState.value?.handleRedo) {
    currentCanvasState.value.handleRedo()
    updateHistoryItems()
  }
}

function setEntryAndMode(entry: 'region' | 'textline' | 'baseline', option?: ShapeOption) {
  if (!currentCanvasState.value) return
  if (!canActivateEntry(entry)) return

  const next = option ?? getPrimaryShapeForEntry(entry)

  if (entry === 'region') {
    preferredShapeByEntry.region = next
  } else if (entry === 'textline') {
    preferredShapeByEntry.textline = next
  }

  if (entry === 'region') {
    selectedRegionType.value = PolygonType.REGION
  } else if (entry === 'textline') {
    selectedRegionType.value = PolygonType.TEXTLINE
  } else {
    selectedRegionType.value = PolygonType.BASELINE
  }

  if (next === 'polygon') {
    currentCanvasState.value.togglePolygonMode?.()
  } else if (next === 'rectangle') {
    currentCanvasState.value.toggleRectangleMode?.()
  } else {
    currentCanvasState.value.togglePolylineMode?.()
  }
}

watchEffect(() => {
  if (!isDrawingMode.value) return

  if (isRegionTypeRegion.value && (isPolygonMode.value || isRectangleMode.value)) {
    preferredShapeByEntry.region = isRectangleMode.value ? 'rectangle' : 'polygon'
  }

  if (isRegionTypeTextline.value && (isPolygonMode.value || isRectangleMode.value)) {
    preferredShapeByEntry.textline = isRectangleMode.value ? 'rectangle' : 'polygon'
  }
})

const uiStore = useEditorUiStore()
const virtualKeyboardMode = computed(() => uiStore.virtualKeyboardMode)
const { hasKeyboards } = useVirtualKeyboardAvailability()
const isCompact = computed(() => uiStore.toolbarCompact)

const showVirtualKeyboardControls = computed(() => !isCompact.value || hasKeyboards.value)
const showSelectAndMove = computed(() => !isCompact.value || !!currentCanvasState.value)
const showRegionTools = computed(() => !isCompact.value || (!!currentCanvasState.value && canCreateRegion.value))
const showTextlineTools = computed(() => !isCompact.value || (!!currentCanvasState.value && canCreateTextline.value))
const showBaselineTool = computed(() => !isCompact.value || (!!currentCanvasState.value && canCreateBaseline.value))
const showCutTools = computed(() => !isCompact.value || !!currentCanvasState.value)
const showMergeTool = computed(() => !isCompact.value || (!!currentCanvasState.value && canMerge.value))
const showUndoTool = computed(() => !isCompact.value || (!!currentCanvasState.value && canUndo.value))
const showRedoTool = computed(() => !isCompact.value || (!!currentCanvasState.value && canRedo.value))
const showHistoryTool = computed(() => !isCompact.value || !!currentCanvasState.value)
const showMoreMenu = computed(() => !isCompact.value || !!currentCanvasState.value)

const vkModeIcon = computed(() => {
  switch (virtualKeyboardMode.value) {
    case 'floating': return 'i-lucide-app-window'
    case 'slideover': return 'i-lucide-panel-bottom'
    default: return 'i-lucide-keyboard-off'
  }
})

const cycleVirtualKeyboardMode = () => {
  const modes: Array<'off' | 'floating' | 'slideover'> = ['off', 'floating', 'slideover']
  const currentIndex = modes.indexOf(virtualKeyboardMode.value)
  const nextIndex = (currentIndex + 1) % modes.length
  uiStore.setVirtualKeyboardMode(modes[nextIndex])
}

const vkDropdownItems = computed(() => [
  [
    {
      label: 'Off',
      icon: 'i-lucide-keyboard-off',
      onSelect: () => uiStore.setVirtualKeyboardMode('off')
    },
    {
      label: 'Floating',
      icon: 'i-lucide-app-window',
      onSelect: () => uiStore.setVirtualKeyboardMode('floating')
    },
    {
      label: 'Slideover',
      icon: 'i-lucide-panel-bottom',
      onSelect: () => uiStore.setVirtualKeyboardMode('slideover')
    }
  ]
])

const regionDropdownItems = computed(() => [
  [
    {
      label: 'Polygon',
      icon: 'i-lucide-pen-tool',
      kbds: getTooltipProps('regionPolygon').kbds,
      color: 'neutral',
      active: isRegionTypeRegion.value && isPolygonMode.value,
      activeColor: 'primary',
      activeVariant: 'solid',
      disabled: !currentCanvasState.value || !canCreateRegion.value,
      onSelect: () => setEntryAndMode('region', 'polygon')
    },
    {
      label: 'Rectangle',
      icon: 'i-lucide-square',
      kbds: getTooltipProps('regionRectangle').kbds,
      color: 'neutral',
      active: isRegionTypeRegion.value && isRectangleMode.value,
      activeColor: 'primary',
      activeVariant: 'solid',
      disabled: !currentCanvasState.value || !canCreateRegion.value,
      onSelect: () => setEntryAndMode('region', 'rectangle')
    }
  ]
])

const textlineDropdownItems = computed(() => [
  [
    {
      label: 'Polygon',
      icon: 'i-lucide-pen-tool',
      kbds: getTooltipProps('textlinePolygon').kbds,
      disabled: !currentCanvasState.value || !canCreateTextline.value,
      class: (isRegionTypeTextline.value && isPolygonMode.value) ? 'bg-primary-50 dark:bg-primary-900/50 text-primary-600' : '',
      onSelect: () => setEntryAndMode('textline', 'polygon')
    },
    {
      label: 'Rectangle',
      icon: 'i-lucide-square',
      kbds: getTooltipProps('textlineRectangle').kbds,
      disabled: !currentCanvasState.value || !canCreateTextline.value,
      class: (isRegionTypeTextline.value && isRectangleMode.value) ? 'bg-primary-50 dark:bg-primary-900/50 text-primary-600' : '',
      onSelect: () => setEntryAndMode('textline', 'rectangle')
    }
  ]
])

const cutDropdownItems = computed(() => [
  [
    {
      label: 'Cut Line',
      icon: 'i-lucide-scissors',
      kbds: getTooltipProps('cutLine').kbds,
      disabled: !currentCanvasState.value,
      class: isCutLineMode.value ? 'bg-primary-50 dark:bg-primary-900/50 text-primary-600' : '',
      onSelect: () => handleToggleCutMode('line')
    },
    {
      label: 'Cut Polygon',
      icon: 'i-lucide-pen-tool',
      kbds: getTooltipProps('cutPolygon').kbds,
      disabled: !currentCanvasState.value,
      class: isCutPolygonMode.value ? 'bg-primary-50 dark:bg-primary-900/50 text-primary-600' : '',
      onSelect: () => handleToggleCutMode('polygon')
    },
    {
      label: 'Cut Rectangle',
      icon: 'i-lucide-square-minus',
      kbds: getTooltipProps('cutRectangle').kbds,
      disabled: !currentCanvasState.value,
      class: isCutRectangleMode.value ? 'bg-primary-50 dark:bg-primary-900/50 text-primary-600' : '',
      onSelect: () => handleToggleCutMode('rectangle')
    }
  ]
])

const preferredCutMode = ref<'line' | 'polygon' | 'rectangle'>('line')

function handleToggleCutMode(mode: 'line' | 'polygon' | 'rectangle') {
  if (!currentCanvasState.value) return

  preferredCutMode.value = mode

  if (mode === 'line') {
    currentCanvasState.value.toggleCutLineMode?.()
  } else if (mode === 'polygon') {
    currentCanvasState.value.toggleCutPolygonMode?.()
  } else {
    currentCanvasState.value.toggleCutRectangleMode?.()
  }
}

const moreOptionsDropdownItems = computed(() => [
  [
    {
      label: 'Compact toolbar',
      icon: 'i-lucide-minimize-2',
      type: 'checkbox',
      checked: isCompact.value,
      onUpdateChecked(checked: boolean) {
        uiStore.setToolbarCompact(checked)
      },
      onSelect(e: Event) {
        e.preventDefault()
      }
    }
  ],
  [
    {
      label: 'Keyboard Shortcuts',
      icon: 'i-lucide-circle-help',
      onSelect: () => uiStore.toggleShortcutsHelp()
    },
    {
      label: 'Lock View',
      icon: perPanelUiModeModel.value ? 'i-lucide-unlock' : 'i-lucide-lock',
      type: 'checkbox',
      checked: !perPanelUiModeModel.value,
      onUpdateChecked(checked: boolean) {
        perPanelUiModeModel.value = !checked
      },
      onSelect(e: Event) {
        e.preventDefault()
      }
    }
  ]
])
</script>

<template>
  <div
    data-tour="editor-toolbar"
    :class="[
      'z-50 print:hidden'
    ]"
  >
    <div
      :class="[
        'flex items-center justify-between dark:bg-neutral-900 bg-neutral-50 border-default',
        toolbarStyle,
        isVertical ? 'flex-col px-1 py-2 overflow-y-scroll' : 'flex-row px-2 py-1 overflow-x-scroll'
      ]"
    >
      <div class="flex items-center" :class="[(isVertical ? 'flex-col' : 'flex-row'), (isCompact ? 'gap-0' : 'gap-1')]">
        <template v-if="isTextUiMode">
          <template v-if="showVirtualKeyboardControls">
            <div class="flex items-center">
              <UFieldGroup>
                <UTooltip :delay-duration="0" v-bind="getTooltipProps('toggleVirtualKeyboard')">
                  <UButton
                    variant="ghost"
                    size="sm"
                    :icon="vkModeIcon"
                    :active="virtualKeyboardMode !== 'off'"
                    color="neutral"
                    active-color="primary"
                    active-variant="solid"
                    :disabled="!hasKeyboards"
                    @click="cycleVirtualKeyboardMode"
                  />
                </UTooltip>
                <UDropdownMenu :items="vkDropdownItems" :popper="{ placement: 'top' }">
                  <UButton
                    variant="ghost"
                    size="sm"
                    icon="i-lucide-chevron-up"
                    color="neutral"
                    :active="virtualKeyboardMode !== 'off'"
                    active-color="primary"
                    active-variant="solid"
                    :disabled="!hasKeyboards"
                    aria-label="Virtual keyboard mode"
                  />
                </UDropdownMenu>
              </UFieldGroup>
            </div>

            <USeparator
              :orientation="isVertical ? 'horizontal' : 'vertical'"
              class="h-6 mx-1"
            />
          </template>

          <UDropdownMenu :items="toolbarLayoutItems">
            <UButton
              :icon="toolbarLayoutIcon"
              color="neutral"
              size="sm"
              variant="ghost"
              aria-label="Toolbar layout"
            />
          </UDropdownMenu>

          <UDropdownMenu v-if="showMoreMenu" :items="moreOptionsDropdownItems">
            <UButton
              variant="ghost"
              icon="i-lucide-more-vertical"
              color="neutral"
              size="xs"
              aria-label="Toolbar settings"
            />
          </UDropdownMenu>
        </template>
        <template v-else>
          <template v-if="showSelectAndMove">
            <UTooltip :delay-duration="0" v-bind="getTooltipProps('selectMode')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-mouse-pointer-2"
                color="neutral"
                :active="isSelectMode"
                active-color="primary"
                active-variant="solid"
                :disabled="!currentCanvasState"
                @click="handleToggleSelectMode"
              />
            </UTooltip>

            <UTooltip :delay-duration="0" v-bind="getTooltipProps('moveMode')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-move"
                color="neutral"
                :active="isMoveMode"
                active-color="primary"
                active-variant="solid"
                :disabled="!currentCanvasState"
                @click="handleToggleMoveMode"
              />
            </UTooltip>
          </template>

          <USeparator
            v-if="showSelectAndMove && (showRegionTools || showTextlineTools || showBaselineTool || showCutTools || showMergeTool)"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <template v-if="!isFloating">
            <div v-if="showRegionTools" data-tour="region-tools" class="contents">
              <UTooltip :delay-duration="0" v-bind="getTooltipProps('regionPolygon')">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-pen-tool"
                  color="neutral"
                  :active="isRegionTypeRegion && isPolygonMode"
                  active-color="primary"
                  active-variant="solid"
                  :disabled="!currentCanvasState || !canCreateRegion"
                  @click="setEntryAndMode('region', 'polygon')"
                />
              </UTooltip>

              <UTooltip :delay-duration="0" v-bind="getTooltipProps('regionRectangle')">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-square"
                  color="neutral"
                  :active="isRegionTypeRegion && isRectangleMode"
                  active-color="primary"
                  active-variant="solid"
                  :disabled="!currentCanvasState || !canCreateRegion"
                  @click="setEntryAndMode('region', 'rectangle')"
                />
              </UTooltip>
            </div>

            <USeparator
              v-if="showRegionTools && showTextlineTools"
              :orientation="isVertical ? 'horizontal' : 'vertical'"
              class="h-6 mx-1"
            />

            <div v-if="showTextlineTools" data-tour="textline-tools" class="contents">
              <UTooltip :delay-duration="0" v-bind="getTooltipProps('textlinePolygon')">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-pen-tool"
                  color="neutral"
                  :active="isRegionTypeTextline && isPolygonMode"
                  active-color="primary"
                  active-variant="solid"
                  :disabled="!currentCanvasState || !canCreateTextline"
                  @click="setEntryAndMode('textline', 'polygon')"
                />
              </UTooltip>

              <UTooltip :delay-duration="0" v-bind="getTooltipProps('textlineRectangle')">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-square"
                  color="neutral"
                  :active="isRegionTypeTextline && isRectangleMode"
                  active-color="primary"
                  active-variant="solid"
                  :disabled="!currentCanvasState || !canCreateTextline"
                  @click="setEntryAndMode('textline', 'rectangle')"
                />
              </UTooltip>
            </div>
          </template>

          <template v-else>
            <div v-if="showRegionTools" data-tour="region-tools" class="flex items-center">
              <UFieldGroup>
                <UTooltip :delay-duration="0" v-bind="getTooltipProps('regionPolygon')">
                  <UButton
                    variant="ghost"
                    size="md"
                    color="neutral"
                    :active="isRegionTypeRegion && (isPolygonMode || isRectangleMode)"
                    active-color="primary"
                    active-variant="solid"
                    :disabled="!currentCanvasState || !canCreateRegion"
                    @click="setEntryAndMode('region', getPrimaryShapeForEntry('region'))"
                  >
                    <Icon :name="getIconForShape(getPrimaryShapeForEntry('region'))" class="h-4 w-4" />
                  </UButton>
                </UTooltip>

                <UDropdownMenu :items="regionDropdownItems" :popper="{ placement: 'top' }">
                  <UButton
                    variant="ghost"
                    size="sm"
                    icon="i-lucide-chevron-up"
                    color="neutral"
                    :active="isRegionTypeRegion && (isPolygonMode || isRectangleMode)"
                    active-color="primary"
                    active-variant="solid"
                    :disabled="!currentCanvasState || !canCreateRegion"
                    aria-label="Region tools"
                  />
                </UDropdownMenu>
              </UFieldGroup>
            </div>

            <USeparator
              v-if="showRegionTools && showTextlineTools"
              :orientation="isVertical ? 'horizontal' : 'vertical'"
              class="h-6 mx-1"
            />

            <div v-if="showTextlineTools" data-tour="textline-tools" class="flex items-center">
              <UFieldGroup>
                <UTooltip :delay-duration="0" v-bind="getTooltipProps('textlinePolygon')">
                  <UButton
                    variant="ghost"
                    size="md"
                    color="neutral"
                    :active="isRegionTypeTextline && (isPolygonMode || isRectangleMode)"
                    active-color="primary"
                    active-variant="solid"
                    :disabled="!currentCanvasState || !canCreateTextline"
                    @click="setEntryAndMode('textline', getPrimaryShapeForEntry('textline'))"
                  >
                    <Icon :name="getIconForShape(getPrimaryShapeForEntry('textline'))" class="h-4 w-4" />
                  </UButton>
                </UTooltip>

                <UDropdownMenu
                  :items="textlineDropdownItems"
                  :popper="{ placement: 'top' }"
                >
                  <UButton
                    variant="ghost"
                    size="sm"
                    icon="i-lucide-chevron-up"
                    color="neutral"
                    :active="isRegionTypeTextline && (isPolygonMode || isRectangleMode)"
                    active-color="primary"
                    active-variant="solid"
                    :disabled="!currentCanvasState || !canCreateTextline"
                    aria-label="Textline tools"
                  />
                </UDropdownMenu>
              </UFieldGroup>
            </div>
          </template>

          <USeparator
            v-if="showBaselineTool && (showRegionTools || showTextlineTools)"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <UTooltip v-if="showBaselineTool" :delay-duration="0" v-bind="canCreateBaseline ? getTooltipProps('baseline') : { text: 'Select a TextLine or switch to Baseline view' }">
            <UButton
              variant="ghost"
              size="sm"
              icon="i-lucide-activity"
              color="neutral"
              :active="isRegionTypeBaseline && isPolylineMode"
              active-color="primary"
              active-variant="solid"
              :disabled="!currentCanvasState || !canCreateBaseline"
              @click="setEntryAndMode('baseline', 'polyline')"
            />
          </UTooltip>

          <USeparator
            v-if="(showRegionTools || showTextlineTools || showBaselineTool) && (showCutTools || showMergeTool)"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <div v-if="showCutTools && !isFloating" data-tour="cut-tools" class="contents">
            <UTooltip :delay-duration="0" v-bind="getTooltipProps('cutLine')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-scissors"
                color="neutral"
                :active="isCutLineMode"
                active-color="primary"
                active-variant="solid"
                :disabled="!currentCanvasState"
                @click="handleToggleCutMode('line')"
              />
            </UTooltip>

            <UTooltip :delay-duration="0" v-bind="getTooltipProps('cutPolygon')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-pen-tool"
                color="neutral"
                :active="isCutPolygonMode"
                active-color="primary"
                active-variant="solid"
                :disabled="!currentCanvasState"
                @click="handleToggleCutMode('polygon')"
              />
            </UTooltip>

            <UTooltip :delay-duration="0" v-bind="getTooltipProps('cutRectangle')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-square-minus"
                color="neutral"
                :active="isCutRectangleMode"
                active-color="primary"
                active-variant="solid"
                :disabled="!currentCanvasState"
                @click="handleToggleCutMode('rectangle')"
              />
            </UTooltip>
          </div>

          <div v-if="showCutTools && isFloating" data-tour="cut-tools" class="flex items-center">
            <UFieldGroup>
              <UTooltip :delay-duration="0" v-bind="getTooltipProps('cutLine')">
                <UButton
                  variant="ghost"
                  size="md"
                  color="neutral"
                  :active="isCutMode"
                  active-color="primary"
                  active-variant="solid"
                  :disabled="!currentCanvasState"
                  @click="handleToggleCutMode(preferredCutMode)"
                >
                  <Icon name="i-lucide-scissors" class="h-4 w-4" />
                </UButton>
              </UTooltip>

              <UDropdownMenu :items="cutDropdownItems" :popper="{ placement: 'top' }">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-chevron-up"
                  color="neutral"
                  :active="isCutMode"
                  active-color="primary"
                  active-variant="solid"
                  :disabled="!currentCanvasState"
                  aria-label="Cut tools"
                />
              </UDropdownMenu>
            </UFieldGroup>
          </div>

          <UTooltip v-if="showMergeTool" :delay-duration="0" v-bind="canMerge ? getTooltipProps('merge') : { text: 'Select 2+ elements of the same type to merge' }">
            <UButton
              variant="ghost"
              size="sm"
              icon="i-lucide-merge"
              color="neutral"
              :disabled="!currentCanvasState || !canMerge"
              @click="handleMerge"
            />
          </UTooltip>

          <USeparator
            v-if="showSelectAndMove || showRegionTools || showTextlineTools || showBaselineTool || showCutTools || showMergeTool"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <UTabs
            v-model="selectedViewMode"
            data-tour="view-mode-tabs"
            :orientation="isVertical ? 'vertical' : 'horizontal'"
            size="sm"
            color="neutral"
            :content="false"
            :items="viewModeItems"
          >
            <template #item="{ item }">
              <UTooltip :delay-duration="0" :text="item.tooltip?.text" :kbds="item.tooltip?.kbds">
                <div class="flex items-center gap-1.5">
                  <Icon v-if="item.icon" :name="item.icon" class="size-4 shrink-0" />
                  <span v-if="item.label">{{ item.label }}</span>
                </div>
              </UTooltip>
            </template>
          </UTabs>

          <USeparator
            v-if="showUndoTool || showRedoTool || showHistoryTool"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <div
            v-if="showUndoTool || showRedoTool || showHistoryTool"
            data-tour="undo-redo"
            class="flex items-center gap-1"
            :class="isVertical ? 'flex-col' : 'flex-row'"
          >
            <UTooltip v-if="showUndoTool" :delay-duration="0" v-bind="getTooltipProps('undo')">
              <UButton
                variant="ghost"
                icon="i-lucide-undo"
                color="neutral"
                size="sm"
                :disabled="!canUndo || !currentCanvasState"
                @click="handleUndo"
              />
            </UTooltip>
            <UTooltip v-if="showRedoTool" :delay-duration="0" v-bind="getTooltipProps('redo')">
              <UButton
                variant="ghost"
                icon="i-lucide-redo"
                color="neutral"
                size="sm"
                :disabled="!canRedo || !currentCanvasState"
                @click="handleRedo"
              />
            </UTooltip>

            <UDropdownMenu v-if="showHistoryTool" :items="historyDropdownItems">
              <UTooltip :delay-duration="0" v-bind="getTooltipProps('history')">
                <UButton
                  variant="ghost"
                  icon="i-lucide-history"
                  color="neutral"
                  size="sm"
                  :disabled="!currentCanvasState"
                  @click="updateHistoryItems"
                />
              </UTooltip>
            </UDropdownMenu>
          </div>

          <USeparator :orientation="isVertical ? 'horizontal' : 'vertical'" class="h-6 mx-1" />

          <UDropdownMenu :items="toolbarLayoutItems">
            <UButton
              :icon="toolbarLayoutIcon"
              color="neutral"
              size="sm"
              variant="ghost"
            />
          </UDropdownMenu>

          <UDropdownMenu v-if="showMoreMenu" :items="moreOptionsDropdownItems">
            <UButton
              variant="ghost"
              icon="i-lucide-more-vertical"
              color="neutral"
              size="xs"
            />
          </UDropdownMenu>
        </template>
      </div>
      <div data-tour="editor-mode-tabs" class="flex gap-x-1 items-center" :class="isVertical ? 'flex-col gap-y-1' : 'flex-row'">
        <UTabs
          v-model="editorModeModel"
          :orientation="isVertical ? 'vertical' : 'horizontal'"
          size="sm"
          color="neutral"
          :content="false"
          :items="editorModeItems"
        >
          <template #item="{ item }">
            <UTooltip :delay-duration="0" :text="item.tooltip?.text" :kbds="item.tooltip?.kbds">
              <div class="flex items-center gap-1.5">
                <Icon v-if="item.icon" :name="item.icon" class="size-4 shrink-0" />
                <span v-if="item.label && !isCompact">{{ item.label }}</span>
              </div>
            </UTooltip>
          </template>
        </UTabs>
      </div>
    </div>
  </div>
</template>
