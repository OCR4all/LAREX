import { describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useEditorSelectionSync } from '../use-editor-selection-sync'

function createHarness(kind: 'polygon' | 'baseline') {
  const selectedIds = ref<string[]>([])
  const focusedId = ref<string | null>(null)
  const canvasId = ref<string | null>(null)
  const context = ref('page-a')
  const scope = effectScope()
  const applySelection = vi.fn((id: string | null) => {
    focusedId.value = id
    selectedIds.value = id ? [id] : []
  })
  scope.run(() => useEditorSelectionSync({
    canvasSelection: () => kind === 'polygon' ? [canvasId.value, null] : [null, canvasId.value],
    localSelection: () => {
      const id = selectedIds.value.length > 1
        ? null
        : selectedIds.value[0] ?? focusedId.value
      return kind === 'polygon' ? [id, null] : [null, id]
    },
    context: () => context.value,
    applyCanvasSelection: ([polygonId, baselineId]) => applySelection(polygonId ?? baselineId),
    publishLocalSelection: ([polygonId, baselineId]) => {
      canvasId.value = polygonId ?? baselineId
    }
  }))
  return { scope, selectedIds, focusedId, canvasId, context, applySelection }
}

describe('editor selection synchronization', () => {
  it.each(['polygon', 'baseline'] as const)('keeps Shift-click %s selection from changing focus', async (kind) => {
    const harness = createHarness(kind)
    try {
      harness.selectedIds.value = ['a']
      await nextTick()
      expect(harness.canvasId.value).toBe('a')
      expect(harness.focusedId.value).toBeNull()
      expect(harness.applySelection).not.toHaveBeenCalled()

      harness.selectedIds.value = ['a', 'b']
      await nextTick()
      expect(harness.selectedIds.value).toEqual(['a', 'b'])
      expect(harness.focusedId.value).toBeNull()

      harness.selectedIds.value = ['b']
      await nextTick()
      expect(harness.focusedId.value).toBeNull()
      expect(harness.selectedIds.value).toEqual(['b'])
    } finally {
      harness.scope.stop()
    }
  })

  it('preserves the drilled-in parent when selecting one child', async () => {
    const harness = createHarness('polygon')
    try {
      harness.focusedId.value = 'parent'
      await nextTick()
      harness.selectedIds.value = ['child']
      await nextTick()
      expect(harness.focusedId.value).toBe('parent')
      expect(harness.selectedIds.value).toEqual(['child'])
    } finally {
      harness.scope.stop()
    }
  })

  it('still applies external selections and context refreshes', () => {
    const harness = createHarness('polygon')
    try {
      harness.canvasId.value = 'external'
      expect(harness.focusedId.value).toBe('external')
      expect(harness.selectedIds.value).toEqual(['external'])
      harness.context.value = 'page-b'
      expect(harness.applySelection).toHaveBeenCalledTimes(2)
    } finally {
      harness.scope.stop()
    }
  })
})
