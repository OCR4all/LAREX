import type { ShortcutCommandId } from '@/composables/editor/shortcut-registry'

export function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tagName = target.tagName.toLowerCase()
  return (
    tagName === 'input'
    || tagName === 'textarea'
    || tagName === 'select'
    || target.isContentEditable
  )
}

export function shouldIgnoreGlobalShortcutForTypingTarget(
  commandId: ShortcutCommandId,
  isTextViewContext: boolean,
  event: KeyboardEvent
): boolean {
  const hasTypingTarget = isTypingTarget(event.target)
    || event.composedPath().some(isTypingTarget)
  if (!hasTypingTarget) return false
  return !(isTextViewContext && (commandId === 'undo' || commandId === 'redo'))
}
