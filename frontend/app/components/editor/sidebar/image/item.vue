<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { PageData } from '@/stores/editor/types'
import { useEditorStore } from '@/stores/editor/editor.store'
import UiColorTag from '@/components/ui/color-tag.vue'
import type { PageWorkflowState } from '@/types/project-page'
import { resolvePageLockReason } from '@/utils/page-lock'

type VariantItem = { label: string, value: string }

const DEFAULT_CUSTOM_TAG_COLOR = '#2563eb'

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
const imageLoader = useEditorImageLoader()
const actionRunsStore = useActionRunsStore()
const pageSummaries = useCollaborationPageSummary()
const toast = useToast()
const router = useRouter()
const { user } = useUserSession()

const isSelected = computed(() => props.page.id === props.currentPageId)
const actionLockReason = computed(() => actionRunsStore.getPageActionLockReason(props.page.projectId, props.page.id))
const pageLockReason = computed(() => resolvePageLockReason(props.page, actionLockReason.value))
const isPageLocked = computed(() => pageLockReason.value !== null)

const pageLabel = computed(() => props.page.label || props.page.id)
const workflowStateBadge = computed(() => {
  const state: PageWorkflowState = props.page.workflowState ?? 'OPEN'
  return {
    OPEN: { label: 'Open', icon: 'i-lucide-circle', color: 'neutral' as const },
    IN_PROGRESS: { label: 'In progress', icon: 'i-lucide-loader-circle', color: 'info' as const },
    DONE: { label: 'Done', icon: 'i-lucide-circle-check', color: 'success' as const }
  }[state]
})
const openTasks = computed(() => props.openSubtaskCount ?? 0)
const hasAnnotations = computed(() => (props.page.xmlFiles?.length ?? 0) > 0 || (props.page.xmlFileCount ?? 0) > 0)
const hasUnsavedChanges = computed(() => editorStore.hasUnsavedChangesForPage(props.page.id))
const annotationSummary = computed(() => {
  if (hasUnsavedChanges.value) {
    return {
      label: 'Unsaved',
      icon: 'i-lucide-save-off',
      class: 'text-amber-700 dark:text-amber-300'
    }
  }

  if (hasAnnotations.value) {
    return {
      label: 'Annotated',
      icon: 'i-lucide-file-pen-line',
      class: 'text-emerald-700 dark:text-emerald-300'
    }
  }

  return {
    label: 'No annotation',
    icon: 'i-lucide-file-plus-2',
    class: 'text-neutral-500 dark:text-neutral-500'
  }
})
const indexingStatus = computed(() => props.page.indexingStatus ?? 'NOT_APPLICABLE')
const showIndexingIndicator = computed(() => indexingStatus.value === 'UNINDEXED' || indexingStatus.value === 'INDEXING')
const indexingIndicatorClasses = computed(() => {
  if (indexingStatus.value === 'INDEXING') {
    return 'bg-amber-400 shadow-[0_0_0_2px_rgba(251,191,36,0.2)] animate-pulse'
  }
  return 'bg-red-400 shadow-[0_0_0_2px_rgba(248,113,113,0.2)]'
})
const indexingIndicatorLabel = computed(() => indexingStatus.value === 'INDEXING' ? 'Indexing in background' : 'Not indexed yet')

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
const currentUserId = computed(() => {
  const value = user.value as { id?: string, sub?: string } | null | undefined
  return value?.id ?? value?.sub ?? null
})
const collaborationEditor = computed(() => {
  const editor = collaborationSummary.value?.editor ?? null
  if (!editor) return null
  return editor.user.id === currentUserId.value ? null : editor
})
const collaborationAvatarRingClass = computed(() => {
  const summary = collaborationSummary.value
  if (!summary?.editor || !collaborationEditor.value) return ''
  return summary.isLive ? 'ring-emerald-400/90' : 'ring-neutral-400/90'
})

const collaborationTooltip = computed(() => {
  const summary = collaborationSummary.value
  if (!summary) return ''

  if (collaborationEditor.value) {
    const activity = summary.isLive ? 'Live' : 'Idle'
    const viewers = summary.viewerCount > 0 ? `, ${summary.viewerCount} viewer${summary.viewerCount === 1 ? '' : 's'}` : ''
    const pending = summary.hasPendingTakeover ? ', pending request' : ''
    return `${collaborationEditor.value.user.displayName} editing (${activity}${viewers}${pending})`
  }

  return `${summary.viewerCount} viewer${summary.viewerCount === 1 ? '' : 's'} watching`
})

const previewImageLoaded = ref(false)
const previewImageFailed = ref(false)
const hasPreviewImage = computed(() => Boolean(props.previewUrl) && !previewImageFailed.value)
const previewImageSrc = computed(() => hasPreviewImage.value ? props.previewUrl ?? undefined : undefined)

watch(() => props.previewUrl, () => {
  previewImageLoaded.value = props.previewUrl ? imageLoader.isPreviewUrlLoaded(props.previewUrl) : false
  previewImageFailed.value = false
}, { immediate: true })

function handlePreviewImageLoad() {
  if (props.previewUrl) {
    imageLoader.markPreviewUrlLoaded(props.previewUrl)
  }
  previewImageLoaded.value = true
}

function handlePreviewImageError() {
  previewImageLoaded.value = false
  previewImageFailed.value = true
}

const annotationModeBadge = computed<{
  label: string
  icon: string
  color: 'info' | 'warning'
  tooltip: string
} | null>(() => {
  const mode = props.page.annotationContext?.mode
  if (mode === 'DATASET_LINK') {
    return {
      label: 'LINK',
      icon: 'i-lucide-link-2',
      color: 'info',
      tooltip: 'Linked dataset item: annotations are saved to the source project XML.'
    }
  }
  if (mode === 'DATASET_COPY') {
    return {
      label: 'COPY',
      icon: 'i-lucide-copy',
      color: 'warning',
      tooltip: 'Dataset copy (frozen source): annotations are saved to the dataset copy XML.'
    }
  }
  return null
})

watch(() => props.page.projectId, (value) => {
  if (value) {
    void pageSummaries.ensureProjectSummary(value)
  }
}, { immediate: true })

const tooltipUi = {
  content: 'bg-white border border-neutral-200 text-neutral-800 shadow-lg dark:bg-neutral-900 dark:border-neutral-700 dark:text-neutral-200'
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

function handleVariantChange(variantId: string | null) {
  if (variantId) emit('variant-change', variantId)
}

function handleSelectPage() {
  emit('select-page', props.page)
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
  <UContextMenu :items="contextMenuItems">
    <div
      :class="[
        'group relative overflow-hidden rounded-xl border cursor-pointer bg-white shadow-sm dark:bg-neutral-950',
        'transition-[border-color,box-shadow,transform] duration-200 ease-out',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50',
        isSelected
          ? 'border-primary-500 ring-2 ring-primary-500/20 shadow-lg shadow-primary-950/20'
          : 'border-neutral-200 hover:-translate-y-0.5 hover:border-neutral-300 hover:shadow-lg hover:shadow-neutral-300/30 dark:border-neutral-800 dark:hover:border-neutral-700 dark:hover:shadow-black/20'
      ]"
      role="button"
      tabindex="0"
      :aria-label="`Open page ${pageLabel}`"
      @click="handleSelectPage"
      @keydown.enter.prevent="handleSelectPage"
      @keydown.space.prevent="handleSelectPage"
    >
      <div class="relative m-2 mb-0 aspect-3/4 overflow-hidden rounded-lg border border-black/10 bg-neutral-100 shadow-inner dark:border-white/10 dark:bg-neutral-900">
        <USkeleton
          v-if="hasPreviewImage && !previewImageLoaded"
          class="absolute inset-0 h-full w-full rounded-none"
        />
        <img
          v-if="hasPreviewImage"
          :src="previewImageSrc"
          :alt="`Page ${pageLabel}`"
          class="absolute inset-0 h-full w-full object-cover transition-opacity duration-200"
          :class="previewImageLoaded ? 'opacity-100' : 'opacity-0'"
          loading="eager"
          decoding="async"
          @load="handlePreviewImageLoad"
          @error="handlePreviewImageError"
        >

        <div
          v-if="!hasPreviewImage"
          class="absolute inset-0 flex items-center justify-center bg-linear-to-br from-neutral-100 to-neutral-200 text-neutral-500 dark:from-neutral-900 dark:to-neutral-950 dark:text-neutral-400"
        >
          <Icon name="i-lucide-file-text" class="h-5 w-5" />
        </div>
      </div>

      <div class="p-2.5">
        <div class="flex min-w-0 items-center gap-2">
          <span class="min-w-0 flex-1 truncate text-[13px] font-semibold leading-5 text-neutral-900 dark:text-neutral-100">
            {{ pageLabel }}
          </span>

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
              <div class="flex shrink-0 items-center gap-1 rounded-md border border-neutral-200 bg-neutral-50 px-1.5 py-1 dark:border-neutral-800 dark:bg-neutral-900">
                <span
                  v-for="tag in visibleTagDots"
                  :key="tag.label"
                  class="size-1.5 rounded-full"
                  :style="{ backgroundColor: tag.color }"
                />
                <span v-if="hiddenTagDotCount > 0" class="ml-0.5 text-[9px] text-neutral-500 dark:text-neutral-400">+{{ hiddenTagDotCount }}</span>
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
        </div>

        <div class="mt-1 flex min-w-0 items-center gap-2 overflow-hidden text-[10px] text-neutral-600 dark:text-neutral-400">
          <span class="inline-flex shrink-0 items-center gap-1">
            <Icon :name="workflowStateBadge.icon" class="size-3" />
            {{ workflowStateBadge.label }}
          </span>
          <span class="h-3 w-px shrink-0 bg-neutral-200 dark:bg-neutral-800" />
          <span :class="['inline-flex min-w-0 items-center gap-1 truncate', annotationSummary.class]">
            <Icon :name="annotationSummary.icon" class="size-3 shrink-0" />
            <span class="truncate">{{ annotationSummary.label }}</span>
          </span>

          <UTooltip
            v-if="openTasks > 0"
            :content="{ side: 'right' }"
            :text="`${openTasks} open task${openTasks !== 1 ? 's' : ''}`"
            :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
          >
            <span class="ml-auto inline-flex shrink-0 items-center gap-1 text-neutral-700 dark:text-neutral-300">
              <Icon name="i-lucide-list-todo" class="size-3" />
              {{ openTasks }}
            </span>
          </UTooltip>

          <UTooltip
            v-if="annotationModeBadge"
            :text="annotationModeBadge.tooltip"
            :content="{ side: 'right' }"
            :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
          >
            <span class="inline-flex shrink-0 items-center gap-1 uppercase text-neutral-700 dark:text-neutral-300">
              <Icon :name="annotationModeBadge.icon" class="size-3" />
              {{ annotationModeBadge.label }}
            </span>
          </UTooltip>

          <div class="ml-auto flex shrink-0 items-center gap-1.5">
            <UTooltip
              v-if="showIndexingIndicator"
              :text="indexingIndicatorLabel"
              :content="{ side: 'right' }"
              :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
            >
              <span class="inline-flex size-4 items-center justify-center" :aria-label="indexingIndicatorLabel">
                <span :class="['size-1.5 rounded-full', indexingIndicatorClasses]" />
              </span>
            </UTooltip>

            <UTooltip
              v-if="isPageLocked"
              :text="pageLockReason ?? undefined"
              :content="{ side: 'right' }"
              :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
            >
              <Icon name="i-lucide-lock" class="size-3.5 text-amber-600 dark:text-amber-300" />
            </UTooltip>

            <UTooltip
              v-if="collaborationEditor"
              :text="collaborationTooltip"
              :content="{ side: 'right' }"
              :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
            >
              <AppAvatar
                :seed="collaborationEditor.user.id"
                :src="resolveManagedProfileAvatarSrc(collaborationEditor.user.avatar)"
                :alt="collaborationEditor.user.displayName"
                size="2xs"
                :class="['ring-2', collaborationAvatarRingClass]"
              />
            </UTooltip>

            <UTooltip
              v-else-if="collaborationSummary?.viewerCount"
              :text="collaborationTooltip"
              :content="{ side: 'right' }"
              :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
            >
              <Icon name="i-lucide-eye" class="size-3.5 text-sky-600 dark:text-sky-300" />
            </UTooltip>
          </div>
        </div>

        <div class="mt-2 flex items-center gap-1.5" @click.stop @pointerdown.stop>
          <USelect
            v-if="variantItems.length > 0"
            :model-value="selectedVariant ?? undefined"
            :items="variantItems"
            placeholder="Select variant"
            size="xs"
            :disabled="variantItems.length <= 1"
            class="min-w-0 flex-1"
            @update:model-value="handleVariantChange"
          />
          <div v-else class="flex h-7 min-w-0 flex-1 items-center rounded-md border border-dashed border-neutral-200 px-2 text-[10px] text-neutral-400 dark:border-neutral-800 dark:text-neutral-600">
            No image variants
          </div>

          <UDropdownMenu
            :items="contextMenuItems"
            :content="{ side: 'right', align: 'end', sideOffset: 6 }"
          >
            <UButton
              color="neutral"
              variant="outline"
              size="xs"
              square
              icon="i-lucide-ellipsis"
              aria-label="Page actions"
              @click.stop
            />
          </UDropdownMenu>
        </div>
      </div>
    </div>
  </UContextMenu>
</template>
