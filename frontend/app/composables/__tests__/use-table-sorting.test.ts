import {
  computed,
  nextTick,
  onMounted,
  onServerPrefetch,
  ref,
  toValue,
  watch
} from 'vue'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

const preferences = ref<{
  tableSorting: Record<string, { column: string, direction: 'asc' | 'desc' }> | null
}>({
  tableSorting: null
})
const savePreferences = vi.fn()

vi.mock('@/composables/use-editor-preferences', () => ({
  useEditorPreferences: () => ({
    preferences: computed(() => preferences.value),
    initialized: ref(true),
    fetchPreferences: vi.fn(),
    savePreferences
  })
}))

describe('usePersistentTableSorting', () => {
  beforeAll(() => {
    vi.stubGlobal('computed', computed)
    vi.stubGlobal('onMounted', onMounted)
    vi.stubGlobal('onServerPrefetch', onServerPrefetch)
    vi.stubGlobal('ref', ref)
    vi.stubGlobal('toValue', toValue)
    vi.stubGlobal('watch', watch)
  })

  beforeEach(() => {
    preferences.value = { tableSorting: null }
    savePreferences.mockReset()
  })

  it('restores sorting for the table type', async () => {
    preferences.value.tableSorting = {
      'project-pages-v2': { column: 'name', direction: 'desc' }
    }
    const { usePersistentTableSorting } = await import('../use-table-sorting')

    const sort = usePersistentTableSorting(
      'project-pages-v2',
      { column: 'projectOrderPosition', direction: 'asc' }
    )

    expect(sort.value).toEqual({ column: 'name', direction: 'desc' })
    expect(savePreferences).not.toHaveBeenCalled()
  })

  it('persists changed sorting without replacing other table types', async () => {
    preferences.value.tableSorting = {
      'workspace-datasets': { column: 'updated', direction: 'desc' }
    }
    const { usePersistentTableSorting } = await import('../use-table-sorting')
    const sort = usePersistentTableSorting(
      'project-pages-v2',
      { column: 'projectOrderPosition', direction: 'asc' }
    )

    sort.value = { column: 'name', direction: 'asc' }
    await nextTick()

    expect(preferences.value.tableSorting).toEqual({
      'workspace-datasets': { column: 'updated', direction: 'desc' },
      'project-pages-v2': { column: 'name', direction: 'asc' }
    })
    expect(savePreferences).toHaveBeenCalledWith({
      tableSorting: preferences.value.tableSorting
    })
  })
})
