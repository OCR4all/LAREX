import type { KeyboardLayout, KeyboardItem } from '@/types/virtual-keyboard'

export function useVirtualKeyboardBuilder(initialLayout: KeyboardLayout) {
  const layoutId = ref(initialLayout.id)
  const gridCols = ref(initialLayout.cols)
  const gridRows = ref(initialLayout.rows)
  const items = ref<KeyboardItem[]>([...initialLayout.items])

  const layoutName = ref(initialLayout.name || '')
  const layoutDesc = ref(initialLayout.description || '')
  const tags = ref<string[]>([...(initialLayout.tags ?? [])])
  const themeId = ref<string | undefined>(initialLayout.themeId)

  const currentLayout = computed(() => ({
    ...initialLayout,
    id: layoutId.value,
    name: layoutName.value,
    description: layoutDesc.value,
    tags: tags.value.map(t => t.trim()).filter(t => t),
    cols: gridCols.value,
    rows: gridRows.value,
    themeId: themeId.value,
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

  const updateFromImport = (json: any) => {
    if (json.cols && json.rows && json.items) {
      if (json.id) layoutId.value = json.id
      gridCols.value = json.cols
      gridRows.value = json.rows
      items.value = json.items
      layoutName.value = json.name || ''
      layoutDesc.value = json.description || ''
      tags.value = Array.isArray(json.tags) ? json.tags : []
      themeId.value = json.themeId
    }
  }

  return {
    layoutId,
    gridCols,
    gridRows,
    items,
    layoutName,
    layoutDesc,
    tags,
    themeId,
    currentLayout,
    changeGridCols,
    changeGridRows,
    updateFromImport
  }
}

export type VirtualKeyboardBuilderState = ReturnType<typeof useVirtualKeyboardBuilder>
