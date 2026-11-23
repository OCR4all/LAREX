import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Commander } from '@/commands/editor/commander'
import type { Command } from '@/commands/editor/types'
import { getEditorSession } from '@/session/editor/editor-session'
import { showEditorDeleteUndoToast } from '../use-editor-delete-undo-toast'

vi.mock('@/session/editor/editor-session', () => ({ getEditorSession: vi.fn() }))

type ToastInput = { title: string, color?: string, actions?: { onClick: () => void }[] }

function fakeCommand(log: string[], name: string): Command {
  return {
    execute: () => { log.push(`do ${name}`) },
    undo: () => { log.push(`undo ${name}`) },
    getDescription: () => name
  }
}

let toasts: ToastInput[]
const session = { id: 'session' }

beforeEach(() => {
  toasts = []
  vi.stubGlobal('useToast', () => ({ add: (toast: ToastInput) => toasts.push(toast) }))
  vi.mocked(getEditorSession).mockReturnValue(session as unknown as ReturnType<typeof getEditorSession>)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function clickUndo(index = 0): void {
  toasts[index]?.actions?.[0]?.onClick()
}

describe('showEditorDeleteUndoToast', () => {
  it('undoes the delete command when it is still the latest one', () => {
    const log: string[] = []
    const commander = new Commander()
    const command = fakeCommand(log, 'delete')
    commander.execute(command)
    const onUndone = vi.fn()

    showEditorDeleteUndoToast({ title: 'Deleted', commander, command, canvasId: 'c', onUndone })
    clickUndo()
    clickUndo()

    expect(log).toEqual(['do delete', 'undo delete'])
    expect(onUndone).toHaveBeenCalledOnce()
  })

  it('leaves newer edits alone and warns instead', () => {
    const log: string[] = []
    const commander = new Commander()
    const command = fakeCommand(log, 'delete')
    commander.execute(command)
    showEditorDeleteUndoToast({ title: 'Deleted', commander, command, canvasId: 'c' })
    commander.execute(fakeCommand(log, 'move'))

    clickUndo()

    expect(log).toEqual(['do delete', 'do move'])
    expect(toasts[1]).toMatchObject({ title: 'Could not undo the deletion', color: 'warning' })
  })

  it('does nothing once the editor session was replaced', () => {
    const log: string[] = []
    const commander = new Commander()
    const command = fakeCommand(log, 'delete')
    commander.execute(command)
    showEditorDeleteUndoToast({ title: 'Deleted', commander, command, canvasId: 'c' })
    vi.mocked(getEditorSession).mockReturnValue({ id: 'other' } as unknown as ReturnType<typeof getEditorSession>)

    clickUndo()

    expect(log).toEqual(['do delete'])
    expect(toasts).toHaveLength(1)
  })
})
