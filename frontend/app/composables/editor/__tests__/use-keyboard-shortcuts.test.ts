// @vitest-environment happy-dom

import { describe, expect, it } from 'vitest'
import { shouldIgnoreGlobalShortcutForTypingTarget } from '@/utils/editor/keyboard-shortcut-target'

describe('global shortcuts while typing', () => {
  function keyboardEvent(target: EventTarget, path: EventTarget[] = [target]): KeyboardEvent {
    return {
      target,
      composedPath: () => path
    } as unknown as KeyboardEvent
  }

  it.each(['merge', 'delete', 'centerOnSelection'] as const)(
    'does not capture %s from a textarea',
    (commandId) => {
      const textarea = document.createElement('textarea')

      expect(shouldIgnoreGlobalShortcutForTypingTarget(commandId, false, keyboardEvent(textarea))).toBe(true)
    }
  )

  it('still permits editor undo in a registered text-view scope', () => {
    const textarea = document.createElement('textarea')

    expect(shouldIgnoreGlobalShortcutForTypingTarget('undo', true, keyboardEvent(textarea))).toBe(false)
  })

  it('recognizes a textarea in the composed event path', () => {
    const host = document.createElement('div')
    const textarea = document.createElement('textarea')

    expect(
      shouldIgnoreGlobalShortcutForTypingTarget(
        'merge',
        false,
        keyboardEvent(host, [textarea, host, document, window])
      )
    ).toBe(true)
  })

  it('does not suppress shortcuts outside text-entry controls', () => {
    const button = document.createElement('button')

    expect(shouldIgnoreGlobalShortcutForTypingTarget('merge', false, keyboardEvent(button))).toBe(false)
  })
})
