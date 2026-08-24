import type { IconCursorPresetName } from '@/composables/use-icon-cursor'
import { useIconCursorPreset } from '@/composables/use-icon-cursor'

export interface EditorCustomCursorState {
  actionWandActive: boolean
}

export function useEditorCustomCursor(state: Readonly<Ref<EditorCustomCursorState>>) {
  const { cursor: actionWandCursor } = useIconCursorPreset('actionWand')

  const activePreset = computed<IconCursorPresetName | null>(() =>
    state.value.actionWandActive ? 'actionWand' : null
  )

  const activeCursor = computed(() => {
    switch (activePreset.value) {
      case 'actionWand':
        return actionWandCursor.value
      default:
        return null
    }
  })

  return {
    activeCursor,
    activePreset
  }
}
