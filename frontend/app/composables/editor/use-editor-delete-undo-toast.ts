import type { Commander } from '@/commands/editor/commander'
import type { Command } from '@/commands/editor/types'
import { getEditorSession } from '@/session/editor/editor-session'
import { useUndoToast } from '@/composables/use-undo-toast'

interface EditorDeleteUndoToastOptions {
  title: string
  description?: string
  commander: Commander
  command: Command
  canvasId: string
  onUndone?: () => void
}

export function showEditorDeleteUndoToast(options: EditorDeleteUndoToastOptions): void {
  const session = getEditorSession(options.canvasId)
  if (!session) return

  useUndoToast()({
    title: options.title,
    description: options.description,
    onUndo: () => {
      if (getEditorSession(options.canvasId) !== session) return
      if (!options.commander.isLatest(options.command)) {
        useToast().add({
          title: 'Could not undo the deletion',
          description: 'Other edits were made after it. Use Undo in the toolbar or the edit history instead.',
          color: 'warning'
        })
        return
      }
      if (options.commander.undo({ canvasId: options.canvasId, session })) options.onUndone?.()
    }
  })
}
