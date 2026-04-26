import { describe, expect, it } from 'vitest'
import {
  createShortcutPreferences,
  getEffectiveShortcutBindings,
  getResolvedShortcutDefinitions,
  getShortcutConflictMap,
  serializeKeyboardEventToBinding
} from '../shortcut-registry'

describe('shortcut-registry', () => {
  it('serializes keyboard events to canonical bindings', () => {
    expect(serializeKeyboardEventToBinding({
      key: 'Z',
      ctrlKey: true,
      metaKey: false,
      altKey: false,
      shiftKey: false
    }, { platform: 'other' })).toBe('meta_z')

    expect(serializeKeyboardEventToBinding({
      key: '?',
      ctrlKey: false,
      metaKey: false,
      altKey: false,
      shiftKey: true
    }, { platform: 'other' })).toBe('shift_/')

    expect(serializeKeyboardEventToBinding({
      key: 'g',
      ctrlKey: false,
      metaKey: true,
      altKey: true,
      shiftKey: false
    }, { platform: 'mac' })).toBe('meta_alt_g')
  })

  it('merges defaults with per-user overrides', () => {
    const bindings = getEffectiveShortcutBindings(createShortcutPreferences({
      redo: ['meta_y'],
      nextTextField: ['alt_n']
    }))

    expect(bindings.redo).toEqual(['meta_y'])
    expect(bindings.nextTextField).toEqual(['alt_n'])
    expect(bindings.undo).toEqual(['meta_z'])
  })

  it('does not bind Escape to global clear selection by default', () => {
    const bindings = getEffectiveShortcutBindings(null)

    expect(bindings.clearSelection).toEqual([])
    expect(bindings.blurTextField).toEqual(['escape'])
  })

  it('detects same-scope collisions but allows duplicates across scopes', () => {
    const bindings = getEffectiveShortcutBindings(createShortcutPreferences({
      redo: ['meta_z']
    }))

    const conflictMap = getShortcutConflictMap(bindings)

    expect(conflictMap.undo).toEqual(['meta_z'])
    expect(conflictMap.redo).toEqual(['meta_z'])
    expect(conflictMap.clearSelection).toBeUndefined()
    expect(conflictMap.blurTextField).toBeUndefined()
  })

  it('resolves help and tooltip bindings from overrides', () => {
    const definitions = getResolvedShortcutDefinitions(createShortcutPreferences({
      save: ['alt_s']
    }))

    expect(definitions.save.bindings).toEqual(['alt_s'])
    expect(definitions.undo.bindings).toEqual(['meta_z'])
  })
})
