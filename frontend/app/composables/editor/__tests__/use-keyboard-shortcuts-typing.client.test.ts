// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, onBeforeUnmount, onMounted, onUnmounted, ref, watchEffect, type App } from 'vue'
import { useKeyboardShortcuts, useTextViewShortcutScope, type KeyboardShortcutsOptions } from '../use-keyboard-shortcuts'
import { isTypingSafeShortcut, shouldIgnoreGlobalShortcutForTypingTarget } from '@/utils/editor/keyboard-shortcut-target'

// Nuxt auto-imports used by the composable.
vi.stubGlobal('useEditorPreferences', () => ({ preferences: ref({ shortcutBindings: null }) }))
vi.stubGlobal('onMounted', onMounted)
vi.stubGlobal('onUnmounted', onUnmounted)
vi.stubGlobal('onBeforeUnmount', onBeforeUnmount)
vi.stubGlobal('ref', ref)
vi.stubGlobal('watchEffect', watchEffect)

let app: App | null = null

function mountShortcuts(
  callbacks: Partial<KeyboardShortcutsOptions['callbacks']>,
  setupScope?: () => void
): HTMLTextAreaElement {
  const host = document.createElement('div')
  document.body.appendChild(host)

  app = createApp(defineComponent({
    setup() {
      useKeyboardShortcuts({
        canvasId: ref(setupScope ? 'canvas-1' : null),
        isDrawingMode: ref(false),
        drawingMode: ref('select'),
        viewMode: ref('default'),
        selectedPolygonIds: ref([]),
        selectedPolylineIds: ref([]),
        polygons: ref([]),
        polylines: ref([]),
        selectedPolygonIndex: ref(-1),
        selectedPolylineIndex: ref(-1),
        callbacks: {
          handleUndo: vi.fn(),
          handleRedo: vi.fn(),
          setDrawingMode: vi.fn(),
          setViewMode: vi.fn(),
          selectPolygonByIndex: vi.fn(),
          selectPolylineByIndex: vi.fn(),
          clearSelection: vi.fn(),
          selectAll: vi.fn(),
          zoomIn: vi.fn(),
          zoomOut: vi.fn(),
          fitToContent: vi.fn(),
          resetView: vi.fn(),
          centerOnSelection: vi.fn(),
          ...callbacks
        }
      })
      setupScope?.()
      return () => h('textarea')
    }
  }))
  app.mount(host)

  const textarea = host.querySelector('textarea')!
  textarea.focus()
  return textarea
}

function pressCreateGt(target: HTMLElement): KeyboardEvent {
  // macOS reports Option+G as the composed character.
  const event = new KeyboardEvent('keydown', {
    key: '©',
    code: 'KeyG',
    metaKey: true,
    altKey: true,
    bubbles: true,
    cancelable: true
  })
  target.dispatchEvent(event)
  return event
}

function mountTextViewScope(createGtFromRecognition: () => boolean) {
  useTextViewShortcutScope({
    canvasId: ref('canvas-1'),
    rootEl: ref(document.createElement('div')),
    handlers: {
      nextTextField: () => false,
      nextTextlineGtField: () => false,
      prevTextlineGtField: () => false,
      prevTextField: () => false,
      blurTextField: () => false,
      nextSameIndexField: () => false,
      createGtFromRecognition
    }
  })
}

describe('create GT shortcut', () => {
  afterEach(() => {
    app?.unmount()
    app = null
    document.body.replaceChildren()
  })

  it('runs the canvas command from its textarea', () => {
    const createGtFromRecognition = vi.fn(() => true)
    const textarea = mountShortcuts({ createGtFromRecognition })

    expect(pressCreateGt(textarea).defaultPrevented).toBe(true)
    expect(createGtFromRecognition).toHaveBeenCalledOnce()
  })

  it('reaches the text list when focus is outside the list', () => {
    const listHandler = vi.fn(() => true)
    mountShortcuts({ createGtFromRecognition: () => false }, () => mountTextViewScope(listHandler))
    const outside = document.createElement('button')
    document.body.appendChild(outside)
    outside.focus()

    expect(pressCreateGt(outside).defaultPrevented).toBe(true)
    expect(listHandler).toHaveBeenCalledOnce()
  })

  it('does not run the text list when the canvas handled the command', () => {
    const listHandler = vi.fn(() => true)
    const textarea = mountShortcuts({ createGtFromRecognition: () => true }, () => mountTextViewScope(listHandler))

    pressCreateGt(textarea)

    expect(listHandler).not.toHaveBeenCalled()
  })
})

function pressCtrl(target: HTMLElement, key: string, code: string): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key, code, ctrlKey: true, bubbles: true, cancelable: true })
  target.dispatchEvent(event)
  return event
}

afterEach(() => {
  app?.unmount()
  app = null
  document.body.innerHTML = ''
})

describe('editor shortcuts while a text field has focus', () => {
  it('saves with Mod+S instead of letting the browser save the page', () => {
    const saveDocument = vi.fn()
    const textarea = mountShortcuts({ saveDocument })

    const event = pressCtrl(textarea, 's', 'KeyS')

    expect(saveDocument).toHaveBeenCalledOnce()
    expect(event.defaultPrevented).toBe(true)
  })

  it('switches views with Mod+number instead of switching browser tabs', () => {
    const setLayoutViewMode = vi.fn()
    const setTextViewMode = vi.fn()
    const textarea = mountShortcuts({ setLayoutViewMode, setTextViewMode })

    const layoutEvent = pressCtrl(textarea, '3', 'Digit3')
    const textEvent = pressCtrl(textarea, '7', 'Digit7')

    expect(setLayoutViewMode).toHaveBeenCalledWith('baseline')
    expect(setTextViewMode).toHaveBeenCalledWith('expert')
    expect(layoutEvent.defaultPrevented).toBe(true)
    expect(textEvent.defaultPrevented).toBe(true)
  })

  it('leaves text-editing Mod shortcuts to the field', () => {
    const selectAll = vi.fn()
    const textarea = mountShortcuts({ selectAll })

    const event = pressCtrl(textarea, 'a', 'KeyA')

    expect(selectAll).not.toHaveBeenCalled()
    expect(event.defaultPrevented).toBe(false)
  })
})

describe('typing-safe shortcut guard', () => {
  function keyboardEvent(target: EventTarget): KeyboardEvent {
    return { target, composedPath: () => [target] } as unknown as KeyboardEvent
  }

  it('only lets Mod/Ctrl bindings of typing-safe commands through', () => {
    const textarea = document.createElement('textarea')

    expect(isTypingSafeShortcut('save', 'meta_s')).toBe(true)
    expect(isTypingSafeShortcut('textlineView', 'ctrl_2')).toBe(true)
    expect(isTypingSafeShortcut('textlineView', '2')).toBe(false)
    expect(isTypingSafeShortcut('selectAll', 'meta_a')).toBe(false)
    expect(shouldIgnoreGlobalShortcutForTypingTarget('save', false, keyboardEvent(textarea), 'meta_s')).toBe(false)
    expect(shouldIgnoreGlobalShortcutForTypingTarget('textlineView', false, keyboardEvent(textarea), '2')).toBe(true)
  })
})
