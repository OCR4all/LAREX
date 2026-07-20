import type { Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { resolveTextModeSubmodeFromQuery } from '@/utils/editor/text-mode'

type EditorDeepLinksOptions = {
  route: RouteLocationNormalizedLoaded
  router: Router
  isApplyingPageDeepLink?: Ref<boolean>
  ensureFullProjectPagesLoaded: (projectId: string) => Promise<boolean>
  openEditorForPage: (
    projectId: string,
    pageId: string,
    variantId?: string
  ) => Promise<'opened' | 'cancelled' | 'unavailable'>
  getErrorMessage: (error: unknown, fallback: string) => string
}

export function useEditorDeepLinks(options: EditorDeepLinksOptions) {
  const editorStore = useEditorStore()
  const sessionStore = useEditorSessionStore()
  const editorUiStore = useEditorUiStore()
  const toast = useToast()
  const isApplyingPageDeepLink = options.isApplyingPageDeepLink ?? ref(false)

  function getSingleQueryValue(value: unknown): string | null {
    if (Array.isArray(value)) {
      const first = value[0]
      value = typeof first === 'string' ? first : null
    }
    if (typeof value !== 'string') return null

    const trimmed = value.trim()
    return trimmed.length > 0 ? trimmed : null
  }

  async function applyPageDeepLinkFromQuery(): Promise<void> {
    if (isApplyingPageDeepLink.value) return

    const projectId = getSingleQueryValue(options.route.query.projectId)
    const pageId = getSingleQueryValue(options.route.query.pageId)
    const variantId = getSingleQueryValue(options.route.query.variantId) ?? undefined
    const editorMode = getSingleQueryValue(options.route.query.editorMode)
    const textView = getSingleQueryValue(options.route.query.textView)
    const textSearch = getSingleQueryValue(options.route.query.textSearch)
    if (!projectId || !pageId) return

    isApplyingPageDeepLink.value = true
    try {
      const openResult = await options.openEditorForPage(projectId, pageId, variantId)
      if (openResult === 'cancelled') return

      const wasOpened = sessionStore.getOpenedPageIds(projectId).includes(pageId)
      if (!wasOpened) {
        toast.add({
          title: 'Unable to open linked page',
          description: 'The page does not exist or you do not have access to it.',
          color: 'error',
          icon: 'i-lucide-alert-circle'
        })
        return
      }

      if (editorMode === 'text') {
        editorStore.setUiMode('text')
        editorUiStore.setTextModeSubmode(resolveTextModeSubmodeFromQuery(textView))
        sessionStore.updateTextViewSettings(current => ({
          ...current,
          mode: 'textline',
          searchQuery: textSearch ?? current.searchQuery
        }))
      }

      const nextQuery = { ...options.route.query }
      delete nextQuery.projectId
      delete nextQuery.pageId
      delete nextQuery.variantId
      delete nextQuery.editorMode
      delete nextQuery.textView
      delete nextQuery.textSearch
      await options.router.replace({ path: options.route.path, query: nextQuery })
    } catch (error) {
      toast.add({
        title: 'Failed to open linked page',
        description: options.getErrorMessage(error, 'An unexpected error occurred while opening the page link.'),
        color: 'error',
        icon: 'i-lucide-alert-circle'
      })
    } finally {
      isApplyingPageDeepLink.value = false
    }
  }

  async function applyProjectDeepLinkFromQuery(): Promise<void> {
    if (isApplyingPageDeepLink.value) return

    const scope = getSingleQueryValue(options.route.query.scope)
    const projectId = getSingleQueryValue(options.route.query.projectId)
    const pageId = getSingleQueryValue(options.route.query.pageId)
    if (scope !== 'project' || !projectId || pageId) return

    isApplyingPageDeepLink.value = true
    try {
      const loaded = await options.ensureFullProjectPagesLoaded(projectId)
      if (!loaded) {
        toast.add({
          title: 'Unable to open linked project',
          description: 'The project does not exist or you do not have access to it.',
          color: 'error',
          icon: 'i-lucide-alert-circle'
        })
        return
      }

      const pages = editorStore.getProjectPages(projectId)
      if (pages.length === 0) {
        toast.add({
          title: 'Unable to open linked project',
          description: 'No accessible pages were found for this project.',
          color: 'error',
          icon: 'i-lucide-alert-circle'
        })
        return
      }

      const firstPage = pages[0]
      if (firstPage) {
        await options.openEditorForPage(projectId, firstPage.id)
      }

      const nextQuery = { ...options.route.query }
      delete nextQuery.projectId
      delete nextQuery.scope
      delete nextQuery.variantId
      await options.router.replace({ path: options.route.path, query: nextQuery })
    } catch (error) {
      toast.add({
        title: 'Failed to open linked project',
        description: options.getErrorMessage(error, 'An unexpected error occurred while opening the project link.'),
        color: 'error',
        icon: 'i-lucide-alert-circle'
      })
    } finally {
      isApplyingPageDeepLink.value = false
    }
  }

  async function applyEditorDeepLinkFromQuery(): Promise<void> {
    const projectId = getSingleQueryValue(options.route.query.projectId)
    const pageId = getSingleQueryValue(options.route.query.pageId)
    if (projectId && pageId) {
      await applyPageDeepLinkFromQuery()
      return
    }

    await applyProjectDeepLinkFromQuery()
  }

  return {
    isApplyingPageDeepLink,
    getSingleQueryValue,
    applyPageDeepLinkFromQuery,
    applyProjectDeepLinkFromQuery,
    applyEditorDeepLinkFromQuery
  }
}
