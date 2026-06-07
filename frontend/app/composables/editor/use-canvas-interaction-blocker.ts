import type { MaybeRefOrGetter } from 'vue'
import { computed, onScopeDispose, toValue, watch } from 'vue'

export function useEditorCanvasInteractionBlocker() {
  const blockCount = useState<number>('editor.canvasInteractionBlockCount', () => 0)
  const isCanvasInteractionBlocked = computed(() => blockCount.value > 0)

  function blockCanvasInteractions() {
    let released = false
    blockCount.value += 1

    return () => {
      if (released) return
      released = true
      blockCount.value = Math.max(0, blockCount.value - 1)
    }
  }

  return {
    isCanvasInteractionBlocked,
    blockCanvasInteractions
  }
}

export function useBlockEditorCanvasInteractions(active: MaybeRefOrGetter<boolean> = true) {
  const { blockCanvasInteractions } = useEditorCanvasInteractionBlocker()
  let release: (() => void) | null = null

  const stop = watch(
    () => toValue(active),
    (shouldBlock) => {
      if (shouldBlock && !release) {
        release = blockCanvasInteractions()
      } else if (!shouldBlock && release) {
        release()
        release = null
      }
    },
    { immediate: true }
  )

  onScopeDispose(() => {
    stop()
    if (release) {
      release()
      release = null
    }
  })
}
