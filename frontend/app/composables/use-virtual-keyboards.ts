import type { KeyboardLayout } from '@/types/virtual-keyboard'
import { wsKey } from '@/utils/fetch-keys'

export function useVirtualKeyboards() {
  const workspace = useWorkspaceStore()
  const uiStore = useEditorUiStore()

  const selectedWorkspaceId = computed(() => workspace.selectedWorkspaceId as string)
  const keyboardsKey = computed(() => wsKey(selectedWorkspaceId.value, 'virtual-keyboards', 'list'))

  const { data: keyboards } = useFetch<KeyboardLayout[]>(
    () => `/api/workspaces/${selectedWorkspaceId.value}/virtual-keyboards`,
    { key: keyboardsKey, default: () => [] }
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

  return {
    keyboards,
    selectedKeyboardId,
    selectedLayout
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
