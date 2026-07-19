import { computed, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useEditorFollowMode } from '../use-editor-follow-mode'

describe('useEditorFollowMode', () => {
  it('starts in explore and applies the current editor state when follow is enabled', () => {
    const editorId = ref<string | null>('editor-1')
    const canEdit = ref(false)
    const applyEditorState = vi.fn()
    const navigation = useEditorFollowMode({
      canFollow: computed(() => !canEdit.value && Boolean(editorId.value)),
      syncKey: computed(() => editorId.value ?? ''),
      applyEditorState
    })

    expect(navigation.mode.value).toBe('explore')

    navigation.follow()

    expect(navigation.mode.value).toBe('follow')
    expect(applyEditorState).toHaveBeenCalledOnce()
  })

  it('continues following across an editor handoff and applies the new state', async () => {
    const editorId = ref<string | null>('editor-1')
    const applyEditorState = vi.fn()
    const navigation = useEditorFollowMode({
      canFollow: computed(() => Boolean(editorId.value)),
      syncKey: computed(() => editorId.value ?? ''),
      applyEditorState
    })
    navigation.follow()
    applyEditorState.mockClear()

    editorId.value = 'editor-2'
    await nextTick()

    expect(navigation.mode.value).toBe('follow')
    expect(applyEditorState).toHaveBeenCalledOnce()
  })

  it('returns to explore when the editor disappears or the viewer gains edit access', async () => {
    const editorId = ref<string | null>('editor-1')
    const canEdit = ref(false)
    const navigation = useEditorFollowMode({
      canFollow: computed(() => !canEdit.value && Boolean(editorId.value)),
      syncKey: computed(() => `${editorId.value}:${canEdit.value}`),
      applyEditorState: vi.fn()
    })

    navigation.follow()
    editorId.value = null
    await nextTick()
    expect(navigation.mode.value).toBe('explore')

    editorId.value = 'editor-2'
    await nextTick()
    navigation.follow()
    canEdit.value = true
    await nextTick()
    expect(navigation.mode.value).toBe('explore')
  })

  it('returns to explore on local interaction', () => {
    const navigation = useEditorFollowMode({
      canFollow: ref(true),
      syncKey: ref('editor-1'),
      applyEditorState: vi.fn()
    })
    navigation.follow()

    navigation.handleLocalInteraction()

    expect(navigation.mode.value).toBe('explore')
  })
})
