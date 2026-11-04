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

/**
 * Commands whose Mod/Ctrl bindings have no text-editing meaning. They keep
 * working while a text field has focus, so the browser doesn't act on the
 * keys instead (Mod+S saving the web page, Mod+1 switching browser tabs).
 */
const TYPING_SAFE_COMMANDS = new Set<ShortcutCommandId>([
  'save',
  'defaultView',
  'textlineView',
  'baselineView',
  'wordView',
  'glyphView',
  'textCanvasView',
  'textListView',
  'textFullView',
  'toggleLeftSidebar',
  'toggleRightSidebar',
  'createGtFromRecognition'
])

/** Whether a matched binding may run even while the user is typing in a field. */
export function isTypingSafeShortcut(commandId: ShortcutCommandId, binding: string): boolean {
  if (!TYPING_SAFE_COMMANDS.has(commandId)) return false
  const modifiers = binding.split('_').slice(0, -1)
  return modifiers.includes('meta') || modifiers.includes('ctrl')
}

export function shouldIgnoreGlobalShortcutForTypingTarget(
  commandId: ShortcutCommandId,
  isTextViewContext: boolean,
  event: KeyboardEvent,
  binding?: string
): boolean {
  const hasTypingTarget = isTypingTarget(event.target)
    || event.composedPath().some(isTypingTarget)
  if (!hasTypingTarget) return false
  if (binding && isTypingSafeShortcut(commandId, binding)) return false
  return !(isTextViewContext && (commandId === 'undo' || commandId === 'redo'))
}
