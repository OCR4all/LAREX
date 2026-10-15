import type { MaybeRefOrGetter } from 'vue'
import { useEditorPreferences } from '@/composables/use-editor-preferences'

function isPageSize(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

export function usePersistentTablePageSize(
  tableIdSource: MaybeRefOrGetter<string>,
  defaultPageSize = 10
) {
  const editorPreferences = useEditorPreferences()
  const tableId = computed(() => toValue(tableIdSource).trim())
  const pageSize = ref(defaultPageSize)
  let syncingFromPreferences = false

  const storedPageSize = computed(() => {
    if (!editorPreferences.initialized.value || !tableId.value) return defaultPageSize

    const stored = editorPreferences.preferences.value.tablePageSizes?.[tableId.value]
    return isPageSize(stored) ? stored : defaultPageSize
  })

  watch([tableId, storedPageSize], ([, stored]) => {
    syncingFromPreferences = true
    pageSize.value = stored
    syncingFromPreferences = false
  }, { immediate: true, flush: 'sync' })

  watch(pageSize, (value) => {
    if (
      import.meta.server
        || syncingFromPreferences
        || !editorPreferences.initialized.value
        || !tableId.value
        || !isPageSize(value)
    ) return

    const existing = editorPreferences.preferences.value.tablePageSizes ?? {}
    if (existing[tableId.value] === value) return

    const tablePageSizes = { ...existing, [tableId.value]: value }
    editorPreferences.preferences.value.tablePageSizes = tablePageSizes
    // Save immediately so navigation does not discard a page-size selection.
    void editorPreferences.savePreferences({ tablePageSizes })
  }, { flush: 'sync' })

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

  return pageSize
}
