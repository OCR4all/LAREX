import type { MaybeRefOrGetter } from 'vue'
import { watchIgnorable } from '@vueuse/core'
import {
  useEditorPreferences,
  type TableSortPreference
} from '@/composables/use-editor-preferences'

function isTableSortPreference(value: unknown): value is TableSortPreference {
  if (!value || typeof value !== 'object') return false

  const candidate = value as Partial<TableSortPreference>
  return typeof candidate.column === 'string'
    && candidate.column.trim().length > 0
    && (candidate.direction === 'asc' || candidate.direction === 'desc')
}

function copySort(sort: TableSortPreference): TableSortPreference {
  return {
    column: sort.column,
    direction: sort.direction
  }
}

function areSortsEqual(a: TableSortPreference, b: TableSortPreference): boolean {
  return a.column === b.column && a.direction === b.direction
}

export function usePersistentTableSorting(
  tableIdSource: MaybeRefOrGetter<string>,
  defaultSortSource: MaybeRefOrGetter<TableSortPreference>
) {
  const editorPreferences = useEditorPreferences()
  const tableId = computed(() => toValue(tableIdSource).trim())
  const defaultSort = computed(() => copySort(toValue(defaultSortSource)))
  const sort = ref<TableSortPreference>(copySort(defaultSort.value))

  const storedSort = computed<TableSortPreference | null>(() => {
    if (!editorPreferences.initialized.value || !tableId.value) return null

    const candidate = editorPreferences.preferences.value.tableSorting?.[tableId.value]
    return isTableSortPreference(candidate) ? candidate : null
  })

  const { ignoreUpdates: ignorePreferenceSync } = watchIgnorable(sort, (nextSort) => {
    if (
      import.meta.server
        || !editorPreferences.initialized.value
        || !tableId.value
        || !isTableSortPreference(nextSort)
    ) return

    const existingTableSorting = editorPreferences.preferences.value.tableSorting ?? {}
    const existingSort = existingTableSorting[tableId.value]
    if (existingSort && areSortsEqual(existingSort, nextSort)) return

    const nextTableSorting = {
      ...existingTableSorting,
      [tableId.value]: copySort(nextSort)
    }

    // Persist immediately so a sort selection survives a fast page or browser close.
    editorPreferences.preferences.value.tableSorting = nextTableSorting
    void editorPreferences.savePreferences({ tableSorting: nextTableSorting })
  }, { deep: true })

  watch([storedSort, defaultSort], ([stored, fallback]) => {
    const nextSort = copySort(stored ?? fallback)
    if (areSortsEqual(sort.value, nextSort)) return

    ignorePreferenceSync(() => {
      sort.value = nextSort
    })
  }, { immediate: true })

  onServerPrefetch(async () => {
    if (!editorPreferences.initialized.value) {
      await editorPreferences.fetchPreferences()
    }
  })

  onMounted(async () => {
    if (!editorPreferences.initialized.value) {
      await editorPreferences.fetchPreferences()
    }
  })

  return sort
}
