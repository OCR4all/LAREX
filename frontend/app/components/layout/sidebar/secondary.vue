<script setup lang="ts">
import { useStorage } from '@vueuse/core'

interface Props {
  id: string
  storageKey: string
  icon: string
  label: string
  defaultSize: number
  minSize?: number
  maxSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  minSize: 16,
  maxSize: 30
})

const route = useRoute()
const isDesktop = useMediaQuery('(min-width: 1024px)')
const isMounted = ref(false)
const isMobileOpen = ref(false)

const desktopWidthRem = useStorage<number>(`${props.storageKey}:size`, props.defaultSize)
const isCollapsed = useStorage<boolean>(`${props.storageKey}:collapsed`, false)

const collapsedWidthRem = 3.5

const showDesktopSidebar = computed(() => !isMounted.value || isDesktop.value)
const showMobileSidebar = computed(() => isMounted.value && !isDesktop.value)

const clampedWidthRem = computed(() =>
  Math.min(props.maxSize, Math.max(props.minSize, Number(desktopWidthRem.value) || props.defaultSize))
)

const sidebarWidthRem = computed(() => (isCollapsed.value ? collapsedWidthRem : clampedWidthRem.value))

const desktopSidebarStyle = computed(() => ({
  width: `${sidebarWidthRem.value}rem`
}))

let resizeCleanup: (() => void) | null = null

function setDesktopWidthFromPixels(nextWidthPx: number): void {
  const rootFontSize = Number.parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
  const nextWidthRem = nextWidthPx / rootFontSize
  desktopWidthRem.value = Math.min(props.maxSize, Math.max(props.minSize, nextWidthRem))
}

function stopResize(): void {
  resizeCleanup?.()
  resizeCleanup = null
}

function startResize(event: MouseEvent): void {
  if (isCollapsed.value) return

  event.preventDefault()

  const sidebarElement = document.getElementById(props.id)
  if (!sidebarElement) return

  const startX = event.clientX
  const startWidthPx = sidebarElement.getBoundingClientRect().width

  const handleMouseMove = (moveEvent: MouseEvent) => {
    const delta = moveEvent.clientX - startX
    setDesktopWidthFromPixels(startWidthPx + delta)
  }

  const handleMouseUp = () => {
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    stopResize()
  }

  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp, { once: true })

  resizeCleanup = () => {
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
  }
}

function resetWidth(): void {
  desktopWidthRem.value = props.defaultSize
}

function toggleCollapsed(): void {
  isCollapsed.value = !isCollapsed.value
}

function openMobileSidebar(): void {
  isCollapsed.value = false
  isMobileOpen.value = true
}

onMounted(() => {
  isMounted.value = true
})

onBeforeUnmount(() => {
  stopResize()
})

watch(() => route.fullPath, () => {
  isMobileOpen.value = false
})

watch(isDesktop, (desktop) => {
  if (desktop) {
    isMobileOpen.value = false
  }
})
</script>

<template>
  <div class="flex min-h-0 min-w-0 flex-1 overflow-hidden">
    <aside
      v-if="showDesktopSidebar"
      :id="id"
      class="hidden h-full min-h-0 shrink-0 border-r border-default bg-elevated/25 lg:flex"
      :style="desktopSidebarStyle"
    >
      <div class="flex h-full min-h-0 w-full flex-col overflow-hidden">
        <div
          class="border-b border-default"
          :class="isCollapsed ? 'px-2 py-2' : 'px-4 py-2'"
        >
          <div
            class="flex h-[calc(var(--ui-header-height)-1rem)] w-full"
            :class="isCollapsed ? 'flex-col items-center justify-between' : 'items-center justify-between gap-3'"
          >
            <div class="flex min-w-0 items-center gap-3">
              <UIcon :name="icon" class="size-5 shrink-0 text-muted" />
              <span v-if="!isCollapsed" class="truncate text-sm font-medium">
                {{ label }}
              </span>
            </div>

            <UButton
              color="neutral"
              variant="ghost"
              :class="isCollapsed ? 'mb-1' : undefined"
              :icon="isCollapsed ? 'i-lucide-panel-left-open' : 'i-lucide-panel-left-close'"
              :aria-label="isCollapsed ? `Expand ${label}` : `Collapse ${label}`"
              @click="toggleCollapsed"
            />
          </div>
        </div>

        <div v-if="!isCollapsed" class="min-h-0 flex-1 overflow-hidden">
          <slot name="sidebar" />
        </div>

        <div
          v-else-if="$slots.collapsed"
          class="flex min-h-0 flex-1 flex-col items-center gap-2 overflow-y-auto px-1 py-3"
        >
          <slot name="collapsed" />
        </div>
      </div>
    </aside>

    <div
      v-if="showDesktopSidebar"
      class="group relative hidden w-0 shrink-0 cursor-col-resize lg:block"
      @mousedown="startResize"
      @dblclick="resetWidth"
    >
      <div class="absolute inset-y-0 left-0 w-3 -translate-x-1/2" />
      <div class="absolute inset-y-0 left-0 w-px bg-default transition-colors group-hover:bg-primary/40" />
    </div>

    <div class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <div v-if="showMobileSidebar" class="border-b border-default lg:hidden">
        <UButton
          color="neutral"
          variant="ghost"
          class="w-full justify-start rounded-none px-4 py-3"
          :leading-icon="icon"
          :label="label"
          @click="openMobileSidebar"
        />
      </div>

      <slot />
    </div>

    <USlideover
      v-if="showMobileSidebar"
      v-model:open="isMobileOpen"
      side="left"
      :title="label"
      :close="{ color: 'neutral', variant: 'ghost' }"
      :ui="{ body: 'p-0', content: 'sm:max-w-sm' }"
    >
      <template #body>
        <div class="flex h-full min-h-0 flex-col overflow-hidden bg-elevated/25">
          <div class="min-h-0 flex-1 overflow-hidden">
            <slot name="sidebar" />
          </div>
        </div>
      </template>
    </USlideover>
  </div>
</template>
