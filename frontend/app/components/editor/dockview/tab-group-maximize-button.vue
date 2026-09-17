<script setup lang="ts">
import type { IDockviewHeaderActionsProps } from 'dockview-vue'
import { parsePagePanelId, parseProjectPanelId } from '@/stores/editor/editor.keys'
import { getScrollLeftToRevealTab, isDetachedTabHeader } from '@/utils/editor/dockview-tab-layout'

const props = defineProps<{
  params: IDockviewHeaderActionsProps
}>()

const isMaximized = ref(false)
const actionsRef = ref<HTMLElement | null>(null)
const groupRef = ref<HTMLElement | null>(null)
const isProjectGroup = computed(() => props.params.panels.some(panel => Boolean(parseProjectPanelId(panel.id))))
const editorStore = useEditorStore()
const pageProjectId = computed(() => props.params.panels
  .map(panel => parsePagePanelId(panel.id)?.projectId)
  .find((projectId): projectId is string => Boolean(projectId)) ?? null)
const parentProjectTitle = computed(() => {
  const projectId = pageProjectId.value
  if (!projectId) return null
  return editorStore.getProjectPages(projectId)[0]?.projectName ?? projectId
})

let disposable: { dispose: () => void } | null = null
let subscribedContainerApi: IDockviewHeaderActionsProps['containerApi'] | null = null

const groupApi = shallowRef<IDockviewHeaderActionsProps['api'] | null>(null)
const containerApi = shallowRef<IDockviewHeaderActionsProps['containerApi'] | null>(null)
const showParentTab = ref(false)
let layoutDisposable: { dispose: () => void } | null = null
let tabsResizeObserver: ResizeObserver | null = null

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

const updateParentTabVisibility = () => {
  const group = groupRef.value
  const pageHeader = actionsRef.value?.closest<HTMLElement>('.dv-tabs-and-actions-container')
  const parentGroup = group?.parentElement?.closest<HTMLElement>('.dv-groupview')
  const parentHeader = parentGroup?.querySelector<HTMLElement>(':scope > .dv-tabs-and-actions-container')
  if (!pageHeader || !parentHeader) return

  const pageRect = pageHeader.getBoundingClientRect()
  const parentRect = parentHeader.getBoundingClientRect()
  showParentTab.value = isDetachedTabHeader(pageRect, parentRect)
}

const revealActiveTab = () => {
  const tabList = actionsRef.value
    ?.closest<HTMLElement>('.dv-tabs-and-actions-container')
    ?.querySelector<HTMLElement>('.dv-tabs-container')
  const activeTab = tabList?.querySelector<HTMLElement>('.dv-tab.dv-active-tab')
  if (!tabList || !activeTab) return

  tabList.scrollLeft = getScrollLeftToRevealTab({
    scrollLeft: tabList.scrollLeft,
    viewportWidth: tabList.clientWidth,
    tabLeft: activeTab.offsetLeft,
    tabWidth: activeTab.offsetWidth
  })
}

watch(() => props.params, () => {
  syncApis()
  ensureSubscription()
}, { immediate: true })

watchEffect(() => {
  const group = groupRef.value
  if (!group) return
  group.classList.toggle('dv-project-tab-overlay', isProjectGroup.value)
})

watch(() => props.params.activePanel?.id, () => void nextTick(revealActiveTab))

onMounted(() => {
  groupRef.value = actionsRef.value?.closest<HTMLElement>('.dv-groupview') ?? null
  if (pageProjectId.value) {
    layoutDisposable = props.params.containerApi.onDidLayoutChange(updateParentTabVisibility)
    void nextTick(updateParentTabVisibility)
  }
  const tabList = actionsRef.value
    ?.closest<HTMLElement>('.dv-tabs-and-actions-container')
    ?.querySelector<HTMLElement>('.dv-tabs-container')
  if (tabList) {
    tabsResizeObserver = new ResizeObserver(revealActiveTab)
    tabsResizeObserver.observe(tabList)
    void nextTick(revealActiveTab)
  }
})

onUnmounted(() => {
  disposable?.dispose()
  disposable = null
  subscribedContainerApi = null
  layoutDisposable?.dispose()
  layoutDisposable = null
  tabsResizeObserver?.disconnect()
  tabsResizeObserver = null
  groupRef.value?.classList.remove('dv-project-tab-overlay')
})
</script>

<template>
  <Teleport v-if="parentProjectTitle && showParentTab" :to="props.params.group.element">
    <div class="dv-tabs-and-actions-container parent-tab-overlay" aria-hidden="true">
      <div class="dv-tabs-container">
        <div class="dv-tab dv-active-tab">
          <div class="dv-default-tab">
            <div class="dv-default-tab-content">
              <Icon name="i-lucide-folder" class="size-3.5 shrink-0 opacity-70" />
              <span class="truncate" :title="parentProjectTitle">{{ parentProjectTitle }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
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
