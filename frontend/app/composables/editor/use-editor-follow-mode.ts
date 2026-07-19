import { readonly, ref, watch, type Ref } from 'vue'

export type EditorViewerNavigationMode = 'explore' | 'follow'

type EditorFollowModeOptions = {
  canFollow: Readonly<Ref<boolean>>
  syncKey: Readonly<Ref<string>>
  applyEditorState: () => void
}

export function useEditorFollowMode(options: EditorFollowModeOptions) {
  const mode = ref<EditorViewerNavigationMode>('explore')

  function explore() {
    mode.value = 'explore'
  }

  function applyLatestEditorState() {
    if (mode.value !== 'follow' || !options.canFollow.value) return
    options.applyEditorState()
  }

  function follow() {
    if (!options.canFollow.value) return
    mode.value = 'follow'
    applyLatestEditorState()
  }

  function handleLocalInteraction() {
    explore()
  }

  watch(options.canFollow, (canFollow) => {
    if (!canFollow) {
      explore()
    }
  })

  watch(options.syncKey, () => {
    applyLatestEditorState()
  })

  return {
    mode: readonly(mode),
    explore,
    follow,
    handleLocalInteraction,
    applyLatestEditorState
  }
}
