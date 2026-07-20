import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  updatePreference: vi.fn(),
  fetchedPageFocusMode: null as boolean | null,
  fetchedTextModeSubmode: null as 'visual' | 'expert' | 'full' | null
}))

vi.mock('@/composables/use-editor-preferences', () => ({
  useEditorPreferences: () => ({
    fetchPreferences: vi.fn(async () => ({
      pageFocusMode: mocks.fetchedPageFocusMode,
      textModeSubmode: mocks.fetchedTextModeSubmode
    })),
    updatePreference: mocks.updatePreference,
    updatePreferences: vi.fn()
  })
}))

async function initializeStoreGlobals() {
  const [pinia, vue] = await Promise.all([import('pinia'), import('vue')])
  ;(globalThis as any).defineStore = pinia.defineStore
  ;(globalThis as any).ref = vue.ref
  pinia.setActivePinia(pinia.createPinia())
}

async function createStore() {
  await initializeStoreGlobals()
  const { useEditorUiStore } = await import('../editor.ui.store')
  return useEditorUiStore()
}

describe('editor.ui.store page Focus mode', () => {
  beforeEach(() => {
    mocks.fetchedPageFocusMode = null
    mocks.fetchedTextModeSubmode = null
    mocks.updatePreference.mockReset()
  })

  it('defaults to enabled when the saved preference is missing', async () => {
    const store = await createStore()

    await store.loadPreferences()

    expect(store.pageFocusMode).toBe(true)
  })

  it('loads and immediately persists an explicit user choice', async () => {
    mocks.fetchedPageFocusMode = false
    const store = await createStore()

    await store.loadPreferences()
    expect(store.pageFocusMode).toBe(false)

    store.setPageFocusMode(true)

    expect(store.pageFocusMode).toBe(true)
    expect(mocks.updatePreference).toHaveBeenCalledWith('pageFocusMode', true, { immediate: true })
  })

  it('loads and persists the Full text submode', async () => {
    mocks.fetchedTextModeSubmode = 'full'
    const store = await createStore()

    await store.loadPreferences()
    expect(store.textModeSubmode).toBe('full')

    store.setTextModeSubmode('visual')

    expect(store.textModeSubmode).toBe('visual')
    expect(mocks.updatePreference).toHaveBeenCalledWith('textModeSubmode', 'visual')
  })
})
