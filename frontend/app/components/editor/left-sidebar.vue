<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { LabelDefinition as ApiLabelDefinition } from '@/types/label-set'
import type { CSSProperties } from 'vue'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'

const props = defineProps<{
  leftRailWidthPx: number
  useFloatingCollapsed: boolean
  imagePopoverDismissKey: number
  logoMenuItems: DropdownMenuItem[][]
  currentProjectId: string | null
  pageNameFilter: string
  filterPopoverOpen: boolean
  availableLabels: ApiLabelDefinition[]
  availableTags: Array<{ label: string, value: string, count: number }>
  openSubtaskPageIds: Set<string>
  hasAdvancedFilters: boolean
  isFiltering: boolean
  totalFilteredPagesAcrossProjects: number
  globalVariantItems: Array<{ label: string, value: string }>
}>()

const emit = defineEmits<{
  'update:pageNameFilter': [value: string]
  'update:filterPopoverOpen': [value: boolean]
  'open-command-center': []
}>()

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()
const { isNotificationsSlideoverOpen } = useDashboard()
const { unreadCount, ensureInitialData } = useNotifications()
const sidebarShellRef = ref<HTMLElement | null>(null)
const floatingPosition = ref<{ x: number, y: number } | null>(null)
const isDraggingSidebar = ref(false)
const isFloatingCollapsed = computed(() => editorUiStore.leftCollapsed && props.useFloatingCollapsed)
const floatingImagePopoverOpen = ref(false)
const collapsedRailImagePopoverOpen = ref(false)

const DEFAULT_FLOATING_SIDEBAR_TOP = 120

await ensureInitialData()

let dragPointerId: number | null = null
let dragStartClientX = 0
let dragStartClientY = 0
let dragStartSidebarX = 0
let dragStartSidebarY = 0

const pageNameFilterModel = computed({
  get: () => props.pageNameFilter,
  set: (value: string) => emit('update:pageNameFilter', value)
})

const filterPopoverOpenModel = computed({
  get: () => props.filterPopoverOpen,
  set: (value: boolean) => emit('update:filterPopoverOpen', value)
})

function handleImageVariantChange(key: string | undefined) {
  editorStore.setPreferredImageVariantKey(key ?? null)
}

function openNotifications() {
  isNotificationsSlideoverOpen.value = true
}

function getViewportSize() {
  return {
    width: window.innerWidth || document.documentElement.clientWidth,
    height: window.innerHeight || document.documentElement.clientHeight
  }
}

function clampSidebarPosition(x: number, y: number) {
  const rect = sidebarShellRef.value?.getBoundingClientRect()
  const { width, height } = getViewportSize()
  const shellWidth = rect?.width ?? 48
  const shellHeight = rect?.height ?? 240
  const maxX = Math.max(8, width - shellWidth - 8)
  const maxY = Math.max(8, height - shellHeight - 8)

  return {
    x: Math.min(Math.max(8, x), maxX),
    y: Math.min(Math.max(8, y), maxY)
  }
}

function getDefaultFloatingPosition() {
  return clampSidebarPosition(16, DEFAULT_FLOATING_SIDEBAR_TOP)
}

function ensureFloatingPosition() {
  if (!import.meta.client || !isFloatingCollapsed.value) return

  if (!floatingPosition.value) {
    floatingPosition.value = getDefaultFloatingPosition()
    return
  }

  floatingPosition.value = clampSidebarPosition(floatingPosition.value.x, floatingPosition.value.y)
}

const floatingSidebarStyle = computed<CSSProperties | undefined>(() => {
  if (!isFloatingCollapsed.value) return undefined

  const position = floatingPosition.value ?? { x: 16, y: DEFAULT_FLOATING_SIDEBAR_TOP }
  return {
    left: `${position.x}px`,
    top: `${position.y}px`
  }
})

function handleSidebarDragMove(event: PointerEvent) {
  if (!isDraggingSidebar.value || event.pointerId !== dragPointerId) return

  floatingPosition.value = clampSidebarPosition(
    dragStartSidebarX + event.clientX - dragStartClientX,
    dragStartSidebarY + event.clientY - dragStartClientY
  )
}

function stopSidebarDrag(event?: PointerEvent) {
  if (event && dragPointerId !== null && event.pointerId !== dragPointerId) return

  window.removeEventListener('pointermove', handleSidebarDragMove)
  window.removeEventListener('pointerup', stopSidebarDrag)
  window.removeEventListener('pointercancel', stopSidebarDrag)

  dragPointerId = null
  isDraggingSidebar.value = false
}

function startSidebarDrag(event: PointerEvent) {
  if (!isFloatingCollapsed.value) return

  ensureFloatingPosition()

  const position = floatingPosition.value ?? getDefaultFloatingPosition()
  dragPointerId = event.pointerId
  dragStartClientX = event.clientX
  dragStartClientY = event.clientY
  dragStartSidebarX = position.x
  dragStartSidebarY = position.y
  isDraggingSidebar.value = true

  window.addEventListener('pointermove', handleSidebarDragMove)
  window.addEventListener('pointerup', stopSidebarDrag)
  window.addEventListener('pointercancel', stopSidebarDrag)
}

function handleFloatingSidebarResize() {
  ensureFloatingPosition()
}

onMounted(() => {
  if (!import.meta.client) return

  requestAnimationFrame(() => ensureFloatingPosition())
  window.addEventListener('resize', handleFloatingSidebarResize)
})

onBeforeUnmount(() => {
  if (!import.meta.client) return

  window.removeEventListener('resize', handleFloatingSidebarResize)
  stopSidebarDrag()
})

watch(() => editorUiStore.leftCollapsed, (collapsed) => {
  if (!import.meta.client || !collapsed || !props.useFloatingCollapsed) return

  requestAnimationFrame(() => ensureFloatingPosition())
})

watch(() => props.useFloatingCollapsed, (enabled) => {
  if (!import.meta.client || !enabled || !editorUiStore.leftCollapsed) return

  requestAnimationFrame(() => ensureFloatingPosition())
})

watch(() => props.imagePopoverDismissKey, () => {
  floatingImagePopoverOpen.value = false
  collapsedRailImagePopoverOpen.value = false
})
</script>

<template>
  <aside
    ref="sidebarShellRef"
    data-tour="editor-left-sidebar"
    :class="isFloatingCollapsed
      ? 'fixed z-40 flex w-12 max-h-[calc(100vh-7rem)] flex-col items-center overflow-y-auto rounded-xl border border-default bg-neutral-50 px-1 py-2 shadow-2xl dark:bg-neutral-900'
      : 'h-full flex flex-col border-r border-default bg-elevated/25'"
    :style="isFloatingCollapsed
      ? floatingSidebarStyle
      : { width: (editorUiStore.leftCollapsed ? leftRailWidthPx : editorUiStore.leftWidthPx) + 'px' }"
  >
    <template v-if="isFloatingCollapsed">
      <UTooltip text="Drag sidebar" :content="{ side: 'right' }">
        <UButton
          variant="ghost"
          color="neutral"
          icon="i-lucide-grip-horizontal"
          size="sm"
          aria-label="Drag sidebar"
          :class="isDraggingSidebar ? 'cursor-grabbing' : 'cursor-grab'"
          @pointerdown.prevent.stop="startSidebarDrag"
          @click.prevent.stop
        />
      </UTooltip>

      <USeparator orientation="horizontal" class="my-1 w-6" />

      <UTooltip text="LAREX menu" :content="{ side: 'right' }">
        <UDropdownMenu :items="logoMenuItems">
          <UButton
            variant="ghost"
            color="neutral"
            square
            size="sm"
            aria-label="Open LAREX menu"
          >
            <UiLogo size="28" class="self-center" />
          </UButton>
        </UDropdownMenu>
      </UTooltip>

      <USeparator orientation="horizontal" class="my-1 w-6" />

      <UTooltip text="Open command center" :content="{ side: 'right' }">
        <UButton
          variant="ghost"
          color="neutral"
          icon="i-lucide-search"
          size="sm"
          class="shrink-0"
          aria-label="Open command center"
          @click="emit('open-command-center')"
        />
      </UTooltip>

      <UPopover v-model:open="floatingImagePopoverOpen" :content="{ side: 'right', align: 'center', sideOffset: 12 }">
        <template #default>
          <UTooltip text="Pages" :content="{ side: 'right' }">
            <UButton
              variant="ghost"
              color="neutral"
              icon="i-lucide-images"
              size="sm"
              class="shrink-0"
              aria-label="Pages"
            />
          </UTooltip>
        </template>
        <template #content>
          <div class="w-96 max-h-[min(82vh,760px)] flex flex-col rounded-xl border border-default bg-neutral-50 p-2 shadow-2xl dark:bg-neutral-900">
            <div class="shrink-0 border-b border-default p-3 space-y-2">
              <div class="flex items-center gap-2">
                <UInput
                  v-model="pageNameFilterModel"
                  size="sm"
                  placeholder="Filter pages…"
                  aria-label="Filter pages by name"
                  class="min-w-0 flex-1"
                />
                <div class="shrink-0 flex items-center gap-1">
                  <EditorPageFilterPopover
                    v-if="currentProjectId"
                    v-model:open="filterPopoverOpenModel"
                    :project-id="currentProjectId"
                    :available-labels="availableLabels"
                    :available-tags="availableTags"
                    :open-subtask-page-ids="openSubtaskPageIds"
                    popover-side="right"
                  />
                  <UPopover :content="{ side: 'right', align: 'start', sideOffset: 8 }">
                    <UTooltip text="Editor settings">
                      <UButton
                        variant="ghost"
                        color="neutral"
                        icon="i-lucide-settings"
                        size="sm"
                        aria-label="Editor settings"
                      />
                    </UTooltip>
                    <template #content>
                      <div class="w-64 p-4 space-y-3">
                        <h3 class="font-semibold text-sm">
                          Settings
                        </h3>
                        <div class="space-y-1.5">
                          <label class="text-xs font-medium text-muted">Image Variant</label>
                          <USelect
                            :model-value="editorStore.preferredImageVariantKey ?? undefined"
                            :items="globalVariantItems"
                            placeholder="Default"
                            size="sm"
                            class="w-full"
                            @update:model-value="handleImageVariantChange"
                          />
                        </div>
                      </div>
                    </template>
                  </UPopover>
                </div>
              </div>

              <div v-if="hasAdvancedFilters" class="min-w-0 text-xs text-muted flex items-center gap-1">
                <UIcon v-if="isFiltering" name="i-lucide-loader-2" class="shrink-0 animate-spin" />
                <span v-else class="truncate">{{ totalFilteredPagesAcrossProjects }} pages match filters</span>
              </div>
            </div>

            <div class="min-h-0 flex-1 overflow-auto editor-sidebar-image-scroll p-2">
              <slot name="image-popover" />
            </div>
          </div>
        </template>
      </UPopover>

      <USeparator orientation="horizontal" class="my-1 w-6" />

      <div class="mb-2 flex flex-col items-center gap-2">
        <UTooltip text="Notifications" :content="{ side: 'right' }">
          <UChip
            inset
            :show="unreadCount > 0"
            :text="unreadCount"
            color="error"
          >
            <UButton
              color="neutral"
              variant="ghost"
              square
              size="sm"
              aria-label="Open notifications"
              @click="openNotifications"
            >
              <UIcon name="i-lucide-bell" class="size-4" />
            </UButton>
          </UChip>
        </UTooltip>
        <AppStatusPopoverTrigger collapsed />
      </div>

      <div class="flex justify-center">
        <UserMenu :collapsed="true" />
      </div>

      <UTooltip text="Expand left sidebar" :content="{ side: 'right' }">
        <UButton
          type="button"
          variant="ghost"
          color="neutral"
          icon="i-lucide-panel-left-open"
          size="sm"
          class="shrink-0"
          aria-label="Expand sidebar"
          @click="editorUiStore.toggleLeftCollapsed"
        />
      </UTooltip>
    </template>

    <template v-else-if="editorUiStore.leftCollapsed">
      <div class="shrink-0 px-0 py-2 border-b border-default">
        <div class="flex w-full h-full items-center px-2 flex-col justify-center gap-1">
          <UDropdownMenu :items="logoMenuItems">
            <div class="flex items-center gap-x-0.5 p-1 hover:bg-accented rounded-sm cursor-pointer">
              <UiLogo size="32" class="self-center" />
            </div>
          </UDropdownMenu>
          <div class="flex items-center gap-1">
            <UButton
              type="button"
              variant="ghost"
              color="neutral"
              icon="i-lucide-panel-left-open"
              aria-label="Expand sidebar"
              @click="editorUiStore.toggleLeftCollapsed"
            />
          </div>
        </div>
      </div>

      <div class="flex-1 min-h-0 flex flex-col px-2 pt-2 gap-y-2">
        <div class="shrink-0 flex flex-col items-center gap-2">
          <UButton
            variant="ghost"
            color="neutral"
            icon="i-lucide-search"
            size="sm"
            aria-label="Open command center"
            @click="emit('open-command-center')"
          />
          <UPopover v-model:open="collapsedRailImagePopoverOpen" :content="{ side: 'right', align: 'start', sideOffset: 8 }">
            <UTooltip text="Pages" :content="{ side: 'right' }">
              <UButton
                variant="ghost"
                color="neutral"
                icon="i-lucide-images"
                size="sm"
                aria-label="Pages"
              />
            </UTooltip>
            <template #content>
              <div class="w-96 max-h-[min(82vh,760px)] flex flex-col">
                <div class="shrink-0 border-b border-default p-3 space-y-2">
                  <div class="flex items-center gap-2">
                    <UInput
                      v-model="pageNameFilterModel"
                      size="sm"
                      placeholder="Filter pages…"
                      aria-label="Filter pages by name"
                      class="min-w-0 flex-1"
                    />
                    <div class="shrink-0 flex items-center gap-1">
                      <EditorPageFilterPopover
                        v-if="currentProjectId"
                        v-model:open="filterPopoverOpenModel"
                        :project-id="currentProjectId"
                        :available-labels="availableLabels"
                        :available-tags="availableTags"
                        :open-subtask-page-ids="openSubtaskPageIds"
                        popover-side="right"
                      />
                      <UPopover :content="{ side: 'right', align: 'start', sideOffset: 8 }">
                        <UTooltip text="Editor settings">
                          <UButton
                            variant="ghost"
                            color="neutral"
                            icon="i-lucide-settings"
                            size="sm"
                            aria-label="Editor settings"
                          />
                        </UTooltip>
                        <template #content>
                          <div class="w-64 p-4 space-y-3">
                            <h3 class="font-semibold text-sm">
                              Settings
                            </h3>
                            <div class="space-y-1.5">
                              <label class="text-xs font-medium text-muted">Image Variant</label>
                              <USelect
                                :model-value="editorStore.preferredImageVariantKey ?? undefined"
                                :items="globalVariantItems"
                                placeholder="Default"
                                size="sm"
                                class="w-full"
                                @update:model-value="handleImageVariantChange"
                              />
                            </div>
                          </div>
                        </template>
                      </UPopover>
                    </div>
                  </div>

                  <div v-if="hasAdvancedFilters" class="min-w-0 text-xs text-muted flex items-center gap-1">
                    <UIcon v-if="isFiltering" name="i-lucide-loader-2" class="shrink-0 animate-spin" />
                    <span v-else class="truncate">{{ totalFilteredPagesAcrossProjects }} pages match filters</span>
                  </div>
                </div>

                <div class="min-h-0 flex-1 overflow-auto editor-sidebar-image-scroll p-2">
                  <slot name="image-popover" />
                </div>
              </div>
            </template>
          </UPopover>
        </div>
      </div>

      <div class="shrink-0 border-t border-default p-2 space-y-2">
        <div class="flex flex-col items-center gap-2">
          <UTooltip text="Notifications" :content="{ side: 'right' }">
            <UChip
              inset
              :show="unreadCount > 0"
              :text="unreadCount"
              color="error"
              position="top-right"
            >
              <UButton
                color="neutral"
                variant="ghost"
                size="sm"
                aria-label="Open notifications"
                @click="openNotifications"
              >
                <UIcon name="i-lucide-bell" class="size-4" />
              </UButton>
            </UChip>
          </UTooltip>
          <AppStatusPopoverTrigger collapsed />
        </div>
        <div class="border-t border-default" />
        <UserMenu :collapsed="true" />
      </div>
    </template>

    <template v-else>
      <div class="shrink-0 px-0 py-2 border-b border-default">
        <div class="flex w-full h-full items-center px-4 justify-between">
          <UDropdownMenu :items="logoMenuItems">
            <div class="flex items-center gap-x-0.5 p-1 hover:bg-accented rounded-sm cursor-pointer">
              <UiLogo size="32" class="self-center" />
              <Icon name="i-lucide-chevron-down" class="self-center" />
            </div>
          </UDropdownMenu>
          <div class="flex items-center gap-1">
            <UButton
              type="button"
              variant="ghost"
              color="neutral"
              icon="i-lucide-panel-left-close"
              aria-label="Collapse sidebar"
              @click="editorUiStore.toggleLeftCollapsed"
            />
          </div>
        </div>
      </div>

      <div class="flex-1 min-h-0 flex flex-col px-2 pt-2 gap-y-2">
        <div class="shrink-0 flex flex-col gap-2">
          <UDashboardSearchButton
            class="bg-transparent ring-default"
            :kbds="['meta', 'k']"
            @click="emit('open-command-center')"
          />

          <div class="border-t border-default" />

          <div class="flex items-center gap-2">
            <UInput
              v-model="pageNameFilterModel"
              size="sm"
              placeholder="Filter pages…"
              aria-label="Filter pages by name"
              class="flex-1"
            />
            <EditorPageFilterPopover
              v-if="currentProjectId"
              v-model:open="filterPopoverOpenModel"
              :project-id="currentProjectId"
              :available-labels="availableLabels"
              :available-tags="availableTags"
              :open-subtask-page-ids="openSubtaskPageIds"
            />
            <UPopover :content="{ side: 'bottom', align: 'end', sideOffset: 8 }">
              <UTooltip text="Editor settings">
                <UButton
                  variant="ghost"
                  color="neutral"
                  icon="i-lucide-settings"
                  size="sm"
                  aria-label="Editor settings"
                />
              </UTooltip>
              <template #content>
                <div class="w-64 p-4 space-y-3">
                  <h3 class="font-semibold text-sm">
                    Settings
                  </h3>
                  <div class="space-y-1.5">
                    <label class="text-xs font-medium text-muted">Image Variant</label>
                    <USelect
                      :model-value="editorStore.preferredImageVariantKey ?? undefined"
                      :items="globalVariantItems"
                      placeholder="Default"
                      size="sm"
                      class="w-full"
                      @update:model-value="handleImageVariantChange"
                    />
                  </div>
                </div>
              </template>
            </UPopover>
          </div>

          <div v-if="hasAdvancedFilters" class="text-xs text-muted flex items-center gap-1">
            <UIcon v-if="isFiltering" name="i-lucide-loader-2" class="animate-spin" />
            <span v-else>{{ totalFilteredPagesAcrossProjects }} pages match filters</span>
          </div>
        </div>

        <div class="min-h-0 flex-1 overflow-auto editor-sidebar-image-scroll">
          <slot />
        </div>
      </div>

      <div class="shrink-0 border-t border-default p-2 space-y-2">
        <div class="flex items-center justify-between">
          <UTooltip text="Notifications" :content="{ side: 'top' }">
            <UChip
              :show="unreadCount > 0"
              :text="unreadCount"
              color="error"
              position="top-right"
            >
              <UButton
                color="neutral"
                variant="ghost"
                square
                size="md"
                aria-label="Open notifications"
                @click="openNotifications"
              >
                <UIcon name="i-lucide-bell" class="size-4" />
              </UButton>
            </UChip>
          </UTooltip>
          <AppStatusPopoverTrigger :collapsed="false" />
        </div>
        <div class="border-t border-default" />
        <UserMenu :collapsed="false" />
      </div>
    </template>
  </aside>
</template>
