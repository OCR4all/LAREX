import type { KeyboardLayout, BoardTheme } from '@/types/virtual-keyboard'
import { wsKey } from '@/utils/fetch-keys'

const defaultTheme: BoardTheme = {
  name: 'Dark',
  bgClass: 'bg-neutral-900',
  borderClass: 'border-neutral-700',
  gridLineClass: 'border-neutral-800',
  keyBgClass: 'bg-neutral-800',
  keyTextClass: 'text-neutral-200',
  previewClass: 'bg-neutral-900'
}

export function useVirtualKeyboards() {
  const workspace = useWorkspaceStore()
  const uiStore = useEditorUiStore()

  const selectedWorkspaceId = computed(() => workspace.selectedWorkspaceId as string)
  const keyboardsKey = computed(() => wsKey(selectedWorkspaceId.value, 'virtual-keyboards', 'list'))
  const themesKey = computed(() => wsKey(selectedWorkspaceId.value, 'board-themes', 'list'))

  const { data: keyboards } = useFetch<KeyboardLayout[]>(
    () => `/api/workspaces/${selectedWorkspaceId.value}/virtual-keyboards`,
    { key: keyboardsKey, default: () => [] }
  )

  const { data: themes } = useFetch<BoardTheme[]>(
    () => `/api/workspaces/${selectedWorkspaceId.value}/board-themes`,
    { key: themesKey, default: () => [] }
  )

  const selectedKeyboardId = computed({
    get: () => uiStore.selectedVirtualKeyboardId,
    set: id => uiStore.setSelectedVirtualKeyboardId(id)
  })

  const selectedLayout = computed(() => {
    const list = keyboards.value ?? []
    if (list.length === 0) return null
    const found = list.find(k => k.id === selectedKeyboardId.value)
    if (found) return found
    if (!selectedKeyboardId.value && list[0]) {
      selectedKeyboardId.value = list[0].id
      return list[0]
    }
    return list[0] ?? null
  })

  const selectedTheme = computed(() => {
    const themeId = selectedLayout.value?.themeId
    if (themeId) {
      const found = (themes.value ?? []).find(t => t.id === themeId)
      if (found) return found
    }
    return (themes.value ?? [])[0] ?? defaultTheme
  })

  return {
    keyboards,
    themes,
    selectedKeyboardId,
    selectedLayout,
    selectedTheme
  }
}

export function useVirtualKeyboardAvailability() {
  const workspace = useWorkspaceStore()

  const selectedWorkspaceId = computed(() => workspace.selectedWorkspaceId as string)
  const keyboardsKey = computed(() => wsKey(selectedWorkspaceId.value, 'virtual-keyboards', 'list'))

  const { data: keyboards } = useFetch<KeyboardLayout[]>(
    () => `/api/workspaces/${selectedWorkspaceId.value}/virtual-keyboards`,
    { key: keyboardsKey, default: () => [] }
  )

  const hasKeyboards = computed(() => (keyboards.value ?? []).length > 0)

  return {
    hasKeyboards,
    keyboards
  }
}
