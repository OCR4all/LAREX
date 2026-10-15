import { watch } from 'vue'

type Selection = readonly [string | null, string | null]

interface EditorSelectionSyncOptions {
  canvasSelection: () => Selection
  localSelection: () => Selection
  context: () => unknown
  applyCanvasSelection: (selection: Selection) => void
  publishLocalSelection: (selection: Selection) => void
}

/** Synchronize canvas and local selections without refocusing local selections. */
export function useEditorSelectionSync(options: EditorSelectionSyncOptions): void {
  let publishingLocalSelection = false

  watch(
    () => [options.canvasSelection(), options.context()] as const,
    ([selection]) => {
      if (publishingLocalSelection) return
      options.applyCanvasSelection(selection)
    },
    { flush: 'sync' }
  )

  watch(options.localSelection, (selection) => {
    // Publishing a selection set must not be interpreted as an external
    // navigation request by the synchronous canvas watcher.
    publishingLocalSelection = true
    try {
      options.publishLocalSelection(selection)
    } finally {
      publishingLocalSelection = false
    }
  }, { immediate: true })
}
