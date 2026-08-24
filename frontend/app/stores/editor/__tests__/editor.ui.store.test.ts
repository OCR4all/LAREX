import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

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

async function createStore() {
  setActivePinia(createPinia())
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

  it('loads all preference defaults when saved values are missing', async () => {
    const store = await createStore()

    await store.loadPreferences()

    const defaults = [
      ['toolbarCompact', store.toolbarCompact, true],
      ['pageFocusMode', store.pageFocusMode, true],
      ['openRegionTypeMenuOnCreate', store.globalSettings.openRegionTypeMenuOnCreate, true],
      ['textItemLayout', store.textItemLayout, 'vertical']
    ] as const
    for (const [name, actual, expected] of defaults) {
      expect(actual, name).toBe(expected)
    }
  })

  it('loads all explicit preference values', async () => {
    mocks.fetchedToolbarCompact = false
    mocks.fetchedPageFocusMode = false
    mocks.fetchedOpenRegionTypeMenuOnCreate = false
    mocks.fetchedTextModeSubmode = 'full'
    mocks.fetchedTextItemLayout = 'side-by-side'
    const store = await createStore()

    await store.loadPreferences()

    const loadedValues = [
      ['toolbarCompact', store.toolbarCompact, false],
      ['pageFocusMode', store.pageFocusMode, false],
      ['openRegionTypeMenuOnCreate', store.globalSettings.openRegionTypeMenuOnCreate, false],
      ['textModeSubmode', store.textModeSubmode, 'full'],
      ['textItemLayout', store.textItemLayout, 'side-by-side']
    ] as const
    for (const [name, actual, expected] of loadedValues) {
      expect(actual, name).toBe(expected)
    }
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
})
