import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, type EffectScope } from 'vue'
import { useEditorCanvasCollaboration } from '../use-editor-canvas-collaboration'

const dependencies = vi.hoisted(() => ({
  store: { canvases: {} as Record<string, { projectId: string, pageId: string, isLoadingAnnotations: boolean }> },
  ensureCanvasRoom: vi.fn(),
  attachCanvasSession: vi.fn(),
  leaveCanvasRoom: vi.fn(),
  session: {}
}))
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => dependencies.store }))
vi.mock('@/session/editor/editor-session', () => ({ useEditorSession: () => dependencies.session }))
vi.mock('@/composables/editor/use-editor-collaboration', () => ({ useEditorCollaboration: () => dependencies }))

describe('canvas collaboration membership', () => {
  let scope: EffectScope
  beforeEach(() => {
    vi.clearAllMocks()
    dependencies.ensureCanvasRoom.mockResolvedValue({})
    dependencies.store.canvases = reactive({
      canvas: { projectId: 'project', pageId: 'page', isLoadingAnnotations: false }
    })
    scope = effectScope()
  })
  afterEach(() => scope.stop())

  it('retains takeover ownership when the page reloads annotations', async () => {
    scope.run(() => useEditorCanvasCollaboration('canvas'))
    await nextTick()
    expect(dependencies.attachCanvasSession).toHaveBeenCalledWith('canvas', dependencies.session)
    dependencies.store.canvases.canvas!.isLoadingAnnotations = true
    await nextTick()
    expect(dependencies.leaveCanvasRoom).not.toHaveBeenCalled()
    dependencies.store.canvases.canvas!.isLoadingAnnotations = false
    await nextTick()
    expect(dependencies.leaveCanvasRoom).not.toHaveBeenCalled()
  })

  it('releases ownership when the editor closes', async () => {
    scope.run(() => useEditorCanvasCollaboration('canvas'))
    await nextTick()
    scope.stop()
    expect(dependencies.leaveCanvasRoom).toHaveBeenCalledOnce()
  })

  it('does not leave when the canvas object is refreshed with the same resource', async () => {
    scope.run(() => useEditorCanvasCollaboration('canvas'))
    await nextTick()
    dependencies.store.canvases.canvas = { ...dependencies.store.canvases.canvas!, isLoadingAnnotations: true }
    await nextTick()
    expect(dependencies.leaveCanvasRoom).not.toHaveBeenCalled()
  })

  it('keeps membership until the last view of a canvas closes', async () => {
    const secondView = effectScope()
    try {
      scope.run(() => useEditorCanvasCollaboration('canvas'))
      secondView.run(() => useEditorCanvasCollaboration('canvas'))
      await nextTick()
      expect(dependencies.ensureCanvasRoom).toHaveBeenCalledOnce()
      scope.stop()
      expect(dependencies.leaveCanvasRoom).not.toHaveBeenCalled()
      secondView.stop()
      expect(dependencies.leaveCanvasRoom).toHaveBeenCalledOnce()
    } finally {
      secondView.stop()
    }
  })

  it('leaves the old page when the resource changes', async () => {
    scope.run(() => useEditorCanvasCollaboration('canvas'))
    await nextTick()
    dependencies.store.canvases.canvas!.pageId = 'another-page'
    await nextTick()
    expect(dependencies.leaveCanvasRoom).toHaveBeenCalledOnce()
    expect(dependencies.ensureCanvasRoom).toHaveBeenCalledTimes(2)
  })

  it('waits for initial annotation loading without joining and leaving', async () => {
    dependencies.store.canvases.canvas!.isLoadingAnnotations = true
    scope.run(() => useEditorCanvasCollaboration('canvas'))
    expect(dependencies.ensureCanvasRoom).not.toHaveBeenCalled()
    dependencies.store.canvases.canvas!.isLoadingAnnotations = false
    await nextTick()
    expect(dependencies.ensureCanvasRoom).toHaveBeenCalledOnce()
    expect(dependencies.leaveCanvasRoom).not.toHaveBeenCalled()
  })

  it('cancels a pending room join when the page closes', async () => {
    let finishJoin: (room: object) => void = () => {}
    dependencies.ensureCanvasRoom.mockReturnValue(new Promise((resolve) => {
      finishJoin = resolve
    }))
    scope.run(() => useEditorCanvasCollaboration('canvas'))
    const signal = dependencies.ensureCanvasRoom.mock.calls[0]?.[1] as AbortSignal
    scope.stop()
    expect(signal.aborted).toBe(true)
    finishJoin({})
    await nextTick()
    expect(dependencies.attachCanvasSession).not.toHaveBeenCalled()
  })
})
