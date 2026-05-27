import type { VisibilityState } from '@tanstack/vue-table'
import type { MaybeRefOrGetter } from 'vue'
import { useEditorPreferences } from '@/composables/use-editor-preferences'

function normalizeColumnIds(ids: string[]): string[] {
  const seen = new Set<string>()
  const normalized: string[] = []

  for (const id of ids) {
    const trimmed = id.trim()
    if (!trimmed || seen.has(trimmed)) continue
    seen.add(trimmed)
    normalized.push(trimmed)
  }

  return normalized
}

function buildVisibilityState(columnIds: string[], source: VisibilityState | null | undefined): VisibilityState {
  return columnIds.reduce<VisibilityState>((acc, columnId) => {
    acc[columnId] = source?.[columnId] !== false
    return acc
  }, {})
}

function areVisibilityStatesEqual(a: VisibilityState | null | undefined, b: VisibilityState | null | undefined): boolean {
  if (a === b) return true

  const aKeys = Object.keys(a ?? {}).sort()
  const bKeys = Object.keys(b ?? {}).sort()
  if (aKeys.length !== bKeys.length) return false

  for (let index = 0; index < aKeys.length; index++) {
    const key = aKeys[index]
    const bKey = bKeys[index]
    if (!key || !bKey) return false
    if (key !== bKey) return false
    if ((a?.[key] ?? false) !== (b?.[key] ?? false)) return false
  }

  return true
}

export function usePersistentTableColumnVisibility(
  tableIdSource: MaybeRefOrGetter<string>,
  columnIdsSource: MaybeRefOrGetter<string[]>,
  defaultVisibleColumnIdsSource?: MaybeRefOrGetter<string[] | undefined>
) {
  const editorPreferences = useEditorPreferences()

  const columnVisibility = ref<VisibilityState>({})
  const syncingFromPreferences = ref(false)

  const tableId = computed(() => toValue(tableIdSource).trim())
  const columnIds = computed(() => normalizeColumnIds(toValue(columnIdsSource)))
  const defaultVisibleColumnIds = computed(() => {
    if (!defaultVisibleColumnIdsSource) return null
    const ids = toValue(defaultVisibleColumnIdsSource)
    return ids ? normalizeColumnIds(ids) : null
  })

  const storedVisibility = computed<VisibilityState | null>(() => {
    if (!editorPreferences.initialized.value || !tableId.value) return null

    const allTableVisibility = editorPreferences.preferences.value.tableColumnVisibility
    if (!allTableVisibility) return null

    return allTableVisibility[tableId.value] ?? null
  })

  watch([storedVisibility, columnIds, defaultVisibleColumnIds], ([stored, ids, defaultVisible]) => {
    const initialVisibility = stored ?? (defaultVisible
      ? ids.reduce<VisibilityState>((acc, columnId) => {
          acc[columnId] = defaultVisible.includes(columnId)
          return acc
        }, {})
      : null)
    const nextState = buildVisibilityState(ids, initialVisibility)
    if (areVisibilityStatesEqual(columnVisibility.value, nextState)) return

    syncingFromPreferences.value = true
    columnVisibility.value = nextState
    syncingFromPreferences.value = false
  }, { immediate: true })

  watch(columnVisibility, (nextState) => {
    if (import.meta.server || syncingFromPreferences.value || !editorPreferences.initialized.value || !tableId.value) return

    const normalizedState = buildVisibilityState(columnIds.value, nextState)
    if (!areVisibilityStatesEqual(nextState, normalizedState)) {
      syncingFromPreferences.value = true
      columnVisibility.value = normalizedState
      syncingFromPreferences.value = false
      return
    }

    const existingAllTableVisibility = editorPreferences.preferences.value.tableColumnVisibility ?? {}
    const existingForTable = existingAllTableVisibility[tableId.value] ?? null
    if (areVisibilityStatesEqual(existingForTable, normalizedState)) return

    const nextTableColumnVisibility = {
      ...existingAllTableVisibility,
      [tableId.value]: normalizedState
    }

    // Persist immediately so table visibility survives fast page reloads.
    editorPreferences.preferences.value.tableColumnVisibility = nextTableColumnVisibility
    void editorPreferences.savePreferences({ tableColumnVisibility: nextTableColumnVisibility })
  }, { deep: true })

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

  return {
    columnVisibility
  }
}
