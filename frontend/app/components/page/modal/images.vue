<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { LazyUiDeleteSlideover } from '#components'

defineOptions({
  inheritAttrs: false
})

interface PageBasic {
  id: string
  name: string
  locked?: boolean
  lockedReason?: string | null
}

interface Props {
  projectId: string
  pages: PageBasic[]
  initialPageIndex: number
  canDelete?: boolean
  onChanged?: () => void | Promise<void>
}

type PageImage = {
  id: string
  fileName: string
  filePath: string
  mimeType: string
  fileSize: number
  variant: string
  baseName: string
  created: string
}

const props = defineProps<Props>()
const emit = defineEmits<{ close: [boolean] }>()
const attrs = useAttrs()

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const backgroundDownloads = useBackgroundDownloads()
const isLoading = ref(true)
const isDeleting = ref(false)
const hasChanges = ref(false)
const pageImages = ref<PageImage[]>([])
const imageStates = ref<Record<string, 'loading' | 'loaded' | 'error'>>({})
const isSelectionMode = ref(false)
const selectedImageIds = ref<Set<string>>(new Set())

type TabValue = 'overview' | 'compare'
const activeTab = ref<TabValue>('overview')
const tabItems = computed(() => [
  {
    label: 'Overview',
    icon: 'i-lucide-layout-grid',
    value: 'overview' as const,
    badge: pageImages.value.length
  },
  {
    label: 'Compare',
    icon: 'i-lucide-columns-2',
    value: 'compare' as const
  }
])

const sortedPages = computed(() =>
  [...props.pages].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }))
)

const initialPage = props.pages[props.initialPageIndex]
const currentPageId = ref(initialPage?.id ?? props.pages[0]?.id)

const currentPageIndex = computed(() =>
  sortedPages.value.findIndex(p => p.id === currentPageId.value)
)
const currentPage = computed(() =>
  sortedPages.value.find(p => p.id === currentPageId.value)
)
const hasMultiplePages = computed(() => sortedPages.value.length > 1)
const canDeleteCurrentPageImages = computed(() => Boolean(props.canDelete && !currentPage.value?.locked))
const selectedImages = computed(() => pageImages.value.filter(image => selectedImageIds.value.has(image.id)))

const pageOptions = computed(() =>
  sortedPages.value.map(p => ({
    label: p.name,
    value: p.id
  }))
)

function goToNextPage() {
  if (sortedPages.value.length === 0) return
  const nextIndex = (currentPageIndex.value + 1) % sortedPages.value.length
  currentPageId.value = sortedPages.value[nextIndex]!.id
  fetchPageImages()
}

function goToPreviousPage() {
  if (sortedPages.value.length === 0) return
  const prevIndex = (currentPageIndex.value - 1 + sortedPages.value.length) % sortedPages.value.length
  currentPageId.value = sortedPages.value[prevIndex]!.id
  fetchPageImages()
}

function goToPage(pageId: string) {
  if (pageId !== currentPageId.value) {
    currentPageId.value = pageId
    fetchPageImages()
  }
}

function closeSlideover() {
  if (!lightboxOpen.value) emit('close', hasChanges.value)
}

function toggleImageSelection(imageId: string) {
  const next = new Set(selectedImageIds.value)
  if (next.has(imageId)) next.delete(imageId)
  else next.add(imageId)
  selectedImageIds.value = next
}

function selectAllImages() {
  selectedImageIds.value = selectedImageIds.value.size === pageImages.value.length
    ? new Set()
    : new Set(pageImages.value.map(image => image.id))
}

function exitSelectionMode() {
  isSelectionMode.value = false
  selectedImageIds.value = new Set()
}

function enterSelectionMode() {
  isSelectionMode.value = true
}

const lightboxOpen = ref(false)
const lightboxImageIndex = ref(0)
const lightboxScale = ref(1)
const lightboxPosition = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })

const lightboxImage = computed(() => pageImages.value[lightboxImageIndex.value])

function openLightbox(image: PageImage) {
  const index = pageImages.value.findIndex(img => img.id === image.id)
  lightboxImageIndex.value = index >= 0 ? index : 0
  lightboxScale.value = 1
  lightboxPosition.value = { x: 0, y: 0 }
  lightboxOpen.value = true
}

function closeLightbox() {
  lightboxOpen.value = false
}

function lightboxNext() {
  if (pageImages.value.length === 0) return
  lightboxImageIndex.value = (lightboxImageIndex.value + 1) % pageImages.value.length
  resetLightboxView()
}

function lightboxPrev() {
  if (pageImages.value.length === 0) return
  lightboxImageIndex.value = (lightboxImageIndex.value - 1 + pageImages.value.length) % pageImages.value.length
  resetLightboxView()
}

function resetLightboxView() {
  lightboxScale.value = 1
  lightboxPosition.value = { x: 0, y: 0 }
}

function zoomIn() {
  lightboxScale.value = Math.min(lightboxScale.value * 1.25, 5)
}

function zoomOut() {
  lightboxScale.value = Math.max(lightboxScale.value / 1.25, 0.5)
}

function handleLightboxWheel(e: WheelEvent) {
  e.preventDefault()
  if (e.deltaY < 0) {
    zoomIn()
  } else {
    zoomOut()
  }
}

function handleLightboxMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  isDragging.value = true
  dragStart.value = { x: e.clientX - lightboxPosition.value.x, y: e.clientY - lightboxPosition.value.y }
}

function handleLightboxMouseMove(e: MouseEvent) {
  if (!isDragging.value) return
  lightboxPosition.value = {
    x: e.clientX - dragStart.value.x,
    y: e.clientY - dragStart.value.y
  }
}

function handleLightboxMouseUp() {
  isDragging.value = false
}

const leftImageId = ref<string | undefined>(undefined)
const rightImageId = ref<string | undefined>(undefined)

const getImageSrc = (imageId: string) =>
  `/api/media/images/${imageId}?projectId=${props.projectId}`

const flatImageList = computed(() =>
  pageImages.value.map(img => ({
    label: `${img.variant || 'Original'} (${img.baseName})`,
    value: img.id
  }))
)

const leftImageSrc = computed(() =>
  leftImageId.value ? getImageSrc(leftImageId.value) : ''
)

const rightImageSrc = computed(() =>
  rightImageId.value ? getImageSrc(rightImageId.value) : ''
)

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

function getImageCardItems(image: PageImage): DropdownMenuItem[] {
  const items: DropdownMenuItem[] = [
    {
      label: 'Download',
      icon: 'i-lucide-download',
      onSelect: () => downloadImage(image)
    }
  ]

  if (canDeleteCurrentPageImages.value && !isSelectionMode.value) {
    items.push({
      label: 'Delete',
      icon: 'i-lucide-trash-2',
      color: 'error',
      onSelect: () => deleteImages([image])
    })
  }

  return items
}

async function downloadImage(image: PageImage) {
  try {
    await backgroundDownloads.runBackgroundJob({
      title: 'Downloading page image',
      subtitle: image.fileName,
      statusLabel: 'Preparing',
      completedLabel: 'Downloaded',
      icon: 'i-lucide-image-down',
      task: async (job) => {
        const response = await fetch(`/api/projects/${props.projectId}/pages/images/${image.id}/export`)
        if (!response.ok) {
          throw new Error(`Download failed (${response.status})`)
        }
        await backgroundDownloads.downloadBlobResponse(response, image.fileName, job)
      }
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to download image'
    toast.add({ title: 'Download failed', description: message, color: 'error' })
  }
}

async function deleteImages(images: PageImage[]) {
  const page = currentPage.value
  if (!page || !canDeleteCurrentPageImages.value || images.length === 0 || isDeleting.value) return

  const count = images.length
  const instance = deleteSlideover.open({
    name: `${count} image${count === 1 ? '' : 's'} from “${page.name}”`,
    entityType: 'Image',
    title: count === 1 ? 'Delete Image' : 'Delete Images',
    warningMessage: `Delete ${count === 1 ? 'this image' : `these ${count} images`} from “${page.name}”?`,
    warningDetails: ['Original files and generated thumbnails will be permanently removed.'],
    items: images.map(image => ({ id: image.id, label: image.fileName })),
    confirmButtonLabel: `Delete ${count} Image${count === 1 ? '' : 's'}`
  })
  const confirmed = await instance.result
  if (!confirmed) return

  isDeleting.value = true
  try {
    const response = await $fetch<{ deletedCount: number, requestedCount: number }>(
      `/api/projects/${props.projectId}/pages/${page.id}/images/batch`,
      { method: 'DELETE', body: images.map(image => image.id) }
    )

    if (response.deletedCount === 0) {
      throw new Error('No images could be deleted. The page may be locked or no longer available.')
    }

    if (lightboxImage.value && images.some(image => image.id === lightboxImage.value?.id)) {
      closeLightbox()
    }
    hasChanges.value = true
    exitSelectionMode()
    await fetchPageImages()
    await props.onChanged?.()
    toast.add({
      title: `${response.deletedCount} image${response.deletedCount === 1 ? '' : 's'} deleted`,
      description: `Removed from “${page.name}”.`,
      color: 'success',
      icon: 'i-lucide-trash-2'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to delete images'
    toast.add({ title: 'Delete failed', description: message, color: 'error', icon: 'i-lucide-alert-circle' })
  } finally {
    isDeleting.value = false
  }
}

async function fetchPageImages() {
  const page = currentPage.value
  if (!page) return

  isLoading.value = true
  exitSelectionMode()

  try {
    const response = await $fetch<PageImage[]>(
      `/api/projects/${props.projectId}/pages/${page.id}/images`
    )
    pageImages.value = response
    imageStates.value = {}
    response.forEach((img) => {
      imageStates.value[img.id] = 'loading'
    })

    if (response.length >= 2) {
      leftImageId.value = response[0]?.id
      rightImageId.value = response[1]?.id
    } else if (response.length === 1) {
      leftImageId.value = response[0]?.id
      rightImageId.value = response[0]?.id
    } else {
      leftImageId.value = undefined
      rightImageId.value = undefined
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'An error occurred'
    toast.add({
      title: 'Failed to load images',
      description: message,
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
    pageImages.value = []
  } finally {
    isLoading.value = false
  }
}

function handleKeyDown(e: KeyboardEvent) {
  if (lightboxOpen.value) {
    if (e.key === 'Escape') {
      e.preventDefault()
      closeLightbox()
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault()
      lightboxPrev()
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      lightboxNext()
    } else if (e.key === '+' || e.key === '=') {
      e.preventDefault()
      zoomIn()
    } else if (e.key === '-') {
      e.preventDefault()
      zoomOut()
    } else if (e.key === '0') {
      e.preventDefault()
      resetLightboxView()
    }
    return
  }

  const target = e.target as HTMLElement
  if (['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName)) return

  if (e.key === 'ArrowLeft' && hasMultiplePages.value) {
    e.preventDefault()
    goToPreviousPage()
  } else if (e.key === 'ArrowRight' && hasMultiplePages.value) {
    e.preventDefault()
    goToNextPage()
  }
}

onMounted(() => {
  fetchPageImages()
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <UiResponsiveSlideover
    v-bind="attrs"
    side="right"
    :dismissible="!lightboxOpen"
    :close="{ onClick: closeSlideover }"
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-[92vw] flex flex-col' }"
  >
    <template #header>
      <div class="flex items-center justify-between w-full gap-4">
        <div class="flex items-center gap-2 min-w-0 flex-1">
          <UIcon name="i-lucide-images" class="size-5 shrink-0 text-muted" />
          <UButton
            v-if="hasMultiplePages"
            icon="i-lucide-chevron-left"
            color="neutral"
            variant="ghost"
            size="sm"
            @click.stop="goToPreviousPage"
          />
          <div class="flex items-center gap-2 min-w-0 flex-1">
            <span class="text-lg font-semibold shrink-0">Images for</span>
            <USelectMenu
              v-if="hasMultiplePages"
              :model-value="currentPageId"
              :items="pageOptions"
              value-key="value"
              searchable
              searchable-placeholder="Search pages..."
              class="w-48"
              @update:model-value="goToPage"
            />
            <span v-else class="text-lg font-semibold truncate">{{ currentPage?.name }}</span>
          </div>
          <UButton
            v-if="hasMultiplePages"
            icon="i-lucide-chevron-right"
            color="neutral"
            variant="ghost"
            size="sm"
            @click.stop="goToNextPage"
          />
        </div>
        <span v-if="hasMultiplePages" class="text-sm text-muted shrink-0">
          {{ currentPageIndex + 1 }} / {{ sortedPages.length }}
        </span>
      </div>
    </template>

    <template #body>
      <div class="flex flex-col h-[calc(100vh-130px)] overflow-hidden">
        <div class="shrink-0 px-4 pt-1">
          <div class="flex items-center justify-between gap-3">
            <UTabs
              v-model="activeTab"
              :items="tabItems"
              :content="false"
              variant="link"
              color="neutral"
              size="sm"
              :ui="{
                list: 'gap-1 p-0',
                trigger: 'flex-none px-3 py-2.5',
                trailingBadge: 'min-w-5 justify-center tabular-nums'
              }"
            />
            <div v-if="activeTab === 'overview' && canDeleteCurrentPageImages && pageImages.length" class="flex items-center gap-2">
              <template v-if="isSelectionMode">
                <span class="text-xs text-muted">{{ selectedImageIds.size }} selected</span>
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  @click="selectAllImages"
                >
                  {{ selectedImageIds.size === pageImages.length ? 'Clear all' : 'Select all' }}
                </UButton>
                <UButton
                  icon="i-lucide-trash-2"
                  color="error"
                  variant="soft"
                  size="sm"
                  :disabled="selectedImageIds.size === 0"
                  :loading="isDeleting"
                  @click="deleteImages(selectedImages)"
                >
                  Delete
                </UButton>
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  @click="exitSelectionMode"
                >
                  Cancel
                </UButton>
              </template>
              <UButton
                v-else
                icon="i-lucide-list-checks"
                color="neutral"
                variant="ghost"
                size="sm"
                @click="enterSelectionMode"
              >
                Select images
              </UButton>
            </div>
          </div>
        </div>

        <div v-if="isLoading" class="flex-1 overflow-auto p-4">
          <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4 sm:gap-5">
            <div v-for="i in 5" :key="i" class="rounded-2xl bg-elevated p-2.5 shadow-sm">
              <USkeleton class="aspect-[4/5] w-full rounded-xl" />
              <div class="px-1.5 pt-3 pb-1.5 space-y-2">
                <div class="flex items-center justify-between gap-3">
                  <USkeleton class="h-4 w-20" />
                  <USkeleton class="h-5 w-12 rounded-full" />
                </div>
                <USkeleton class="h-3 w-3/4" />
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'overview' && pageImages.length > 0" class="flex-1 overflow-auto p-4 sm:p-5">
          <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4 sm:gap-5">
            <article
              v-for="image in pageImages"
              :key="image.id"
              class="group relative min-w-0 cursor-pointer rounded-2xl bg-elevated p-2.5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-lg"
            >
              <button
                v-if="isSelectionMode"
                type="button"
                class="absolute left-4 top-4 z-30 flex size-7 items-center justify-center rounded-full border bg-default shadow-sm"
                :class="selectedImageIds.has(image.id) ? 'border-primary text-primary' : 'border-default text-muted'"
                :aria-label="selectedImageIds.has(image.id) ? `Deselect ${image.fileName}` : `Select ${image.fileName}`"
                @click.stop="toggleImageSelection(image.id)"
              >
                <UIcon :name="selectedImageIds.has(image.id) ? 'i-lucide-circle-check' : 'i-lucide-circle'" class="size-4" />
              </button>
              <button
                type="button"
                :aria-label="isSelectionMode
                  ? (selectedImageIds.has(image.id) ? `Deselect ${image.fileName}` : `Select ${image.fileName}`)
                  : `Open ${image.variant || 'Original'} image`"
                class="absolute inset-0 z-10 rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-primary"
                @click="isSelectionMode ? toggleImageSelection(image.id) : openLightbox(image)"
              />
              <div class="relative aspect-[4/5] overflow-hidden rounded-xl bg-default">
                <div
                  v-if="imageStates[image.id] === 'loading'"
                  class="absolute inset-0 flex items-center justify-center text-muted"
                >
                  <UIcon name="i-lucide-loader" class="size-5 animate-spin" />
                </div>
                <div
                  v-if="imageStates[image.id] === 'error'"
                  class="absolute inset-0 flex flex-col items-center justify-center text-muted"
                >
                  <UIcon name="i-lucide-image-off" class="size-7 mb-2" />
                  <span class="text-xs">Failed to load</span>
                </div>
                <img
                  v-show="imageStates[image.id] !== 'error'"
                  :src="getImageSrc(image.id)"
                  :alt="image.fileName"
                  class="size-full object-contain p-2 transition-transform duration-300 group-hover:scale-[1.02]"
                  loading="lazy"
                  @load="imageStates[image.id] = 'loaded'"
                  @error="imageStates[image.id] = 'error'"
                >
              </div>

              <div class="min-w-0 px-1.5 pt-3 pb-1.5">
                <div class="flex items-center justify-between gap-3">
                  <h5 class="truncate text-sm font-semibold text-highlighted">
                    {{ image.variant || 'Original' }}
                  </h5>
                  <UBadge
                    size="xs"
                    color="neutral"
                    variant="soft"
                    class="shrink-0 rounded-full tabular-nums"
                  >
                    {{ formatFileSize(image.fileSize) }}
                  </UBadge>
                </div>
                <div class="mt-1.5 flex items-center justify-between gap-3">
                  <p class="min-w-0 truncate text-xs text-muted">
                    {{ image.fileName }}
                  </p>
                  <div class="relative z-20 shrink-0">
                    <UDropdownMenu
                      :items="getImageCardItems(image)"
                      :content="{ align: 'end' }"
                    >
                      <UButton
                        icon="i-lucide-ellipsis-vertical"
                        size="sm"
                        color="neutral"
                        variant="ghost"
                        aria-label="Open image actions"
                      />
                    </UDropdownMenu>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </div>

        <div v-else-if="activeTab === 'compare' && pageImages.length >= 2" class="flex-1 flex flex-col min-h-0 p-4 gap-4">
          <div class="flex gap-4 items-center shrink-0">
            <div class="flex-1">
              <label class="block text-sm font-medium mb-2">Left Image</label>
              <USelectMenu v-model="leftImageId" :items="flatImageList" value-key="value" />
            </div>
            <div class="flex-1">
              <label class="block text-sm font-medium mb-2">Right Image</label>
              <USelectMenu v-model="rightImageId" :items="flatImageList" value-key="value" />
            </div>
          </div>
          <div v-if="leftImageSrc && rightImageSrc" class="relative flex-1 min-h-0">
            <UiImageCompare
              :left-image="leftImageSrc"
              :right-image="rightImageSrc"
              :left-label="flatImageList.find(i => i.value === leftImageId)?.label"
              :right-label="flatImageList.find(i => i.value === rightImageId)?.label"
              class="absolute inset-0"
            />
          </div>
        </div>

        <div v-else-if="activeTab === 'compare' && pageImages.length < 2" class="flex-1 flex items-center justify-center">
          <div class="text-center">
            <UIcon name="i-lucide-images" class="mx-auto text-4xl text-neutral-400 mb-4" />
            <p class="text-neutral-600 dark:text-neutral-400">
              At least 2 images are required for comparison.
            </p>
          </div>
        </div>

        <div v-else-if="!isLoading && pageImages.length === 0" class="flex-1 flex items-center justify-center">
          <div class="text-center">
            <UIcon name="i-lucide-image-off" class="mx-auto text-4xl text-neutral-400 mb-4" />
            <p class="text-neutral-600 dark:text-neutral-400">
              No images found for this page.
            </p>
          </div>
        </div>

        <Transition name="lightbox">
          <div
            v-if="lightboxOpen && lightboxImage"
            class="absolute inset-0 z-50 flex items-center justify-center bg-black/95"
            @click.self="closeLightbox"
            @wheel.prevent="handleLightboxWheel"
          >
            <div class="absolute top-4 left-4 right-4 flex items-center justify-between z-10 pointer-events-none">
              <div class="flex items-center gap-2 bg-black/50 backdrop-blur rounded-sm px-3 py-2 pointer-events-auto">
                <span class="text-white text-sm font-medium">
                  {{ lightboxImage.variant || 'Original' }} - {{ lightboxImage.baseName }}
                </span>
                <UBadge color="neutral" variant="soft" size="xs">
                  {{ lightboxImageIndex + 1 }} / {{ pageImages.length }}
                </UBadge>
              </div>
              <div class="flex items-center gap-1 pointer-events-auto">
                <button
                  v-if="canDeleteCurrentPageImages"
                  type="button"
                  class="p-2 rounded-sm text-error hover:bg-white/20 transition-colors"
                  title="Delete image"
                  @click.stop="deleteImages([lightboxImage])"
                >
                  <UIcon name="i-lucide-trash-2" class="w-5 h-5" />
                </button>
                <button
                  type="button"
                  class="p-2 rounded-sm text-white hover:bg-white/20 transition-colors"
                  title="Zoom out (-)"
                  @click.stop="zoomOut"
                >
                  <UIcon name="i-lucide-zoom-out" class="w-5 h-5" />
                </button>
                <button
                  type="button"
                  class="p-2 rounded-sm text-white hover:bg-white/20 transition-colors"
                  title="Zoom in (+)"
                  @click.stop="zoomIn"
                >
                  <UIcon name="i-lucide-zoom-in" class="w-5 h-5" />
                </button>
                <button
                  type="button"
                  class="p-2 rounded-sm text-white hover:bg-white/20 transition-colors"
                  title="Reset view (0)"
                  @click.stop="resetLightboxView"
                >
                  <UIcon name="i-lucide-maximize-2" class="w-5 h-5" />
                </button>
                <button
                  type="button"
                  class="p-2 rounded-sm text-white hover:bg-white/20 transition-colors"
                  title="Download"
                  @click.stop="downloadImage(lightboxImage)"
                >
                  <UIcon name="i-lucide-download" class="w-5 h-5" />
                </button>
                <button
                  type="button"
                  class="p-2 rounded-sm text-white hover:bg-white/20 transition-colors"
                  title="Close (Esc)"
                  @click.stop="closeLightbox"
                >
                  <UIcon name="i-lucide-x" class="w-5 h-5" />
                </button>
              </div>
            </div>

            <button
              v-if="pageImages.length > 1"
              type="button"
              class="absolute left-4 top-1/2 -translate-y-1/2 p-3 rounded-sm text-white hover:bg-white/20 transition-colors z-10"
              title="Previous image"
              @click.stop="lightboxPrev"
            >
              <UIcon name="i-lucide-chevron-left" class="w-8 h-8" />
            </button>
            <button
              v-if="pageImages.length > 1"
              type="button"
              class="absolute right-4 top-1/2 -translate-y-1/2 p-3 rounded-sm text-white hover:bg-white/20 transition-colors z-10"
              title="Next image"
              @click.stop="lightboxNext"
            >
              <UIcon name="i-lucide-chevron-right" class="w-8 h-8" />
            </button>

            <div
              class="absolute inset-0 flex items-center justify-center overflow-hidden"
              :class="{ 'cursor-grab': !isDragging, 'cursor-grabbing': isDragging }"
              @mousedown.stop="handleLightboxMouseDown"
              @mousemove="handleLightboxMouseMove"
              @mouseup="handleLightboxMouseUp"
              @mouseleave="handleLightboxMouseUp"
            >
              <img
                :src="getImageSrc(lightboxImage.id)"
                :alt="lightboxImage.fileName"
                class="select-none"
                :style="{
                  maxWidth: lightboxScale <= 1 ? '90vw' : 'none',
                  maxHeight: lightboxScale <= 1 ? '85vh' : 'none',
                  objectFit: 'contain',
                  transform: `scale(${lightboxScale}) translate(${lightboxPosition.x / lightboxScale}px, ${lightboxPosition.y / lightboxScale}px)`,
                  transformOrigin: 'center center'
                }"
                draggable="false"
              >
            </div>

            <div class="absolute bottom-4 left-1/2 -translate-x-1/2 bg-black/50 backdrop-blur rounded-sm px-3 py-2 pointer-events-none">
              <p class="text-white text-xs">
                {{ lightboxImage.fileName }} · {{ formatFileSize(lightboxImage.fileSize) }}
              </p>
            </div>
          </div>
        </Transition>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end">
        <UButton
          color="neutral"
          @click="closeSlideover"
        >
          Close
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>

<style scoped>
.lightbox-enter-active,
.lightbox-leave-active {
  transition: opacity 0.2s ease;
}

.lightbox-enter-from,
.lightbox-leave-to {
  opacity: 0;
}
</style>
