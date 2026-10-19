import { computed, effectScope, onScopeDispose, watch, type EffectScope } from 'vue'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSession } from '@/session/editor/editor-session'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'

const canvasMemberships = new Map<string, { scope: EffectScope, hosts: number }>()

/** Keep room membership until the canvas closes or its annotation resource changes. */
export function useEditorCanvasCollaboration(canvasId: string) {
  if (import.meta.server) return

  let membership = canvasMemberships.get(canvasId)
  if (!membership) {
    membership = { scope: effectScope(true), hosts: 0 }
    canvasMemberships.set(canvasId, membership)
  }
  const currentMembership = membership
  currentMembership.hosts++
  onScopeDispose(() => {
    if (--currentMembership.hosts > 0) return
    currentMembership.scope.stop()
    canvasMemberships.delete(canvasId)
  })
  if (currentMembership.hosts > 1) return

  const editorStore = useEditorStore()
  const collaboration = useEditorCollaboration()
  const session = useEditorSession(canvasId)
  const canvas = computed(() => editorStore.canvases[canvasId] ?? null)
  const loading = computed(() => canvas.value?.isLoadingAnnotations ?? false)

  currentMembership.scope.run(() => {
    watch(
      [
        () => canvas.value?.projectId,
        () => canvas.value?.pageId,
        () => canvas.value?.xmlFileId,
        () => canvas.value?.annotationContext?.basePath
      ],
      ([projectId, pageId], _previous, onLeave) => {
        if (!projectId || !pageId) return
        let attached = false
        const stopLoadingWatch = watch(loading, (isLoading, _previousLoading, onRefresh) => {
          if (isLoading || attached) return
          const controller = new AbortController()
          onRefresh(() => controller.abort())
          void collaboration.ensureCanvasRoom(canvasId, controller.signal)
            .then((room) => {
              if (room && !controller.signal.aborted) {
                collaboration.attachCanvasSession(canvasId, session)
                attached = true
              }
            })
            .catch((error) => {
              if (!controller.signal.aborted) console.error('[editor-collaboration] Failed to join room:', error)
            })
        }, { immediate: true })
        onLeave(() => {
          stopLoadingWatch()
          collaboration.leaveCanvasRoom(canvasId)
        })
      },
      { immediate: true }
    )
  })
}
