import type { KeyboardLayout, KeyboardItem } from '@/types/virtual-keyboard'

export function useVirtualKeyboardBuilder(initialLayout: KeyboardLayout) {
  const layoutId = ref(initialLayout.id)
  const gridCols = ref(initialLayout.cols)
  const gridRows = ref(initialLayout.rows)
  const items = ref<KeyboardItem[]>([...initialLayout.items])

  const layoutName = ref(initialLayout.name || '')
  const layoutDesc = ref(initialLayout.description || '')
  const tags = ref<string[]>([...(initialLayout.tags ?? [])])

  const currentLayout = computed(() => ({
    ...initialLayout,
    id: layoutId.value,
    name: layoutName.value,
    description: layoutDesc.value,
    tags: tags.value.map(t => t.trim()).filter(t => t),
    cols: gridCols.value,
    rows: gridRows.value,
    items: items.value
  }))

  const changeGridCols = (delta: number) => {
    const newVal = gridCols.value + delta
    if (newVal >= 1 && newVal <= 30) {
      if (delta < 0) {
        items.value = items.value.filter(item => (item.x + item.w) <= newVal)
      }
      gridCols.value = newVal
    }
  }

  const changeGridRows = (delta: number) => {
    const newVal = gridRows.value + delta
    if (newVal >= 1 && newVal <= 10) {
      if (delta < 0) {
        items.value = items.value.filter(item => item.y < newVal)
      }
      gridRows.value = newVal
    }
  }

  const updateFromImport = (json: unknown) => {
    if (!json || typeof json !== 'object') return

    const payload = json as Partial<KeyboardLayout> & { items?: KeyboardItem[] }
    if (typeof payload.cols !== 'number' || typeof payload.rows !== 'number' || !Array.isArray(payload.items)) return

    if (typeof payload.id === 'string' && payload.id) layoutId.value = payload.id
    gridCols.value = payload.cols
    gridRows.value = payload.rows
    items.value = payload.items
    layoutName.value = payload.name || ''
    layoutDesc.value = payload.description || ''
    tags.value = Array.isArray(payload.tags) ? payload.tags : []
  }

  return {
    layoutId,
    gridCols,
    gridRows,
    items,
    layoutName,
    layoutDesc,
    tags,
    currentLayout,
    changeGridCols,
    changeGridRows,
    updateFromImport
  }
}

export type VirtualKeyboardBuilderState = ReturnType<typeof useVirtualKeyboardBuilder>
