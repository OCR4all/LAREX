<script setup lang="ts">
defineOptions({
  inheritAttrs: false
})

interface PageBasic {
  id: string
  name: string
}

interface Props {
  projectId: string
  pages: PageBasic[]
  initialPageIndex: number
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
const isLoading = ref(true)
const pageImages = ref<PageImage[]>([])
const imageStates = ref<Record<string, 'loading' | 'loaded' | 'error'>>({})

type TabValue = 'overview' | 'compare'
const activeTab = ref<TabValue>('overview')
const tabItems = [
  { label: 'Overview', value: 'overview' as const },
  { label: 'Compare', value: 'compare' as const }
]

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

const groupedImages = computed(() =>
  pageImages.value.reduce((groups, image) => {
    const baseName = image.baseName || 'Unknown'
    ;(groups[baseName] ??= []).push(image)
    return groups
  }, {} as Record<string, PageImage[]>)
)

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

function downloadImage(image: PageImage) {
  const a = document.createElement('a')
  a.href = `/api/projects/${props.projectId}/pages/images/${image.id}/export`
  a.download = image.fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

async function fetchPageImages() {
  const page = currentPage.value
  if (!page) return

  isLoading.value = true

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
  <USlideover
    v-bind="attrs"
    side="right"
    :dismissible="!lightboxOpen"
    :close="{ onClick: () => { if (!lightboxOpen) emit('close', false) } }"
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
        <UTabs v-model="activeTab" :items="tabItems" class="shrink-0" />

        <div v-if="isLoading" class="flex-1 overflow-auto p-4">
          <div class="border border-neutral-200 dark:border-neutral-700 rounded-sm p-4">
            <USkeleton class="h-6 w-32 mb-3" />
            <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
              <div v-for="i in 6" :key="i" class="border border-neutral-200 dark:border-neutral-700 rounded-sm overflow-hidden">
                <USkeleton class="aspect-square w-full" />
                <div class="p-3 space-y-2">
                  <div class="flex items-center justify-between">
                    <USkeleton class="h-4 w-20" />
                    <USkeleton class="h-4 w-12" />
                  </div>
                  <USkeleton class="h-3 w-full" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'overview' && pageImages.length > 0" class="flex-1 overflow-auto p-4 space-y-6">
          <template v-for="(images, baseName) in groupedImages" :key="baseName">
            <div class="border border-neutral-200 dark:border-neutral-700 rounded-sm p-4">
              <h4 class="font-medium text-lg mb-3">
                {{ baseName }}
              </h4>
              <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
                <div
                  v-for="image in images"
                  :key="image.id"
                  class="border border-neutral-200 dark:border-neutral-700 rounded-sm overflow-hidden cursor-pointer hover:border-primary transition-colors"
                  @click="openLightbox(image)"
                >
                  <div class="aspect-square bg-neutral-100 dark:bg-neutral-800 flex items-center justify-center relative">
                    <div
                      v-if="imageStates[image.id] === 'loading'"
                      class="absolute flex items-center justify-center text-neutral-400"
                    >
                      <UIcon name="i-lucide-loader" class="animate-spin" />
                    </div>
                    <div
                      v-if="imageStates[image.id] === 'error'"
                      class="flex flex-col items-center justify-center text-neutral-400"
                    >
                      <UIcon name="i-lucide-image-off" class="text-2xl mb-1" />
                      <span class="text-xs">Failed to load</span>
                    </div>
                    <img
                      v-show="imageStates[image.id] !== 'error'"
                      :src="getImageSrc(image.id)"
                      :alt="image.fileName"
                      class="max-w-full max-h-full object-contain"
                      loading="lazy"
                      @load="imageStates[image.id] = 'loaded'"
                      @error="imageStates[image.id] = 'error'"
                    >
                  </div>
                  <div class="p-3 space-y-2">
                    <div class="flex items-center justify-between">
                      <h5 class="font-medium text-sm truncate">
                        {{ image.variant || 'Original' }}
                      </h5>
                      <UBadge size="xs" variant="solid">
                        {{ formatFileSize(image.fileSize) }}
                      </UBadge>
                    </div>
                    <p class="text-xs text-neutral-600 dark:text-neutral-400 truncate">
                      {{ image.fileName }}
                    </p>
                    <div class="flex justify-end">
                      <UButton
                        icon="i-lucide-download"
                        size="xs"
                        color="neutral"
                        variant="ghost"
                        title="Export image variant"
                        @click.stop="downloadImage(image)"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
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
          @click="emit('close', false)"
        >
          Close
        </UButton>
      </div>
    </template>
  </USlideover>
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
