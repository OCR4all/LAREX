<script setup lang="ts">
import type { IDockviewHeaderActionsProps } from 'dockview-vue'
import { useFloatingAnchorPosition } from '@/composables/editor/use-floating-anchor-position'
import { EDITOR_WORKSPACE_FLOATING_ANCHOR_ID } from '@/session/editor/editor-session'
import { parseProjectPanelId } from '@/stores/editor/editor.keys'
import type { FloatingControlOffset } from '@/utils/editor/floating-anchor-position'

const props = defineProps<{
  params: IDockviewHeaderActionsProps
}>()

const isMaximized = ref(false)
const actionsRef = ref<HTMLElement | null>(null)
const headerRef = ref<HTMLElement | null>(null)
const groupRef = ref<HTMLElement | null>(null)
const overlayOffset = useState<FloatingControlOffset | null>('editor-tab-overlay-offset', () => null)
const isProjectGroup = computed(() => props.params.panels.some(panel => Boolean(parseProjectPanelId(panel.id))))
const overlayEnabled = computed(() => isProjectGroup.value && Boolean(headerRef.value))
const floatingAnchorId = computed(() => EDITOR_WORKSPACE_FLOATING_ANCHOR_ID)

const {
  style: overlayStyle,
  isDragging: isDraggingOverlay,
  startDrag: startOverlayDrag
} = useFloatingAnchorPosition({
  enabled: overlayEnabled,
  anchorId: floatingAnchorId,
  shellRef: headerRef,
  placement: 'top',
  fallbackSize: { width: 240, height: 76 },
  gap: 16,
  includeFixedPosition: true,
  getOffset: () => overlayOffset.value,
  setOffset: (offset) => {
    overlayOffset.value = offset
  }
})

let disposable: { dispose: () => void } | null = null
let subscribedContainerApi: IDockviewHeaderActionsProps['containerApi'] | null = null

const groupApi = shallowRef<IDockviewHeaderActionsProps['api'] | null>(null)
const containerApi = shallowRef<IDockviewHeaderActionsProps['containerApi'] | null>(null)

const maybeParams = computed(() => props.params as Partial<IDockviewHeaderActionsProps>)

const syncApis = () => {
  if (maybeParams.value.api) {
    groupApi.value = maybeParams.value.api
  }

  if (maybeParams.value.containerApi) {
    containerApi.value = maybeParams.value.containerApi
  }
}

const updateMaximizedState = () => {
  isMaximized.value = groupApi.value?.isMaximized() ?? false
}

const ensureSubscription = () => {
  if (!containerApi.value || !groupApi.value) {
    return
  }

  if (subscribedContainerApi === containerApi.value) {
    updateMaximizedState()
    return
  }

  disposable?.dispose()
  subscribedContainerApi = containerApi.value

  const onDidMaximizedGroupChange = (containerApi.value as {
    onDidMaximizedGroupChange?: ((listener: () => void) => { dispose: () => void })
  }).onDidMaximizedGroupChange

  disposable = typeof onDidMaximizedGroupChange === 'function'
    ? onDidMaximizedGroupChange(() => {
        updateMaximizedState()
      })
    : null

  updateMaximizedState()
}

const onClick = () => {
  const api = groupApi.value
  if (!api) {
    return
  }

  if (isMaximized.value) {
    api.exitMaximized()
  } else {
    api.maximize()
  }
}

watch(() => props.params, () => {
  syncApis()
  ensureSubscription()
}, { immediate: true })

watchEffect(() => {
  const header = headerRef.value
  const group = groupRef.value
  const style = overlayStyle.value
  if (!isProjectGroup.value || !header || !group || !style?.left || !style.top) return

  group.classList.add('dv-project-tab-overlay')
  group.style.setProperty('--dv-tab-overlay-left', String(style.left))
  group.style.setProperty('--dv-tab-overlay-top', String(style.top))
  group.style.setProperty('--dv-tab-overlay-transform', 'none')
})

onMounted(() => {
  headerRef.value = actionsRef.value?.closest<HTMLElement>('.dv-tabs-and-actions-container') ?? null
  groupRef.value = headerRef.value?.closest<HTMLElement>('.dv-groupview') ?? null
})

onUnmounted(() => {
  disposable?.dispose()
  disposable = null
  subscribedContainerApi = null
  groupRef.value?.classList.remove('dv-project-tab-overlay')
  groupRef.value?.style.removeProperty('--dv-tab-overlay-left')
  groupRef.value?.style.removeProperty('--dv-tab-overlay-top')
  groupRef.value?.style.removeProperty('--dv-tab-overlay-transform')
})
</script>

<template>
  <div ref="actionsRef" class="header-actions">
    <button
      v-if="isProjectGroup"
      type="button"
      title="Drag to move tabs"
      class="drag-handle header-button touch-none"
      :class="isDraggingOverlay ? 'cursor-grabbing' : 'cursor-grab'"
      aria-label="Drag tab overlay"
      @pointerdown.prevent.stop="startOverlayDrag"
      @click.prevent.stop
    >
      <Icon name="i-lucide-grip-vertical" :size="16" />
    </button>
    <span v-else class="page-branch" aria-hidden="true">
      <Icon name="i-lucide-corner-down-right" :size="14" />
    </span>
    <button
      type="button"
      :title="isMaximized ? 'Restore tab group' : 'Maximize tab group'"
      class="header-button"
      :aria-label="isMaximized ? 'Restore tab group' : 'Maximize tab group'"
      :aria-pressed="isMaximized"
      @pointerdown.stop
      @click.stop="onClick"
    >
      <Icon :name="isMaximized ? 'i-lucide-minimize-2' : 'i-lucide-maximize-2'" :size="14" />
    </button>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 5px;
}
.header-button,
.page-branch {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--dv-tab-close-icon);
}
.header-button {
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}
.header-button:hover {
  background-color: var(--dv-icon-hover-background-color);
  color: var(--dv-activegroup-visiblepanel-tab-color);
}
.header-button:focus-visible {
  outline: 2px solid var(--dv-paneview-active-outline-color);
  outline-offset: -2px;
}
.drag-handle,
.page-branch {
  position: absolute;
  top: 5px;
  left: 5px;
}
.drag-handle {
  cursor: grab;
}
.drag-handle.cursor-grabbing {
  cursor: grabbing;
}
.page-branch {
  opacity: 0.55;
  pointer-events: none;
}
</style>
