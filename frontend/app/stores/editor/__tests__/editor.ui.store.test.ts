import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  updatePreference: vi.fn(),
  fetchedToolbarCompact: null as boolean | null,
  fetchedPageFocusMode: null as boolean | null,
  fetchedOpenRegionTypeMenuOnCreate: null as boolean | null,
  fetchedTextModeSubmode: null as 'visual' | 'expert' | 'full' | null,
  fetchedTextItemLayout: null as 'side-by-side' | 'vertical' | null
}))

vi.mock('@/composables/use-editor-preferences', () => ({
  useEditorPreferences: () => ({
    fetchPreferences: vi.fn(async () => ({
      toolbarCompact: mocks.fetchedToolbarCompact,
      pageFocusMode: mocks.fetchedPageFocusMode,
      openRegionTypeMenuOnCreate: mocks.fetchedOpenRegionTypeMenuOnCreate,
      textModeSubmode: mocks.fetchedTextModeSubmode,
      textItemLayout: mocks.fetchedTextItemLayout
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

describe('editor.ui.store preferences', () => {
  beforeEach(() => {
    mocks.fetchedToolbarCompact = null
    mocks.fetchedPageFocusMode = null
    mocks.fetchedOpenRegionTypeMenuOnCreate = null
    mocks.fetchedTextModeSubmode = null
    mocks.fetchedTextItemLayout = null
    mocks.updatePreference.mockReset()
  })

  it('defaults to a compact toolbar when the saved preference is missing', async () => {
    const store = await createStore()

    await store.loadPreferences()

    expect(store.toolbarCompact).toBe(true)
  })

  it('loads an explicit non-compact toolbar preference', async () => {
    mocks.fetchedToolbarCompact = false
    const store = await createStore()

    await store.loadPreferences()

    expect(store.toolbarCompact).toBe(false)
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

  it('opens the region type menu after creation by default', async () => {
    const store = await createStore()

    await store.loadPreferences()

    expect(store.globalSettings.openRegionTypeMenuOnCreate).toBe(true)
  })

  it('loads and persists the region type menu preference', async () => {
    mocks.fetchedOpenRegionTypeMenuOnCreate = false
    const store = await createStore()

    await store.loadPreferences()
    expect(store.globalSettings.openRegionTypeMenuOnCreate).toBe(false)

    store.toggleOpenRegionTypeMenuOnCreate()

    expect(store.globalSettings.openRegionTypeMenuOnCreate).toBe(true)
    expect(mocks.updatePreference).toHaveBeenCalledWith('openRegionTypeMenuOnCreate', true)
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

  it('defaults text items to the vertical layout when the saved preference is missing', async () => {
    const store = await createStore()

    await store.loadPreferences()

    expect(store.textItemLayout).toBe('vertical')
  })

  it('keeps an explicit side-by-side text item preference', async () => {
    mocks.fetchedTextItemLayout = 'side-by-side'
    const store = await createStore()

    await store.loadPreferences()

    expect(store.textItemLayout).toBe('side-by-side')
  })
})
