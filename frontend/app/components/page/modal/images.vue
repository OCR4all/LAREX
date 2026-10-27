<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { ImageType } from '@/types/image'
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
  imageType?: ImageType | null
  id: string
  fileName: string
  filePath: string
  mimeType: string
  fileSize: number
  variant: string
  baseName: string
  created: string
}

const imageTypeLabels: Record<ImageType, string> = {
  COLOR: 'Color',
  GRAYSCALE: 'Grayscale',
  BINARY: 'Binary',
  UNKNOWN: 'Unknown'
}

function imageTypeLabel(type: ImageType | null | undefined) {
  return imageTypeLabels[type ?? 'UNKNOWN']
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
const isDownloading = ref(false)
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
const lightboxOriginal = ref(false)
const lightboxImageState = ref<'loading' | 'loaded' | 'error'>('loading')
const lightboxLoadAttempt = ref(0)
const lightboxScale = ref(1)
const lightboxPosition = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const lightboxViewport = ref<HTMLElement | null>(null)
const lightboxImageElement = ref<HTMLImageElement | null>(null)

const lightboxImage = computed(() => pageImages.value[lightboxImageIndex.value])

function openLightbox(image: PageImage) {
  const index = pageImages.value.findIndex(img => img.id === image.id)
  lightboxImageIndex.value = index >= 0 ? index : 0
  resetLightboxImage()
  lightboxOpen.value = true
}

function closeLightbox() {
  isDragging.value = false
  lightboxOpen.value = false
}

function lightboxNext() {
  if (pageImages.value.length === 0) return
  lightboxImageIndex.value = (lightboxImageIndex.value + 1) % pageImages.value.length
  resetLightboxImage()
}

function lightboxPrev() {
  if (pageImages.value.length === 0) return
  lightboxImageIndex.value = (lightboxImageIndex.value - 1 + pageImages.value.length) % pageImages.value.length
  resetLightboxImage()
}

function resetLightboxImage() {
  lightboxOriginal.value = false
  lightboxImageState.value = 'loading'
  resetLightboxView()
}

function toggleLightboxResolution() {
  lightboxOriginal.value = !lightboxOriginal.value
  lightboxImageState.value = 'loading'
  resetLightboxView()
}

function retryLightboxImage() {
  lightboxImageState.value = 'loading'
  lightboxLoadAttempt.value++
}

function resetLightboxView() {
  isDragging.value = false
  lightboxScale.value = 1
  lightboxPosition.value = { x: 0, y: 0 }
}

function constrainLightboxPosition(position: { x: number, y: number }, scale: number) {
  const viewport = lightboxViewport.value
  const image = lightboxImageElement.value
  if (!viewport || !image) return position
  const maxX = Math.max(0, (image.clientWidth * scale - viewport.clientWidth) / 2)
  const maxY = Math.max(0, (image.clientHeight * scale - viewport.clientHeight) / 2)
  return {
    x: Math.max(-maxX, Math.min(maxX, position.x)),
    y: Math.max(-maxY, Math.min(maxY, position.y))
  }
}

function setLightboxScale(scale: number, anchor = { x: 0, y: 0 }) {
  const nextScale = Math.max(0.5, Math.min(scale, 5))
  const ratio = nextScale / lightboxScale.value
  // Keep the image point beneath the pointer stationary while zooming.
  lightboxPosition.value = constrainLightboxPosition({
    x: anchor.x - (anchor.x - lightboxPosition.value.x) * ratio,
    y: anchor.y - (anchor.y - lightboxPosition.value.y) * ratio
  }, nextScale)
  lightboxScale.value = nextScale
}

function zoomIn() {
  setLightboxScale(lightboxScale.value * 1.25)
}

function zoomOut() {
  setLightboxScale(lightboxScale.value / 1.25)
}

function handleLightboxWheel(e: WheelEvent) {
  e.preventDefault()
  const viewport = lightboxViewport.value
  if (!viewport || e.deltaY === 0 || lightboxImageState.value !== 'loaded') return
  const bounds = viewport.getBoundingClientRect()
  const delta = e.deltaY * (e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? viewport.clientHeight : 1)
  const factor = Math.exp(-Math.max(-100, Math.min(100, delta)) * 0.002)
  setLightboxScale(lightboxScale.value * factor, {
    x: e.clientX - bounds.left - bounds.width / 2,
    y: e.clientY - bounds.top - bounds.height / 2
  })
}

function handleLightboxPointerDown(e: PointerEvent) {
  if (e.button !== 0 || !e.isPrimary || lightboxImageState.value !== 'loaded') return
  const viewport = lightboxViewport.value
  if (!viewport) return
  viewport.setPointerCapture(e.pointerId)
  isDragging.value = true
  dragStart.value = { x: e.clientX - lightboxPosition.value.x, y: e.clientY - lightboxPosition.value.y }
}

function handleLightboxPointerMove(e: PointerEvent) {
  if (!isDragging.value || !e.isPrimary) return
  lightboxPosition.value = constrainLightboxPosition({
    x: e.clientX - dragStart.value.x,
    y: e.clientY - dragStart.value.y
  }, lightboxScale.value)
}

function handleLightboxPointerUp() {
  isDragging.value = false
}

const leftImageId = ref<string | undefined>(undefined)
const rightImageId = ref<string | undefined>(undefined)

const getImageSrc = (imageId: string, size: 'overview' | 'detail' | 'original' = 'overview') =>
  `/api/projects/${props.projectId}/pages/images/${imageId}/presentation?size=${size}`

const lightboxSrc = computed(() => {
  const image = lightboxImage.value
  if (!image) return ''
  const src = getImageSrc(image.id, lightboxOriginal.value ? 'original' : 'detail')
  return lightboxLoadAttempt.value ? `${src}&_retry=${lightboxLoadAttempt.value}` : src
})

const flatImageList = computed(() =>
  pageImages.value.map(img => ({
    label: `${img.variant || 'Original'} (${img.baseName})`,
    value: img.id
  }))
)

const leftImageSrc = computed(() =>
  leftImageId.value ? getImageSrc(leftImageId.value, 'detail') : ''
)

const rightImageSrc = computed(() =>
  rightImageId.value ? getImageSrc(rightImageId.value, 'detail') : ''
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
  const target = await backgroundDownloads.prepareDownload(image.fileName)
  if (!target) return

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
        await backgroundDownloads.downloadBlobResponse(response, image.fileName, job, target)
      }
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to download image'
    toast.add({ title: 'Download failed', description: message, color: 'error' })
  }
}

async function downloadSelectedImages() {
  const page = currentPage.value
  const images = selectedImages.value
  if (!page || images.length === 0 || isDownloading.value) return

  const query = new URLSearchParams()
  images.forEach(image => query.append('imageIds', image.id))
  const fileName = `${page.name} - images.zip`
  const target = await backgroundDownloads.prepareDownload(fileName)
  if (!target) return

  isDownloading.value = true
  try {
    await backgroundDownloads.runBackgroundJob({
      title: 'Downloading selected images',
      subtitle: `${images.length} image${images.length === 1 ? '' : 's'}`,
      statusLabel: 'Preparing ZIP',
      completedLabel: 'Downloaded',
      icon: 'i-lucide-file-archive',
      task: async (job) => {
        const response = await fetch(
          `/api/projects/${props.projectId}/pages/${page.id}/images/download?${query.toString()}`
        )
        if (!response.ok) {
          throw new Error(`Download failed (${response.status})`)
        }
        await backgroundDownloads.downloadBlobResponse(response, fileName, job, target)
      }
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to download selected images'
    toast.add({ title: 'Download failed', description: message, color: 'error', icon: 'i-lucide-alert-circle' })
  } finally {
    isDownloading.value = false
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

let pageImageRequestId = 0

async function fetchPageImages() {
  const page = currentPage.value
  if (!page) return

  const requestId = ++pageImageRequestId
  isLoading.value = true
  closeLightbox()
  exitSelectionMode()

  try {
    const response = await $fetch<PageImage[]>(
      `/api/projects/${props.projectId}/pages/${page.id}/images`
    )
    if (requestId !== pageImageRequestId) return
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
    if (requestId !== pageImageRequestId) return
    const message = error instanceof Error ? error.message : 'An error occurred'
    toast.add({
      title: 'Failed to load images',
      description: message,
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
    pageImages.value = []
  } finally {
    if (requestId === pageImageRequestId) isLoading.value = false
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
    } else if (lightboxImageState.value !== 'loaded') {
      return
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
    :ui="{
      content: 'w-full max-w-[96vw] sm:max-w-[92vw] h-[calc(100svh-2rem)] flex flex-col',
      body: 'flex min-h-0 flex-1 flex-col overflow-hidden p-4 sm:p-6'
    }"
  >
    <template #header>
      <UiSlideoverHeader
        :title="`Images for ${currentPage?.name || 'page'}`"
        icon="i-lucide-images"
        description="Browse this page's images and variants."
      >
        <template v-if="hasMultiplePages" #description>
          <div class="flex flex-wrap items-center gap-2 pt-1">
            <UButton
              icon="i-lucide-chevron-left"
              color="neutral"
              variant="ghost"
              size="sm"
              aria-label="Previous page"
              @click.stop="goToPreviousPage"
            />
            <USelectMenu
              :model-value="currentPageId"
              :items="pageOptions"
              value-key="value"
              searchable
              searchable-placeholder="Search pages..."
              aria-label="Choose page"
              class="w-40 min-w-0 max-w-full sm:w-48"
              @update:model-value="goToPage"
            />
            <UButton
              icon="i-lucide-chevron-right"
              color="neutral"
              variant="ghost"
              size="sm"
              aria-label="Next page"
              @click.stop="goToNextPage"
            />
            <span class="text-sm text-muted">
              {{ currentPageIndex + 1 }} / {{ sortedPages.length }}
            </span>
          </div>
        </template>
      </UiSlideoverHeader>
    </template>

    <template #body>
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
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
                  icon="i-lucide-download"
                  color="primary"
                  variant="soft"
                  size="sm"
                  :disabled="selectedImageIds.size === 0"
                  :loading="isDownloading"
                  @click="downloadSelectedImages"
                >
                  Download Selected
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

        <div v-if="isLoading" class="flex-1 min-h-0 overflow-hidden p-4 sm:p-5">
          <USkeleton class="size-full" />
        </div>

        <div v-else-if="activeTab === 'overview' && pageImages.length > 0" class="flex-1 min-h-0 overflow-hidden p-4 sm:p-6">
          <UCarousel
            v-slot="{ item: image }"
            :items="pageImages"
            arrows
            wheel-gestures
            :prev="{ variant: 'solid' }"
            :next="{ variant: 'solid' }"
            :ui="{
              viewport: 'h-full min-h-0 overflow-hidden',
              container: 'h-full min-h-0 items-stretch ms-0',
              item: 'h-full min-h-0 basis-full xl:basis-1/3',
              prev: 'xl:start-8',
              next: 'xl:end-8'
            }"
            class="h-full min-h-0 max-h-full overflow-hidden"
          >
            <article class="group relative flex h-full max-h-full min-h-0 min-w-0 cursor-pointer flex-col self-center overflow-hidden rounded-2xl border border-default bg-elevated p-2 shadow-sm">
              <button
                v-if="isSelectionMode"
                type="button"
                class="absolute left-4 top-4 z-30 flex size-7 items-center justify-center rounded-full border bg-default shadow-sm transition-colors duration-150"
                :class="selectedImageIds.has(image.id)
                  ? 'border-success bg-success text-white shadow-lg ring-2 ring-success/30'
                  : 'border-default text-muted'"
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
              <div
                v-if="imageStates[image.id] === 'loading'"
                class="pointer-events-none absolute inset-0 z-20 flex items-center justify-center text-muted"
              >
                <UIcon name="i-lucide-loader" class="size-5 animate-spin" />
              </div>
              <div
                v-if="imageStates[image.id] === 'error'"
                class="pointer-events-none absolute inset-0 z-20 flex flex-col items-center justify-center text-muted"
              >
                <UIcon name="i-lucide-image-off" class="mb-2 size-7" />
                <span class="text-xs">Failed to load</span>
              </div>

              <div class="relative min-h-0 flex-1 overflow-hidden rounded-xl">
                <img
                  v-show="imageStates[image.id] !== 'error'"
                  :src="getImageSrc(image.id)"
                  :alt="image.fileName"
                  class="absolute inset-0 block h-full w-full object-contain transition-transform duration-300 group-hover:scale-[1.02]"
                  loading="lazy"
                  decoding="async"
                  @load="imageStates[image.id] = 'loaded'"
                  @error="imageStates[image.id] = 'error'"
                >
              </div>

              <div class="absolute inset-x-2 bottom-2 z-20 rounded-b-xl bg-linear-to-t from-black/85 via-black/45 to-transparent px-3 pb-3 pt-14 text-white">
                <div class="flex items-center justify-between gap-3">
                  <div class="flex min-w-0 items-center gap-2">
                    <h5 class="truncate text-sm font-semibold">
                      {{ image.variant || 'Original' }}
                    </h5>
                    <UBadge
                      size="xs"
                      color="neutral"
                      variant="soft"
                      class="shrink-0 rounded-full bg-black/35 text-white backdrop-blur-sm"
                      title="Detected image type"
                    >
                      {{ imageTypeLabel(image.imageType) }}
                    </UBadge>
                  </div>
                  <UBadge
                    size="xs"
                    color="neutral"
                    variant="soft"
                    class="shrink-0 rounded-full bg-black/35 text-white tabular-nums backdrop-blur-sm"
                  >
                    {{ formatFileSize(image.fileSize) }}
                  </UBadge>
                </div>
                <div class="mt-1 flex items-center justify-between gap-3">
                  <p class="min-w-0 truncate text-xs text-white/75">
                    {{ image.fileName }}
                  </p>
                  <div class="relative z-30 shrink-0">
                    <UDropdownMenu
                      :items="getImageCardItems(image)"
                      :content="{ align: 'end' }"
                    >
                      <UButton
                        icon="i-lucide-ellipsis-vertical"
                        size="sm"
                        color="neutral"
                        variant="ghost"
                        class="text-white hover:bg-white/15 hover:text-white"
                        aria-label="Open image actions"
                      />
                    </UDropdownMenu>
                  </div>
                </div>
              </div>
            </article>
          </UCarousel>
        </div>

        <div v-else-if="activeTab === 'compare' && pageImages.length >= 2" class="flex-1 flex flex-col min-h-0 p-4 gap-4">
          <div class="flex gap-4 items-center shrink-0">
            <div class="flex-1">
              <div class="mb-2 flex items-center gap-2">
                <label class="text-sm font-medium">Left Image</label>
                <UBadge
                  v-if="leftImageId"
                  color="neutral"
                  variant="soft"
                  size="xs"
                  title="Detected image type"
                >
                  {{ imageTypeLabel(pageImages.find(image => image.id === leftImageId)?.imageType) }}
                </UBadge>
              </div>
              <USelectMenu v-model="leftImageId" :items="flatImageList" value-key="value" />
            </div>
            <div class="flex-1">
              <div class="mb-2 flex items-center gap-2">
                <label class="text-sm font-medium">Right Image</label>
                <UBadge
                  v-if="rightImageId"
                  color="neutral"
                  variant="soft"
                  size="xs"
                  title="Detected image type"
                >
                  {{ imageTypeLabel(pageImages.find(image => image.id === rightImageId)?.imageType) }}
                </UBadge>
              </div>
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
          >
            <div class="absolute top-4 left-4 right-4 flex items-center justify-between z-10 pointer-events-none">
              <div class="flex items-center gap-2 bg-black/50 backdrop-blur rounded-sm px-3 py-2 pointer-events-auto">
                <span class="text-white text-sm font-medium">
                  {{ lightboxImage.variant || 'Original' }} - {{ lightboxImage.baseName }}
                </span>
                <UBadge
                  color="neutral"
                  variant="soft"
                  size="xs"
                  class="shrink-0 bg-white/15 text-white"
                  title="Detected image type"
                >
                  {{ imageTypeLabel(lightboxImage.imageType) }}
                </UBadge>
                <UBadge color="neutral" variant="soft" size="xs">
                  {{ lightboxImageIndex + 1 }} / {{ pageImages.length }}
                </UBadge>
              </div>
              <div class="flex items-center gap-1 bg-black/50 backdrop-blur rounded-sm px-2 py-1 pointer-events-auto">
                <button
                  type="button"
                  class="flex items-center gap-2 p-2 rounded-sm text-white hover:bg-white/20 transition-colors"
                  :title="lightboxOriginal ? 'Current image: Full Resolution. Click to switch to Preview.' : `Current image: Preview. Click to load Full Resolution (${formatFileSize(lightboxImage.fileSize)}).`"
                  :aria-label="`Image resolution: ${lightboxOriginal ? 'Full Resolution' : 'Preview'}`"
                  :aria-pressed="lightboxOriginal"
                  :class="{ 'bg-white/20': lightboxOriginal }"
                  @click.stop="toggleLightboxResolution"
                >
                  <UIcon :name="lightboxOriginal ? 'i-lucide-hd' : 'i-tabler-badge-sd'" class="w-5 h-5" />
                </button>
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
                  :disabled="lightboxImageState !== 'loaded'"
                  title="Zoom out (-)"
                  @click.stop="zoomOut"
                >
                  <UIcon name="i-lucide-zoom-out" class="w-5 h-5" />
                </button>
                <button
                  type="button"
                  class="p-2 rounded-sm text-white hover:bg-white/20 transition-colors"
                  :disabled="lightboxImageState !== 'loaded'"
                  title="Zoom in (+)"
                  @click.stop="zoomIn"
                >
                  <UIcon name="i-lucide-zoom-in" class="w-5 h-5" />
                </button>
                <button
                  type="button"
                  class="p-2 rounded-sm text-white hover:bg-white/20 transition-colors"
                  :disabled="lightboxImageState !== 'loaded'"
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
              class="absolute left-4 top-1/2 -translate-y-1/2 bg-black/50 backdrop-blur p-3 rounded-sm text-white hover:bg-black/70 transition-colors z-10"
              title="Previous image"
              @click.stop="lightboxPrev"
            >
              <UIcon name="i-lucide-chevron-left" class="w-8 h-8" />
            </button>
            <button
              v-if="pageImages.length > 1"
              type="button"
              class="absolute right-4 top-1/2 -translate-y-1/2 bg-black/50 backdrop-blur p-3 rounded-sm text-white hover:bg-black/70 transition-colors z-10"
              title="Next image"
              @click.stop="lightboxNext"
            >
              <UIcon name="i-lucide-chevron-right" class="w-8 h-8" />
            </button>

            <div
              ref="lightboxViewport"
              class="absolute inset-0 flex touch-none items-center justify-center overflow-hidden p-12 sm:p-16"
              :class="{ 'cursor-grab': !isDragging, 'cursor-grabbing': isDragging }"
              @wheel.prevent="handleLightboxWheel"
              @pointerdown.stop="handleLightboxPointerDown"
              @pointermove="handleLightboxPointerMove"
              @pointerup="handleLightboxPointerUp"
              @pointercancel="handleLightboxPointerUp"
              @lostpointercapture="handleLightboxPointerUp"
            >
              <img
                v-show="lightboxImageState === 'loaded'"
                ref="lightboxImageElement"
                :key="`${lightboxSrc}:${lightboxLoadAttempt}`"
                :src="lightboxSrc"
                :alt="lightboxImage.fileName"
                class="min-h-0 min-w-0 shrink-0 select-none"
                :style="{
                  maxWidth: '100%',
                  maxHeight: '100%',
                  objectFit: 'contain',
                  transform: `translate(${lightboxPosition.x}px, ${lightboxPosition.y}px) scale(${lightboxScale})`,
                  transformOrigin: 'center center'
                }"
                draggable="false"
                decoding="async"
                @load="lightboxImageState = 'loaded'"
                @error="lightboxImageState = 'error'"
              >
              <div v-if="lightboxImageState === 'loading'" class="pointer-events-none absolute inset-0 flex items-center justify-center text-white" role="status">
                <UIcon name="i-lucide-loader" class="size-7 animate-spin" />
                <span class="sr-only">Loading image</span>
              </div>
              <div
                v-else-if="lightboxImageState === 'error'"
                class="absolute inset-0 flex flex-col items-center justify-center gap-3 text-white"
                role="alert"
                @pointerdown.stop
              >
                <UIcon name="i-lucide-image-off" class="size-7" />
                <p class="text-sm">
                  {{ lightboxOriginal ? 'Failed to load the full-resolution image. Please retry or use preview.' : 'Failed to load image. Please retry.' }}
                </p>
                <UButton color="neutral" variant="soft" @click.stop="retryLightboxImage">
                  Retry
                </UButton>
              </div>
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
