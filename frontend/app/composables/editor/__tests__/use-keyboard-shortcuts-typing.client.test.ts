// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, onMounted, onUnmounted, ref, type App } from 'vue'
import { useKeyboardShortcuts, type KeyboardShortcutsOptions } from '../use-keyboard-shortcuts'
import { isTypingSafeShortcut, shouldIgnoreGlobalShortcutForTypingTarget } from '@/utils/editor/keyboard-shortcut-target'

// Nuxt auto-imports used by the composable.
vi.stubGlobal('useEditorPreferences', () => ({ preferences: ref({ shortcutBindings: null }) }))
vi.stubGlobal('onMounted', onMounted)
vi.stubGlobal('onUnmounted', onUnmounted)

let app: App | null = null

function mountShortcuts(callbacks: Partial<KeyboardShortcutsOptions['callbacks']>): HTMLTextAreaElement {
  const host = document.createElement('div')
  document.body.appendChild(host)

  app = createApp(defineComponent({
    setup() {
      useKeyboardShortcuts({
        canvasId: ref(null),
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
      return () => h('textarea')
    }
  }))
  app.mount(host)

  const textarea = host.querySelector('textarea')!
  textarea.focus()
  return textarea
}

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
