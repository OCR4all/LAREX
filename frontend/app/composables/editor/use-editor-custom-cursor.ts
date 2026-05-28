import type { IconCursorPresetName } from '@/composables/use-icon-cursor'
import { useIconCursorPreset } from '@/composables/use-icon-cursor'

export interface EditorCustomCursorState {
  actionWandActive: boolean
}

export function resolveEditorCustomCursorPreset(state: EditorCustomCursorState): IconCursorPresetName | null {
  switch (true) {
    case state.actionWandActive:
      return 'actionWand'
    default:
      return null
  }
}

export function useEditorCustomCursor(state: Readonly<Ref<EditorCustomCursorState>>) {
  const { cursor: actionWandCursor } = useIconCursorPreset('actionWand')

  const activePreset = computed(() => resolveEditorCustomCursorPreset(state.value))

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
