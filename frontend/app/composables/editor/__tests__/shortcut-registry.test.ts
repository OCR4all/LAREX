import { afterEach, describe, expect, it } from 'vitest'
import {
  createShortcutPreferences,
  getEffectiveShortcutBindings,
  getResolvedShortcutDefinitions,
  getShortcutConflictMap,
  serializeKeyboardEventToBinding
} from '../shortcut-registry'

describe('shortcut-registry', () => {
  const originalNavigator = globalThis.navigator

  afterEach(() => {
    Object.defineProperty(globalThis, 'navigator', {
      value: originalNavigator,
      configurable: true
    })
  })

  it('serializes keyboard events to canonical bindings', () => {
    expect(serializeKeyboardEventToBinding({
      key: 'Z',
      code: 'KeyZ',
      ctrlKey: true,
      metaKey: false,
      altKey: false,
      shiftKey: false
    }, { platform: 'other' })).toBe('meta_z')

    expect(serializeKeyboardEventToBinding({
      key: '?',
      code: 'Slash',
      ctrlKey: false,
      metaKey: false,
      altKey: false,
      shiftKey: true
    }, { platform: 'other' })).toBe('shift_/')

    expect(serializeKeyboardEventToBinding({
      key: 'g',
      code: 'KeyG',
      ctrlKey: false,
      metaKey: true,
      altKey: true,
      shiftKey: false
    }, { platform: 'mac' })).toBe('meta_alt_g')
  })

  it('recognizes Command undo and redo from navigator platform hints', () => {
    Object.defineProperty(globalThis, 'navigator', {
      value: {
        platform: 'MacIntel',
        userAgent: 'Mozilla/5.0'
      },
      configurable: true
    })

    expect(serializeKeyboardEventToBinding({
      key: 'z',
      code: 'KeyZ',
      ctrlKey: false,
      metaKey: true,
      altKey: false,
      shiftKey: false
    })).toBe('meta_z')

    expect(serializeKeyboardEventToBinding({
      key: 'y',
      code: 'KeyY',
      ctrlKey: false,
      metaKey: true,
      altKey: false,
      shiftKey: false
    })).toBe('meta_y')
  })

  it('keeps real Command key presses as meta bindings even when platform detection is wrong', () => {
    Object.defineProperty(globalThis, 'navigator', {
      value: {
        platform: 'Linux x86_64',
        userAgent: 'Mozilla/5.0'
      },
      configurable: true
    })

    expect(serializeKeyboardEventToBinding({
      key: 'z',
      code: 'KeyZ',
      ctrlKey: false,
      metaKey: true,
      altKey: false,
      shiftKey: false
    })).toBe('meta_z')
  })

  it('uses layout key values for modified letter shortcuts on QWERTZ keyboards', () => {
    expect(serializeKeyboardEventToBinding({
      key: 'z',
      code: 'KeyY',
      ctrlKey: false,
      metaKey: true,
      altKey: false,
      shiftKey: false
    }, { platform: 'mac' })).toBe('meta_z')

    expect(serializeKeyboardEventToBinding({
      key: 'y',
      code: 'KeyZ',
      ctrlKey: false,
      metaKey: true,
      altKey: false,
      shiftKey: false
    }, { platform: 'mac' })).toBe('meta_y')

    expect(serializeKeyboardEventToBinding({
      key: 'Z',
      code: 'KeyY',
      ctrlKey: false,
      metaKey: true,
      altKey: false,
      shiftKey: true
    }, { platform: 'mac' })).toBe('meta_shift_z')
  })

  it('uses keyboard code for modified shortcuts when the key is layout-shifted', () => {
    expect(serializeKeyboardEventToBinding({
      key: '∑',
      code: 'KeyW',
      ctrlKey: false,
      metaKey: false,
      altKey: true,
      shiftKey: false
    }, { platform: 'mac' })).toBe('alt_w')
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

  it('exposes default bindings for close-and-advance page shortcuts', () => {
    const bindings = getEffectiveShortcutBindings(null)

    expect(bindings.closeActiveTabAndNextPage).toEqual(['meta_ctrl_arrowdown'])
    expect(bindings.closeActiveTabAndPrevPage).toEqual(['meta_ctrl_arrowup'])
  })

  it('maps the six editor mode and view choices to number keys without conflicts', () => {
    const bindings = getEffectiveShortcutBindings(null)
    const conflictMap = getShortcutConflictMap(bindings)

    expect(bindings.defaultView).toEqual(['1'])
    expect(bindings.textlineView).toEqual(['2'])
    expect(bindings.baselineView).toEqual(['3'])
    expect(bindings.textCanvasView).toEqual(['4'])
    expect(bindings.textListView).toEqual(['5'])
    expect(bindings.textFullView).toEqual(['6'])
    expect(conflictMap.defaultView).toBeUndefined()
    expect(conflictMap.textlineView).toBeUndefined()
    expect(conflictMap.baselineView).toBeUndefined()
    expect(conflictMap.textCanvasView).toBeUndefined()
    expect(conflictMap.textListView).toBeUndefined()
    expect(conflictMap.textFullView).toBeUndefined()
  })
})
