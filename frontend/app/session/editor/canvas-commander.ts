import type { CommandContext } from '@/commands/editor/types'
import { Commander } from '@/commands/editor/commander'
import { getEditorSession } from '@/session/editor/editor-session'

const detachedCommanders = new Map<string, Commander>()

export function getSessionCommandContext(canvasId: string): CommandContext | undefined {
  const session = getEditorSession(canvasId)
  return session ? { canvasId, session } : undefined
}

export function getSessionCommander(canvasId: string): Commander | null {
  const sessionCommander = getEditorSession(canvasId)?.controls.value?.commander
  if (sessionCommander) return sessionCommander
  return detachedCommanders.get(canvasId) ?? null
}

export function getOrCreateSessionCommander(canvasId: string): Commander {
  const existing = getSessionCommander(canvasId)
  if (existing) return existing

  const created = new Commander()
  detachedCommanders.set(canvasId, created)
  return created
}

export function undoSessionCommand(canvasId: string): boolean {
  const ctx = getSessionCommandContext(canvasId)
  if (!ctx) return false
  return getOrCreateSessionCommander(canvasId).undo(ctx)
}

export function redoSessionCommand(canvasId: string): boolean {
  const ctx = getSessionCommandContext(canvasId)
  if (!ctx) return false
  return getOrCreateSessionCommander(canvasId).redo(ctx)
}

export function jumpSessionCommandHistory(canvasId: string, targetIndex: number): boolean {
  const ctx = getSessionCommandContext(canvasId)
  if (!ctx) return false
  return getOrCreateSessionCommander(canvasId).jumpToHistory(targetIndex, ctx)
}
