import type { PageData } from '@/stores/editor/types'
import type { PageDto } from '@/services/editor/page-conversion.service'

interface PrefetchState {
  prefetchedAnnotations: Map<string, PageDto>
  pendingPrefetches: Set<string>
  prefetchedImages: Set<string>
}

const state = ref<PrefetchState>({
  prefetchedAnnotations: new Map(),
  pendingPrefetches: new Set(),
  prefetchedImages: new Set()
})

/**
 * Composable for prefetching page data from the project overview.
 * Prefetches annotations and images for selected pages before navigating to the editor.
 */
export function usePagePrefetch() {
  /**
   * Prefetch annotations for a list of pages.
   * This is called when the user selects pages or hovers on "Open in Editor".
   */
  async function prefetchAnnotations(projectId: string, pageIds: string[]): Promise<void> {
    if (!projectId || pageIds.length === 0) return

    const pagesToPrefetch = pageIds.slice(0, 5)

    for (const pageId of pagesToPrefetch) {
      const cacheKey = `${projectId}:${pageId}`

      if (state.value.prefetchedAnnotations.has(cacheKey) || state.value.pendingPrefetches.has(cacheKey)) {
        continue
      }

      state.value.pendingPrefetches.add(cacheKey)

      try {
        const xmlFiles = await $fetch<Array<{ id: string, schema: string }>>(`/api/projects/${projectId}/pages/${pageId}/xml`)
        const pageXmlFile = xmlFiles.find(xml => xml.schema === 'PAGE_XML')

        if (!pageXmlFile) {
          state.value.pendingPrefetches.delete(cacheKey)
          continue
        }

        const pageDto = await $fetch<PageDto>(
          `/api/projects/${projectId}/pages/${pageId}/annotations/${pageXmlFile.id}`
        )

        state.value.prefetchedAnnotations.set(cacheKey, pageDto)
        console.log(`[PagePrefetch] Prefetched annotations for page ${pageId}`)
      } catch (error) {
        console.warn(`[PagePrefetch] Failed to prefetch annotations for page ${pageId}:`, error)
      } finally {
        state.value.pendingPrefetches.delete(cacheKey)
      }
    }
  }

  /**
   * Prefetch images for a list of pages.
   */
  async function prefetchImages(projectId: string, pageIds: string[]): Promise<void> {
    if (!projectId || pageIds.length === 0) return

    const pagesToPrefetch = pageIds.slice(0, 3)

    for (const pageId of pagesToPrefetch) {
      if (state.value.prefetchedImages.has(pageId)) continue

      try {
        const images = await $fetch<Array<{ id: string }>>(`/api/projects/${projectId}/pages/${pageId}/images`)

        if (images.length === 0) continue

        const firstImage = images[0]
        if (!firstImage) continue

        const imageUrl = `/api/projects/${projectId}/pages/images/${firstImage.id}/blob`

        await preloadImage(imageUrl)
        state.value.prefetchedImages.add(pageId)
        console.log(`[PagePrefetch] Prefetched image for page ${pageId}`)
      } catch (error) {
        console.warn(`[PagePrefetch] Failed to prefetch image for page ${pageId}:`, error)
      }
    }
  }

  /**
   * Helper to preload an image using the browser's Image API
   */
  function preloadImage(url: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve()
      img.onerror = reject
      img.src = url
    })
  }

  /**
   * Prefetch both annotations and images for selected pages.
   * Call this when user is about to open the editor.
   */
  async function prefetchForEditor(projectId: string, pageIds: string[]): Promise<void> {
    if (!projectId || pageIds.length === 0) return

    console.log(`[PagePrefetch] Starting prefetch for ${pageIds.length} pages`)

    await Promise.allSettled([
      prefetchAnnotations(projectId, pageIds),
      prefetchImages(projectId, pageIds)
    ])
  }

  /**
   * Get prefetched annotation for a page (to be used by editor store)
   */
  function getPrefetchedAnnotation(projectId: string, pageId: string): PageDto | undefined {
    const cacheKey = `${projectId}:${pageId}`
    return state.value.prefetchedAnnotations.get(cacheKey)
  }

  /**
   * Transfer prefetched annotations to the editor's annotation cache.
   * Call this when opening the editor to warm its cache.
   */
  function transferToEditorCache(projectId: string, editorCache: { set: (key: string, value: PageDto) => void }): number {
    let transferred = 0

    for (const [key, value] of state.value.prefetchedAnnotations.entries()) {
      if (key.startsWith(`${projectId}:`)) {
        editorCache.set(key, value)
        transferred++
      }
    }

    console.log(`[PagePrefetch] Transferred ${transferred} prefetched annotations to editor cache`)
    return transferred
  }

  /**
   * Clear all prefetched data (e.g., when switching projects)
   */
  function clear(): void {
    state.value.prefetchedAnnotations.clear()
    state.value.pendingPrefetches.clear()
    state.value.prefetchedImages.clear()
    console.log('[PagePrefetch] Cleared all prefetched data')
  }

  /**
   * Check if a page has been prefetched
   */
  function isPrefetched(projectId: string, pageId: string): boolean {
    const cacheKey = `${projectId}:${pageId}`
    return state.value.prefetchedAnnotations.has(cacheKey)
  }

  return {
    prefetchAnnotations,
    prefetchImages,
    prefetchForEditor,
    getPrefetchedAnnotation,
    transferToEditorCache,
    isPrefetched,
    clear
  }
}
