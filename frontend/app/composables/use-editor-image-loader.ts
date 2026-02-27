import type { PageData } from '@/stores/editor/types'

const isLoading = ref(false)
const loadedThumbnails = ref(new Set<string>())
const loadedImages = ref(new Set<string>())

const MAX_CACHED_IMAGES = 100
const imageAccessOrder: string[] = []

function preloadImage(url: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve()
    img.onerror = reject
    img.src = url
  })
}

/**
 * Track image access for LRU eviction
 */
function trackImageAccess(variantId: string) {
  const idx = imageAccessOrder.indexOf(variantId)
  if (idx > -1) {
    imageAccessOrder.splice(idx, 1)
  }
  imageAccessOrder.push(variantId)

  while (imageAccessOrder.length > MAX_CACHED_IMAGES) {
    const oldest = imageAccessOrder.shift()
    if (oldest) {
      loadedImages.value.delete(oldest)
    }
  }
}

async function loadThumbnails(pages: PageData[]) {
  isLoading.value = true

  const pagesToLoad = pages.filter(p => p.thumbnail && !loadedThumbnails.value.has(p.id))

  const promises = pagesToLoad.map(async (page) => {
    try {
      await preloadImage(page.thumbnail!)
      loadedThumbnails.value.add(page.id)
    } catch (error) {
      console.warn(`Failed to load thumbnail for page ${page.id}:`, error)
    }
  })

  await Promise.allSettled(promises)
  isLoading.value = false
}

/**
 * Prefetch images for pages bidirectionally.
 * @param currentPageId - The current page ID to prefetch around
 * @param pages - All pages in the editor
 * @param forwardCount - Number of pages to prefetch ahead (default: 5)
 * @param backwardCount - Number of pages to prefetch behind (default: 3)
 */
async function prefetchImagesBidirectional(
  currentPageId: string,
  pages: PageData[],
  forwardCount: number = 5,
  backwardCount: number = 3
) {
  const currentIndex = pages.findIndex(p => p.id === currentPageId)
  if (currentIndex === -1) return

  const indicesToPrefetch: number[] = []

  for (let i = 1; i <= forwardCount; i++) {
    const idx = currentIndex + i
    if (idx < pages.length) indicesToPrefetch.push(idx)
  }

  for (let i = 1; i <= backwardCount; i++) {
    const idx = currentIndex - i
    if (idx >= 0) indicesToPrefetch.push(idx)
  }

  const promises: Promise<void>[] = []

  for (const idx of indicesToPrefetch) {
    const page = pages[idx]
    if (!page) continue

    const variant = page.imageVariants[0]
    if (!variant) continue

    if (!loadedImages.value.has(variant.id)) {
      promises.push(
        preloadImage(variant.url)
          .then(() => {
            loadedImages.value.add(variant.id)
            trackImageAccess(variant.id)
          })
          .catch(err => console.warn(`Failed to prefetch ${variant.id}:`, err))
      )
    } else {
      trackImageAccess(variant.id)
    }
  }

  await Promise.allSettled(promises)
}

/**
 * Legacy function for backward compatibility
 */
async function prefetchImages(pageIds: string[], pages: PageData[], count: number = 5) {
  const pagesToPrefetch = pageIds.slice(0, count)
  const promises: Promise<void>[] = []

  for (const pageId of pagesToPrefetch) {
    const page = pages.find(p => p.id === pageId)
    if (!page) continue

    for (const variant of page.imageVariants) {
      if (!loadedImages.value.has(variant.id)) {
        promises.push(
          preloadImage(variant.url)
            .then(() => {
              loadedImages.value.add(variant.id)
              trackImageAccess(variant.id)
            })
            .catch(err => console.warn(`Failed to prefetch ${variant.id}:`, err))
        )
      }
    }
  }

  await Promise.allSettled(promises)
}

function isThumbnailLoaded(pageId: string): boolean {
  return loadedThumbnails.value.has(pageId)
}

function isImageLoaded(variantId: string): boolean {
  return loadedImages.value.has(variantId)
}

export function useEditorImageLoader() {
  return {
    isLoading: readonly(isLoading),
    loadedThumbnails: readonly(loadedThumbnails),
    loadedImages: readonly(loadedImages),
    loadThumbnails,
    prefetchImages,
    prefetchImagesBidirectional,
    isThumbnailLoaded,
    isImageLoaded
  }
}
