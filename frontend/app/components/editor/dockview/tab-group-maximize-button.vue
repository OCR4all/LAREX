<script setup lang="ts">
import type { IDockviewHeaderActionsProps } from 'dockview-vue'
import { parseProjectPanelId } from '@/stores/editor/editor.keys'

const props = defineProps<{
  params: IDockviewHeaderActionsProps
}>()

const isMaximized = ref(false)
const actionsRef = ref<HTMLElement | null>(null)
const isProjectGroup = computed(() => props.params.panels.some(panel => Boolean(parseProjectPanelId(panel.id))))

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

onUnmounted(() => {
  disposable?.dispose()
  disposable = null
  subscribedContainerApi = null
})
</script>

<template>
  <div ref="actionsRef" class="header-actions">
    <span v-if="!isProjectGroup" class="page-branch" aria-hidden="true">
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
  width: 24px;
  height: 24px;
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
.page-branch {
  position: absolute;
  top: 5px;
  left: 5px;
}
.page-branch {
  opacity: 0.55;
  pointer-events: none;
}
</style>
