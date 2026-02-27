import type { EditorSession } from '@/session/editor/editor-session'

export interface CommandContext {
  canvasId: string
  session: EditorSession
}

export interface Command<TResult = unknown> {
  execute(ctx?: CommandContext): TResult
  undo(ctx?: CommandContext): void
  getDescription(): string
}
