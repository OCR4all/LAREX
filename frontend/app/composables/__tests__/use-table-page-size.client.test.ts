import { computed, effectScope, nextTick, ref, type EffectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { usePersistentTablePageSize } from '../use-table-page-size'
import { useItemsPerPageModel } from '../use-items-per-page-model'

const preferences = ref<{ tablePageSizes?: Record<string, unknown> | null }>({ tablePageSizes: null })
const initialized = ref(true)
const savePreferences = vi.fn()
const fetchPreferences = vi.fn()

vi.mock('@/composables/use-editor-preferences', () => ({
  useEditorPreferences: () => ({
    preferences: computed(() => preferences.value),
    initialized: computed(() => initialized.value),
    savePreferences,
    fetchPreferences
  })
}))

describe('usePersistentTablePageSize', () => {
  let scope: EffectScope

  beforeEach(() => {
    preferences.value = { tablePageSizes: null }
    initialized.value = true
    vi.stubGlobal('onMounted', vi.fn())
    vi.stubGlobal('onServerPrefetch', vi.fn())
    savePreferences.mockReset()
    fetchPreferences.mockReset()
    scope = effectScope()
  })

  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('restores the table size without saving on initialization or navigation', () => {
    preferences.value = { tablePageSizes: { projects: 50, datasets: 25 } }
    const tableId = ref('projects')
    const size = scope.run(() => usePersistentTablePageSize(tableId))!

    expect(size.value).toBe(50)
    tableId.value = 'datasets'
    expect(size.value).toBe(25)
    tableId.value = 'new-table'
    expect(size.value).toBe(10)
    expect(savePreferences).not.toHaveBeenCalled()
  })

  it('restores preferences loaded after setup without overwriting them', () => {
    initialized.value = false
    const size = scope.run(() => usePersistentTablePageSize('projects', 25))!
    expect(size.value).toBe(25)

    size.value = 100
    expect(savePreferences).not.toHaveBeenCalled()
    preferences.value = { tablePageSizes: { projects: 50 } }
    initialized.value = true

    expect(size.value).toBe(50)
    expect(savePreferences).not.toHaveBeenCalled()
  })

  it('saves a selection immediately and preserves other table sizes', () => {
    preferences.value = { tablePageSizes: { datasets: 25 } }
    const size = scope.run(() => usePersistentTablePageSize('projects'))!
    size.value = 50

    expect(preferences.value.tablePageSizes).toEqual({ datasets: 25, projects: 50 })
    expect(savePreferences).toHaveBeenCalledExactlyOnceWith({
      tablePageSizes: { datasets: 25, projects: 50 }
    })
  })

  it.each([0, -1, 1.5, Infinity, NaN, '50', null])('falls back for invalid stored size %s', (stored) => {
    preferences.value = { tablePageSizes: { projects: stored } }
    const size = scope.run(() => usePersistentTablePageSize('projects', 25))!
    expect(size.value).toBe(25)
    expect(savePreferences).not.toHaveBeenCalled()
  })

  it('applies preference updates without saving them again', () => {
    const size = scope.run(() => usePersistentTablePageSize('projects'))!
    preferences.value = { tablePageSizes: { projects: 100 } }
    expect(size.value).toBe(100)
    expect(savePreferences).not.toHaveBeenCalled()
  })

  it('keeps the first visible item when selecting a size and restores it on a later visit', async () => {
    const size = scope.run(() => usePersistentTablePageSize('projects'))!
    const page = ref(6)
    const model = scope.run(() => useItemsPerPageModel(page, size, ref(200)))!

    model.value = { value: '25' }
    await nextTick()

    expect(page.value).toBe(3)
    expect(size.value).toBe(25)
    const restored = scope.run(() => usePersistentTablePageSize('projects'))!
    expect(restored.value).toBe(25)
    expect(savePreferences).toHaveBeenCalledTimes(1)
  })
})
