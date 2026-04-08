<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { useCollaborationPageSummary } from '@/composables/use-collaboration-page-summary'
import type { PageData } from '@/stores/editor/types'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import UiColorTag from '@/components/ui/color-tag.vue'
import { copyTextToClipboard } from '@/utils/clipboard'
import { getAvatarInitials, resolveManagedProfileAvatarSrc } from '@/utils/avatar'

type VariantItem = { label: string, value: string }

type BadgeType = 'annotated' | 'reviewed' | 'pending' | 'error'

const DEFAULT_CUSTOM_TAG_COLOR = '#2563eb'

const badgeConfig: Record<BadgeType, { label: string, dot: string }> = {
  annotated: {
    label: 'Annotated',
    dot: 'bg-emerald-400'
  },
  reviewed: {
    label: 'Reviewed',
    dot: 'bg-sky-400'
  },
  pending: {
    label: 'Pending',
    dot: 'bg-amber-400'
  },
  error: {
    label: 'Error',
    dot: 'bg-red-400'
  }
}

const props = defineProps<{
  page: PageData
  currentPageId: string | null
  previewUrl: string | null
  variantItems: VariantItem[]
  selectedVariant: string | null
  openSubtaskCount?: number
}>()

const emit = defineEmits<{
  'select-page': [page: PageData]
  'variant-change': [variantId: string]
  'unload-page': [page: PageData]
}>()

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()
const pageSummaries = useCollaborationPageSummary()
const toast = useToast()
const router = useRouter()

const isCollapsed = computed(() => editorUiStore.leftCollapsed)
const isSelected = computed(() => props.page.id === props.currentPageId)

const pageLabel = computed(() => props.page.label || props.page.id)
const openTasks = computed(() => props.openSubtaskCount ?? 0)
const hasAnnotations = computed(() => (props.page.xmlFiles?.length ?? 0) > 0 || (props.page.xmlFileCount ?? 0) > 0)
const hasUnsavedChanges = computed(() => editorStore.hasUnsavedChangesForPage(props.page.id))
const indexingStatus = computed(() => props.page.indexingStatus ?? 'NOT_APPLICABLE')
const showIndexingIndicator = computed(() => indexingStatus.value === 'UNINDEXED' || indexingStatus.value === 'INDEXING')
const indexingIndicatorClasses = computed(() => {
  if (indexingStatus.value === 'INDEXING') {
    return 'bg-amber-400 shadow-[0_0_0_2px_rgba(251,191,36,0.2)] animate-pulse'
  }
  return 'bg-red-400 shadow-[0_0_0_2px_rgba(248,113,113,0.2)]'
})
const indexingIndicatorLabel = computed(() => indexingStatus.value === 'INDEXING' ? 'Indexing in background' : 'Not indexed yet')

const badges = computed<BadgeType[]>(() => [])
const maxVisibleBadges = 4
const visibleBadges = computed(() => badges.value.slice(0, maxVisibleBadges))
const overflowCount = computed(() => Math.max(0, badges.value.length - maxVisibleBadges))

const displayTags = computed(() => {
  const resolvedTags = props.page.resolvedTags ?? []
  if (resolvedTags && resolvedTags.length > 0) {
    return resolvedTags.map(tag => ({
      label: tag.label || tag.id,
      color: tag.color || DEFAULT_CUSTOM_TAG_COLOR
    }))
  }

  const rawTags = props.page.tags ?? []
  return rawTags.map(tagId => ({
    label: tagId,
    color: DEFAULT_CUSTOM_TAG_COLOR
  }))
})

const maxVisibleTagDots = 4
const visibleTagDots = computed(() => displayTags.value.slice(0, maxVisibleTagDots))
const hiddenTagDotCount = computed(() => Math.max(0, displayTags.value.length - maxVisibleTagDots))
const collaborationSummary = computed(() => pageSummaries.getPageSummary(props.page.id, props.page.projectId))
const collaborationEditor = computed(() => collaborationSummary.value?.editor ?? null)
const collaborationAvatarRingClass = computed(() => {
  const summary = collaborationSummary.value
  if (!summary?.editor) return ''
  return summary.isLive ? 'ring-emerald-400/90' : 'ring-neutral-400/90'
})

const collaborationTooltip = computed(() => {
  const summary = collaborationSummary.value
  if (!summary) return ''

  if (summary.editor) {
    const activity = summary.isLive ? 'Live' : 'Idle'
    const viewers = summary.viewerCount > 0 ? `, ${summary.viewerCount} viewer${summary.viewerCount === 1 ? '' : 's'}` : ''
    const pending = summary.hasPendingTakeover ? ', pending request' : ''
    return `${summary.editor.user.displayName} editing (${activity}${viewers}${pending})`
  }

  return `${summary.viewerCount} viewer${summary.viewerCount === 1 ? '' : 's'} watching`
})

function collaborationAvatarText() {
  const editor = collaborationEditor.value?.user
  if (!editor) return 'U'

  return getAvatarInitials({
    name: editor.displayName,
    username: editor.username
  })
}

watch(() => props.page.projectId, (value) => {
  if (value) {
    void pageSummaries.ensureProjectSummary(value)
  }
}, { immediate: true })

const selectUi = {
  base: 'h-7 w-full text-[11px] px-2 bg-neutral-800/80 border border-neutral-700/50 text-neutral-300 hover:bg-neutral-700/80 hover:border-neutral-600 focus:ring-1 focus:ring-primary-500/50 focus:border-primary-500/50 backdrop-blur-sm',
  value: 'text-neutral-200',
  placeholder: 'text-neutral-500',
  trailing: 'text-neutral-400',
  content: 'bg-neutral-900 border border-neutral-700 min-w-[120px] shadow-lg',
  item: 'text-xs text-neutral-300 focus:bg-neutral-800 focus:text-neutral-100'
}

const tooltipUi = {
  content: 'bg-neutral-900 border border-neutral-700'
}

const contextMenuItems = computed<DropdownMenuItem[][]>(() => [[
  {
    label: 'Open Page',
    icon: 'i-lucide-file-text',
    onSelect: () => handleSelectPage()
  },
  {
    label: 'Copy Page Link',
    icon: 'i-lucide-link',
    disabled: !props.page.projectId,
    onSelect: () => { void handleCopyPageLink() }
  },
  {
    label: 'Copy Page ID',
    icon: 'i-lucide-copy',
    onSelect: () => { void handleCopyPageId() }
  }
], [
  {
    label: 'Unload Page',
    icon: 'i-lucide-x',
    color: 'error',
    onSelect: () => handleUnloadPage()
  }
]])

const popoverOpen = ref(false)
const selectOpen = ref(false)
let closeTimeout: ReturnType<typeof setTimeout> | null = null

function onMouseEnter() {
  if (closeTimeout) {
    clearTimeout(closeTimeout)
    closeTimeout = null
  }
  popoverOpen.value = true
}

function onMouseLeave() {
  if (selectOpen.value) return
  closeTimeout = setTimeout(() => {
    popoverOpen.value = false
  }, 100)
}

function onSelectOpenChange(open: boolean) {
  selectOpen.value = open
  if (!open && closeTimeout === null) {
    closeTimeout = setTimeout(() => {
      popoverOpen.value = false
    }, 100)
  }
}

function handleVariantChange(variantId: string | null) {
  if (variantId) emit('variant-change', variantId)
}

function handleSelectPage() {
  emit('select-page', props.page)
  popoverOpen.value = false
}

function handleUnloadPage() {
  emit('unload-page', props.page)
}

function getPageDeepLink(): string | null {
  const projectId = props.page.projectId
  if (!projectId) return null

  const query: Record<string, string> = {
    projectId,
    pageId: props.page.id
  }
  if (props.selectedVariant) {
    query.variantId = props.selectedVariant
  }

  const href = router.resolve({ path: '/editor', query }).href
  if (typeof window === 'undefined') return href
  return new URL(href, window.location.origin).toString()
}

async function copyToClipboard(text: string, successTitle: string) {
  await copyTextToClipboard(text, {
    successTitle,
    failureTitle: 'Copy failed',
    failureDescription: 'Your browser blocked clipboard access.'
  })
}

async function handleCopyPageLink() {
  const deepLink = getPageDeepLink()
  if (!deepLink) {
    toast.add({
      title: 'Cannot copy page link',
      description: 'Missing project context for this page.',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
    return
  }

  await copyToClipboard(deepLink, 'Page link copied')
}

async function handleCopyPageId() {
  await copyToClipboard(props.page.id, 'Page ID copied')
}
</script>

<template>
  <UContextMenu v-if="!isCollapsed" :items="contextMenuItems">
    <div
      :class="[
        'group relative rounded-sm overflow-hidden cursor-pointer transition-all duration-200',
        'bg-neutral-900 border',
        isSelected
          ? 'border-primary-500 ring-2 ring-primary-500/20'
          : 'border-neutral-800 hover:border-neutral-700'
      ]"
      @click="handleSelectPage"
    >
      <UTooltip
        v-if="showIndexingIndicator"
        :text="indexingIndicatorLabel"
        :content="{ side: 'right' }"
        :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
      >
        <div class="absolute top-2 right-2 z-10 flex h-3 w-3 items-center justify-center rounded-full bg-neutral-950/90 border border-neutral-700/70">
          <span :class="['h-1.5 w-1.5 rounded-full', indexingIndicatorClasses]" />
        </div>
      </UTooltip>

      <div class="relative aspect-3/4 bg-neutra-950">
        <img
          v-if="previewUrl"
          :src="previewUrl"
          :alt="`Page ${pageLabel}`"
          class="absolute inset-0 w-full h-full object-cover"
          loading="lazy"
        >

        <div
          v-else
          class="absolute inset-0 flex items-center justify-center bg-linear-to-br from-neutral-900 to-neutral-950 text-neutral-400"
        >
          <Icon name="i-lucide-file-text" class="h-5 w-5" />
        </div>

        <div class="absolute inset-x-0 top-0 p-2 flex items-start justify-between gap-2">
          <span class="inline-flex px-1.5 py-0.5 text-sm font-mono font-medium bg-neutral-900/90 text-neutral-300 rounded border border-neutral-700/50 backdrop-blur-sm">
            {{ pageLabel }}
          </span>

          <div class="flex items-center gap-1">
            <UTooltip
              v-if="collaborationEditor"
              :text="collaborationTooltip"
              :content="{ side: 'right' }"
              :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
            >
              <UAvatar
                :src="resolveManagedProfileAvatarSrc(collaborationEditor.user.avatar)"
                :alt="collaborationEditor.user.displayName"
                :text="collaborationAvatarText()"
                size="xs"
                :class="['ring-2', collaborationAvatarRingClass]"
              />
            </UTooltip>
            <UTooltip
              v-else-if="collaborationSummary?.viewerCount"
              :text="collaborationTooltip"
              :content="{ side: 'right' }"
              :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
            >
              <UBadge
                color="info"
                variant="subtle"
                size="xs"
                icon="i-lucide-eye"
              >
                Watching
              </UBadge>
            </UTooltip>
            <UPopover
              v-if="displayTags.length > 0"
              mode="hover"
              :open-delay="150"
              :close-delay="100"
              :content="{ side: 'right', align: 'end', sideOffset: 6 }"
              :ui="{ content: `${tooltipUi.content} p-2 z-999` }"
              :portal="false"
            >
              <template #default>
                <div class="flex items-center gap-1 px-1.5 py-1 bg-neutral-900/90 rounded border border-neutral-700/50 backdrop-blur-sm">
                  <span
                    v-for="tag in visibleTagDots"
                    :key="tag.label"
                    class="w-2 h-2 rounded-full"
                    :style="{ backgroundColor: tag.color }"
                  />
                  <span v-if="hiddenTagDotCount > 0" class="text-[9px] text-neutral-400 ml-0.5">+{{ hiddenTagDotCount }}</span>
                </div>
              </template>
              <template #content>
                <div class="flex flex-col gap-1.5">
                  <UiColorTag
                    v-for="tag in displayTags"
                    :key="tag.label"
                    :color="tag.color"
                    variant="subtle"
                    size="sm"
                    dot
                  >
                    {{ tag.label }}
                  </UiColorTag>
                </div>
              </template>
            </UPopover>

            <UTooltip v-if="badges.length > 0" :delay-duration="200" :ui="{ ...tooltipUi, content: `${tooltipUi.content} p-2` }">
              <template #default>
                <div class="flex items-center gap-1 px-1.5 py-1 bg-neutral-900/90 rounded border border-neutral-700/50 backdrop-blur-sm">
                  <span
                    v-for="badge in visibleBadges"
                    :key="badge"
                    :class="['w-2 h-2 rounded-full', badgeConfig[badge].dot]"
                  />
                  <span v-if="overflowCount > 0" class="text-[9px] text-neutral-400 ml-0.5">+{{ overflowCount }}</span>
                </div>
              </template>
              <template #content>
                <div class="flex flex-col gap-1.5">
                  <div v-for="badge in badges" :key="badge" class="flex items-center gap-2">
                    <span :class="['w-2 h-2 rounded-full', badgeConfig[badge].dot]" />
                    <span class="text-xs text-neutral-300">{{ badgeConfig[badge].label }}</span>
                  </div>
                </div>
              </template>
            </UTooltip>

            <UTooltip
              v-if="openTasks > 0"
              :content="{ side: 'left' }"
              :text="`${openTasks} open task ${openTasks !== 1 ? 's' : ''}`"
              :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
            >
              <UBadge color="neutral" variant="subtle" icon="i-lucide-list-todo">
                {{ openTasks }}
              </UBadge>
            </UTooltip>
          </div>
        </div>

        <UTooltip
          v-if="hasAnnotations"
          :content="{ side: 'right' }"
          :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
        >
          <template #default>
            <div
              :class="[
                'absolute bottom-12 left-2 flex items-center gap-1 px-1.5 py-1 rounded backdrop-blur-sm',
                'bg-neutral-900/90 border',
                hasUnsavedChanges ? 'border-amber-500/40' : 'border-neutral-700/50'
              ]"
            >
              <Icon
                name="i-lucide-pen-line"
                :class="['w-3 h-3', hasUnsavedChanges ? 'text-amber-400' : 'text-cyan-400']"
              />
              <span v-if="hasUnsavedChanges" class="relative flex h-2 w-2">
                <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75" />
                <span class="relative inline-flex rounded-full h-2 w-2 bg-amber-400" />
              </span>
            </div>
          </template>
          <template #content>
            <span class="text-xs text-neutral-300">
              {{ hasUnsavedChanges ? 'Annotations (unsaved changes)' : 'Annotations available' }}
            </span>
          </template>
        </UTooltip>

        <UTooltip
          v-else-if="hasUnsavedChanges"
          :content="{ side: 'right' }"
          :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
        >
          <template #default>
            <div class="absolute bottom-12 left-2 flex items-center gap-1 px-1.5 py-1 rounded backdrop-blur-sm bg-neutral-900/90 border border-amber-500/40">
              <Icon name="i-lucide-pen-line" class="w-3 h-3 text-amber-400" />
              <span class="relative flex h-2 w-2">
                <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75" />
                <span class="relative inline-flex rounded-full h-2 w-2 bg-amber-400" />
              </span>
            </div>
          </template>
          <template #content>
            <span class="text-xs text-neutral-300">Unsaved new annotations</span>
          </template>
        </UTooltip>

        <div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-neutral-950 via-neutral-950/90 to-transparent pt-6 pb-2 px-2">
          <div v-if="variantItems.length > 0" @click.stop @pointerdown.stop>
            <USelect
              :model-value="selectedVariant ?? undefined"
              :items="variantItems"
              placeholder="Select variant"
              size="xs"
              :disabled="variantItems.length <= 1"
              :ui="selectUi"
              @update:model-value="handleVariantChange"
            />
          </div>
          <div v-else class="text-[11px] text-neutral-500 px-2 py-1">
            No variants
          </div>
        </div>

        <div
          v-if="isSelected"
          class="absolute bottom-12 right-2 w-5 h-5 rounded-full bg-primary-500 flex items-center justify-center"
        >
          <Icon name="i-lucide-check" class="w-3 h-3 text-white" />
        </div>
      </div>
    </div>
  </UContextMenu>

  <UPopover
    v-else
    v-model:open="popoverOpen"
    :content="{ side: 'right', align: 'start', sideOffset: 8 }"
    :ui="{ content: 'p-0 bg-transparent border-none shadow-none' }"
  >
    <UContextMenu :items="contextMenuItems">
      <button
        type="button"
        :class="[
          'w-full rounded-sm overflow-hidden border transition-colors relative',
          isSelected
            ? 'border-primary-500/60 ring-1 ring-primary-500/30'
            : 'border-neutral-800 hover:border-neutral-700'
        ]"
        style="height: 48px"
        @click="handleSelectPage"
        @mouseenter="onMouseEnter"
        @mouseleave="onMouseLeave"
      >
        <span
          v-if="showIndexingIndicator"
          :class="[
            'absolute top-1 right-1 z-10 h-2.5 w-2.5 rounded-full ring-1 ring-neutral-950/90',
            indexingIndicatorClasses
          ]"
          :aria-label="indexingIndicatorLabel"
        />
        <img
          v-if="previewUrl"
          :src="previewUrl"
          :alt="`Page ${pageLabel}`"
          class="h-full w-full object-cover"
          loading="lazy"
        >

        <div v-else class="h-full w-full bg-neutral-900" />
      </button>
    </UContextMenu>

    <template #content>
      <div
        class="p-2"
        style="width: 220px"
        @mouseenter="onMouseEnter"
        @mouseleave="onMouseLeave"
      >
        <UContextMenu :items="contextMenuItems">
          <div
            :class="[
              'group relative rounded-sm overflow-hidden cursor-pointer transition-all duration-200',
              'bg-neutral-900 border',
              isSelected
                ? 'border-primary-500 ring-2 ring-primary-500/20'
                : 'border-neutral-800 hover:border-neutral-700'
            ]"
            @click="handleSelectPage"
          >
            <div class="relative aspect-[3/4] bg-neutral-950">
              <img
                v-if="previewUrl"
                :src="previewUrl"
                :alt="`Page ${pageLabel}`"
                class="absolute inset-0 w-full h-full object-cover"
              >

              <div
                v-else
                class="absolute inset-0 flex items-center justify-center bg-linear-to-br from-neutral-900 to-neutral-950 text-neutral-400"
              >
                <Icon name="i-lucide-file-text" class="h-5 w-5" />
              </div>

              <div class="absolute inset-x-0 top-0 p-2 flex items-start justify-between gap-2">
                <span class="inline-flex px-1.5 py-0.5 text-sm font-mono font-medium bg-neutral-900/90 text-neutral-300 rounded border border-neutral-700/50 backdrop-blur-sm">
                  {{ pageLabel }}
                </span>

                <div class="flex items-center gap-1">
                  <UPopover
                    v-if="displayTags.length > 0"
                    mode="hover"
                    :open-delay="150"
                    :close-delay="100"
                    :content="{ side: 'right', align: 'end', sideOffset: 6 }"
                    :ui="{ content: `${tooltipUi.content} p-2` }"
                  >
                    <template #default>
                      <div class="flex items-center gap-1 px-1.5 py-1 bg-neutral-900/90 rounded border border-neutral-700/50 backdrop-blur-sm">
                        <span
                          v-for="tag in visibleTagDots"
                          :key="tag.label"
                          class="w-2 h-2 rounded-full"
                          :style="{ backgroundColor: tag.color }"
                        />
                        <span v-if="hiddenTagDotCount > 0" class="text-[9px] text-neutral-400 ml-0.5">+{{ hiddenTagDotCount }}</span>
                      </div>
                    </template>
                    <template #content>
                      <div class="flex flex-col gap-1.5">
                        <UiColorTag
                          v-for="tag in displayTags"
                          :key="tag.label"
                          :color="tag.color"
                          variant="subtle"
                          size="sm"
                          dot
                        >
                          {{ tag.label }}
                        </UiColorTag>
                      </div>
                    </template>
                  </UPopover>

                  <UTooltip
                    v-if="badges.length > 0"
                    :delay-duration="200"
                    :ui="{ ...tooltipUi, content: `${tooltipUi.content} p-2` }"
                    :portal="false"
                  >
                    <template #default>
                      <div class="flex items-center gap-1 px-1.5 py-1 bg-neutral-900/90 rounded border border-neutral-700/50 backdrop-blur-sm">
                        <span
                          v-for="badge in visibleBadges"
                          :key="badge"
                          :class="['w-2 h-2 rounded-full', badgeConfig[badge].dot]"
                        />
                        <span v-if="overflowCount > 0" class="text-[9px] text-neutral-400 ml-0.5">+{{ overflowCount }}</span>
                      </div>
                    </template>
                    <template #content>
                      <div class="flex flex-col gap-1.5">
                        <div v-for="badge in badges" :key="badge" class="flex items-center gap-2">
                          <span :class="['w-2 h-2 rounded-full', badgeConfig[badge].dot]" />
                          <span class="text-xs text-neutral-300">{{ badgeConfig[badge].label }}</span>
                        </div>
                      </div>
                    </template>
                  </UTooltip>

                  <UTooltip
                    v-if="openTasks > 0"
                    :content="{ side: 'left' }"
                    :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
                    :portal="false"
                  >
                    <template #default>
                      <div class="flex items-center gap-1 px-1.5 py-0.5 bg-violet-500/20 border border-violet-500/30 rounded backdrop-blur-sm">
                        <Icon name="i-lucide-list-todo" class="w-3 h-3 text-violet-400" />
                        <span class="text-[10px] font-medium text-violet-400">{{ openTasks }}</span>
                      </div>
                    </template>
                    <template #content>
                      <span class="text-xs text-neutral-300">
                        {{ openTasks }} open task{{ openTasks !== 1 ? 's' : '' }}
                      </span>
                    </template>
                  </UTooltip>
                </div>
              </div>

              <UTooltip
                v-if="hasAnnotations"
                :content="{ side: 'right' }"
                :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
                :portal="false"
              >
                <template #default>
                  <div
                    :class="[
                      'absolute bottom-12 left-2 flex items-center gap-1 px-1.5 py-1 rounded backdrop-blur-sm',
                      'bg-neutral-900/90 border',
                      hasUnsavedChanges ? 'border-amber-500/40' : 'border-neutral-700/50'
                    ]"
                  >
                    <Icon
                      name="i-lucide-pen-line"
                      :class="['w-3 h-3', hasUnsavedChanges ? 'text-amber-400' : 'text-cyan-400']"
                    />
                    <span v-if="hasUnsavedChanges" class="relative flex h-2 w-2">
                      <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75" />
                      <span class="relative inline-flex rounded-full h-2 w-2 bg-amber-400" />
                    </span>
                  </div>
                </template>
                <template #content>
                  <span class="text-xs text-neutral-300">
                    {{ hasUnsavedChanges ? 'Annotations (unsaved changes)' : 'Annotations available' }}
                  </span>
                </template>
              </UTooltip>

              <UTooltip
                v-else-if="hasUnsavedChanges"
                :content="{ side: 'right' }"
                :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
                :portal="false"
              >
                <template #default>
                  <div class="absolute bottom-12 left-2 flex items-center gap-1 px-1.5 py-1 rounded backdrop-blur-sm bg-neutral-900/90 border border-amber-500/40">
                    <Icon name="i-lucide-pen-line" class="w-3 h-3 text-amber-400" />
                    <span class="relative flex h-2 w-2">
                      <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75" />
                      <span class="relative inline-flex rounded-full h-2 w-2 bg-amber-400" />
                    </span>
                  </div>
                </template>
                <template #content>
                  <span class="text-xs text-neutral-300">Unsaved new annotations</span>
                </template>
              </UTooltip>

              <div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-neutral-950 via-neutral-950/90 to-transparent pt-6 pb-2 px-2">
                <div v-if="variantItems.length > 0" @click.stop @pointerdown.stop>
                  <USelect
                    :model-value="selectedVariant ?? undefined"
                    :items="variantItems"
                    placeholder="Select variant"
                    size="xs"
                    :disabled="variantItems.length <= 1"
                    :ui="selectUi"
                    @update:model-value="handleVariantChange"
                    @update:open="onSelectOpenChange"
                  />
                </div>
                <div v-else class="text-[11px] text-neutral-500 px-2 py-1">
                  No variants
                </div>
              </div>

              <div
                v-if="isSelected"
                class="absolute bottom-12 right-2 w-5 h-5 rounded-full bg-primary-500 flex items-center justify-center"
              >
                <Icon name="i-lucide-check" class="w-3 h-3 text-white" />
              </div>
            </div>
          </div>
        </UContextMenu>
      </div>
    </template>
  </UPopover>
</template>
