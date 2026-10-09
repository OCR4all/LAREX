import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { usePageMove } from '../use-page-move'
import type { PageMovePreview } from '@/types/page-move'

function plan(overrides: Partial<PageMovePreview> = {}): PageMovePreview {
  return {
    items: [{ pageId: 'page', sourceName: '001', resultingName: '001', outcome: 'MOVE', overwrittenPageId: null, blockers: [] }],
    blockers: [], movedCount: 1, skippedCount: 0, overwrittenCount: 0, renamedCount: 0, fingerprint: 'reviewed',
    ...overrides
  }
}

const scopes: ReturnType<typeof effectScope>[] = []
function setup() {
  vi.useFakeTimers()
  const transport = { preview: vi.fn().mockResolvedValue(plan()), move: vi.fn().mockResolvedValue(plan()) }
  const scope = effectScope()
  scopes.push(scope)
  const state = scope.run(() => usePageMove(['page'], transport))!
  return { state, transport, scope }
}

afterEach(() => {
  scopes.splice(0).forEach(scope => scope.stop())
  vi.useRealTimers()
})

describe('page move workflow', () => {
  it('defaults to skip, debounces preview, and immediately invalidates changed settings', async () => {
    const { state, transport } = setup()
    expect(state.conflictPolicy.value).toBe('SKIP')
    expect(state.canMove.value).toBe(false)
    state.destinationProjectId.value = 'target'
    await vi.advanceTimersByTimeAsync(250)
    expect(state.canMove.value).toBe(true)
    state.conflictPolicy.value = 'RENAME'
    expect(state.canMove.value).toBe(false)
    expect(state.preview.value).toBeNull()
    state.prefix.value = 'pre-'
    state.suffix.value = '-post'
    await vi.advanceTimersByTimeAsync(250)
    expect(transport.preview).toHaveBeenLastCalledWith({
      pageIds: ['page'], destinationProjectId: 'target', conflictPolicy: 'RENAME', prefix: 'pre-', suffix: '-post'
    })
  })

  it('ignores old previews that arrive after a different destination was selected', async () => {
    const { state, transport } = setup()
    let resolveOld!: (value: PageMovePreview) => void
    transport.preview.mockImplementationOnce(() => new Promise((resolve) => {
      resolveOld = resolve
    }))
    state.destinationProjectId.value = 'old'
    await vi.advanceTimersByTimeAsync(250)
    state.destinationProjectId.value = 'new'
    await vi.advanceTimersByTimeAsync(250)
    resolveOld(plan({ fingerprint: 'old-preview' }))
    await Promise.resolve()
    expect(state.preview.value?.fingerprint).toBe('reviewed')
  })

  it('blocks batches with global blockers, page blockers, or zero movable pages', async () => {
    const { state, transport } = setup()
    state.destinationProjectId.value = 'target'
    for (const blocked of [
      plan({ blockers: ['Project locked'] }),
      plan({ items: [{ ...plan().items[0]!, blockers: ['Rename collision'] }] }),
      plan({ movedCount: 0, skippedCount: 1 })
    ]) {
      transport.preview.mockResolvedValueOnce(blocked)
      await state.refreshPreview()
      expect(state.canMove.value).toBe(false)
      expect(await state.move()).toBeNull()
    }
    expect(transport.move).not.toHaveBeenCalled()
  })

  it('submits only the reviewed fingerprint and prevents duplicate submission', async () => {
    const { state, transport } = setup()
    state.destinationProjectId.value = 'target'
    await vi.advanceTimersByTimeAsync(250)
    let complete!: (value: PageMovePreview) => void
    transport.move.mockImplementationOnce(() => new Promise((resolve) => {
      complete = resolve
    }))
    const pending = state.move()
    expect(state.moving.value).toBe(true)
    expect(await state.move()).toBeNull()
    expect(transport.move).toHaveBeenCalledExactlyOnceWith({
      pageIds: ['page'], destinationProjectId: 'target', conflictPolicy: 'SKIP', prefix: '', suffix: '', fingerprint: 'reviewed'
    })
    complete(plan())
    expect(await pending).toMatchObject({ destinationProjectId: 'target', movedCount: 1 })
    expect(state.moving.value).toBe(false)
  })

  it('shows backend conflict messages and refreshes the preview after failure', async () => {
    const { state, transport } = setup()
    state.destinationProjectId.value = 'target'
    await vi.advanceTimersByTimeAsync(250)
    transport.move.mockRejectedValueOnce({ data: { message: 'Preview changed. Review again.' } })
    transport.preview.mockResolvedValueOnce(plan({ fingerprint: 'fresh' }))
    expect(await state.move()).toBeNull()
    expect(state.error.value).toBe('Preview changed. Review again.')
    expect(state.preview.value?.fingerprint).toBe('fresh')
    expect(state.canMove.value).toBe(true)
  })

  it('clears a preview error after a successful manual retry', async () => {
    const { state, transport } = setup()
    transport.preview.mockRejectedValueOnce({ data: { message: 'Temporarily unavailable' } })
    state.destinationProjectId.value = 'target'
    await vi.advanceTimersByTimeAsync(250)
    expect(state.error.value).toBe('Temporarily unavailable')
    await state.refreshPreview()
    expect(state.error.value).toBe('')
    expect(state.canMove.value).toBe(true)
  })

  it('keeps preview failures visible and cancels scheduled work on close', async () => {
    const { state, transport, scope } = setup()
    transport.preview.mockRejectedValueOnce({ data: { message: 'Access denied' } })
    state.destinationProjectId.value = 'target'
    await vi.advanceTimersByTimeAsync(250)
    expect(state.error.value).toBe('Access denied')
    expect(state.previewPending.value).toBe(false)
    expect(state.canMove.value).toBe(false)
    state.destinationProjectId.value = 'another'
    scope.stop()
    await vi.advanceTimersByTimeAsync(250)
    expect(transport.preview).toHaveBeenCalledTimes(1)
  })
})
