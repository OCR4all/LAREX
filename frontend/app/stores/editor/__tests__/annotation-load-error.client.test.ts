import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia, storeToRefs } from 'pinia'
import { ref } from 'vue'
import { useEditorStore } from '../editor.store'
import { useEditorDocumentStore } from '../editor.document.store'
import { clearAllEditorSessions, getEditorSession } from '@/session/editor/editor-session'

function setupPage(markLoaded = true) {
  useEditorDocumentStore().setProjectPages('project', [{
    id: 'page', projectId: 'project', label: 'Page',
    imageVariants: [{ id: 'image', url: '/image.png', label: 'original' }],
    xmlFiles: [{ id: 'xml', fileName: 'page.xml', schema: 'PAGE_XML' }],
    tags: [], resolvedTags: null, locked: false, lockedReason: null
  }], { markLoaded })
  return useEditorStore()
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.stubGlobal('storeToRefs', storeToRefs)
  vi.stubGlobal('useState', (_key: string, init: () => unknown) => ref(init()))
  vi.stubGlobal('useDebounceFn', (callback: unknown) => callback)
  vi.stubGlobal('useUserSession', () => ({ user: ref({ username: 'editor' }) }))
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

afterEach(() => {
  clearAllEditorSessions()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

it('keeps an invalid annotation failure visible and blocks saving the empty fallback', async () => {
  const errors = [{ line: 2, column: 7, severity: 'error', code: 'XSD_VALIDATION_ERROR', message: 'Missing imageWidth' }]
  const fetch = vi.fn(async (path: string) => {
    if (path.endsWith('/text')) return { validation: { valid: false, errors } }
    throw new Error('Unable to parse PAGE XML')
  })
  vi.stubGlobal('$fetch', fetch)
  const store = setupPage()

  expect(await store.loadPageIntoCanvas('canvas', 'project', 'page', undefined, { awaitAnnotations: true })).toBeNull()
  expect(store.getCanvas('canvas')?.annotationLoadError).toMatchObject({ xmlId: 'xml', invalidXml: true, errors })
  expect(await store.saveAnnotations('canvas')).toBe(false)
  expect(fetch).toHaveBeenCalledTimes(2)
})

it('does not classify a network failure as invalid XML', async () => {
  vi.stubGlobal('$fetch', vi.fn().mockRejectedValue(new Error('Service unavailable')))
  const store = setupPage()
  await store.loadPageIntoCanvas('canvas', 'project', 'page', undefined, { awaitAnnotations: true })
  expect(store.getCanvas('canvas')?.annotationLoadError).toMatchObject({ invalidXml: false, message: 'Service unavailable' })
  expect(await store.saveAnnotations('canvas')).toBe(false)
})

it('keeps a metadata failure visible instead of opening an empty annotation', async () => {
  vi.stubGlobal('$fetch', vi.fn(async (path: string) => {
    if (path.endsWith('/images')) return [{ id: 'image', fileName: 'page.png', variant: 'original' }]
    throw new Error('XML metadata unavailable')
  }))
  const store = setupPage(false)
  expect(await store.loadPageIntoCanvas('canvas', 'project', 'page', undefined, { awaitAnnotations: true })).toBeNull()
  expect(store.getCanvas('canvas')?.annotationLoadError).toMatchObject({ invalidXml: false, message: 'XML metadata unavailable' })
  expect(await store.saveAnnotations('canvas')).toBe(false)
})

it('clears a failed load after the saved XML is repaired', async () => {
  const fetch = vi.fn().mockRejectedValue(new Error('Invalid PAGE XML'))
  vi.stubGlobal('$fetch', fetch)
  const store = setupPage()
  await store.loadPageIntoCanvas('canvas', 'project', 'page', undefined, { awaitAnnotations: true })
  fetch.mockResolvedValue({ imageWidth: 300, imageHeight: 200, regions: [] })

  expect(await store.loadPageIntoCanvas('canvas', 'project', 'page', undefined, { awaitAnnotations: true })).toBe('/image.png')
  expect(store.getCanvas('canvas')?.annotationLoadError).toBeNull()
  expect(store.getCanvas('canvas')?.xmlFileId).toBe('xml')
})

it('ignores late validation errors from an older page load', async () => {
  let finish!: (value: unknown) => void
  const validation = new Promise((resolve) => {
    finish = resolve
  })
  let diagnosticRequested!: () => void
  const requested = new Promise<void>((resolve) => {
    diagnosticRequested = resolve
  })
  const fetch = vi.fn(async (path: string): Promise<unknown> => {
    if (path.endsWith('/text')) {
      diagnosticRequested()
      return validation
    }
    throw new Error('Invalid PAGE XML')
  })
  vi.stubGlobal('$fetch', fetch)
  const store = setupPage()
  const older = store.loadPageIntoCanvas('canvas', 'project', 'page', undefined, { awaitAnnotations: true })
  await requested
  fetch.mockResolvedValue({ imageWidth: 300, imageHeight: 200, regions: [] })
  await store.loadPageIntoCanvas('canvas', 'project', 'page', undefined, { awaitAnnotations: true })
  finish({ validation: { valid: false, errors: [] } })
  await older

  expect(store.getCanvas('canvas')?.annotationLoadError).toBeNull()
  expect(store.getCanvas('canvas')?.isLoadingAnnotations).toBe(false)
})

it('keeps original dimensions on the canvas that finished loading, independently of the active canvas', () => {
  const store = setupPage()
  store.registerCanvas('background')
  store.registerCanvas('active')
  store.setActiveCanvas('active')
  const before = getEditorSession('active')!.document.value!.page
  const activeSize = { width: before.imageWidth, height: before.imageHeight }
  store.setImageSize(19218, 12623, 'background')
  expect(store.getCanvas('background')?.imageSize).toEqual({ width: 19218, height: 12623 })
  expect(getEditorSession('background')!.document.value!.page.imageWidth).toBe(19218)
  expect(getEditorSession('active')!.document.value!.page.imageWidth).toBe(activeSize.width)
  expect(getEditorSession('active')!.document.value!.page.imageHeight).toBe(activeSize.height)
  expect(store.getCanvas('background')?.hasUnsavedChanges).toBe(false)
})
