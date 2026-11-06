// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import { useDebounceFn } from '@vueuse/core'
import { createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import { TextContentVariant, TextLine } from '@/models/editor/text'
import { Polygon } from '@/models/editor/geometry'
import type { EditorCanvasControls } from '@/types/editor/canvas-controls'
import * as shortcuts from '@/composables/editor/use-keyboard-shortcuts'
import * as sessions from '@/session/editor/editor-session'
import { redoSessionCommand, undoSessionCommand } from '@/session/editor/canvas-commander'
import { Commander } from '@/commands/editor/commander'
import * as pagePrimitives from '@/utils/editor/pcgts-editor-primitives'
import * as navigation from '@/utils/editor/textline-navigation'
import * as textVariants from '@/utils/editor/text-variants'
import * as inputGuards from '../shared/text-input-guards'
import * as readingDirection from '../line/reading-direction'
import * as fullTextLines from '../shared/full-text-lines'
import * as runtime from '../shared/text-view-runtime'
import * as variantUpdate from '../shared/textline-variant-update'
import * as comments from '../shared/full-text-comments'
import source from '../full-editor.vue?raw'

const canvasId = 'full-text-history'
const passthrough = Vue.defineComponent({
  inheritAttrs: false,
  setup: (_, { slots }) => () => slots.default?.()
})
const modules: Record<string, unknown> = {
  'vue': Vue,
  '@vueuse/core': { useDebounceFn },
  'reka-ui': { TooltipProvider: passthrough },
  '#components': {},
  '@/commands/editor': {},
  '@/session/editor/editor-session': sessions,
  '@/composables/editor/use-editor-collaboration': { useEditorCollaboration: () => ({ canEditCanvas: () => true }) },
  '@/composables/editor/use-keyboard-shortcuts': shortcuts,
  '@/stores/editor/editor.session.store': { useEditorSessionStore: () => ({ textViewSettings: {} }) },
  '@/utils/editor/textline-navigation': navigation,
  '@/utils/editor/pcgts-editor-primitives': pagePrimitives,
  '@/utils/editor/text-variants': textVariants,
  '@/utils/fetch-keys': {},
  '@/utils/glyph-service': {},
  './shared/text-input-guards': inputGuards,
  './line/reading-direction': readingDirection,
  './shared/full-text-lines': fullTextLines,
  './shared/text-view-runtime': runtime,
  './shared/textline-variant-update': variantUpdate,
  './shared/text-highlighting': {},
  './shared/full-text-dictionary': {},
  './shared/full-text-codec': {},
  './shared/full-text-comments': comments
}
const script = compileScript(parse(source).descriptor, { id: 'full-text-history', inlineTemplate: true })
const componentExports: { default?: Vue.Component } = {}
new Function('require', 'exports', transpileModule(script.content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText.replaceAll('import.meta.client', 'true'))((id: string) => {
  if (!(id in modules)) throw new Error(`Unexpected import: ${id}`)
  return modules[id]
}, componentExports)

let app: Vue.App | undefined

afterEach(() => {
  app?.unmount()
  app = undefined
  sessions.destroyEditorSession(canvasId)
  document.body.replaceChildren()
  vi.unstubAllGlobals()
})

function mountFullText({ editable = true, gtIndex = 0, withNextLine = false } = {}) {
  const line = new TextLine({
    id: 'line-1',
    coords: new Polygon([[0, 0], [10, 0], [10, 10]]),
    textContentVariants: [new TextContentVariant('OCR prediction', undefined, undefined, 1)]
  })
  const nextLine = new TextLine({
    id: 'line-2',
    coords: new Polygon([[0, 20], [10, 20], [10, 30]]),
    textContentVariants: [new TextContentVariant('Next prediction', undefined, undefined, 1)]
  })
  const lastLine = new TextLine({
    id: 'line-3',
    coords: new Polygon([[0, 40], [10, 40], [10, 50]]),
    textContentVariants: [new TextContentVariant('Last prediction', undefined, undefined, 1)]
  })
  const region = createTestTextRegion({ id: 'region-1', textLines: withNextLine ? [line, nextLine, lastLine] : [line] })
  const session = sessions.ensureEditorSession(canvasId, { document: createTestDocument({ regions: [region] }) })
  const commander = new Commander()
  const selectPolygonById = vi.fn()
  session.controls.value = { commander, isCanvasEditable: Vue.ref(editable), selectPolygonById } as EditorCanvasControls
  vi.stubGlobal('useEditorStore', () => ({ canvases: {}, projectTextDefaultGtIndex: gtIndex, setActiveCanvas: () => {}, regionsByCanvasId: () => [] }))
  vi.stubGlobal('useEditorUiStore', () => ({}))
  vi.stubGlobal('useWorkspaceStore', () => ({}))
  vi.stubGlobal('useToast', () => ({}))
  vi.stubGlobal('useOverlay', () => ({ create: () => ({}) }))
  vi.stubGlobal('useDictionaryTokenLookup', () => ({}))
  vi.stubGlobal('useEditorPreferences', () => ({ preferences: Vue.ref({ shortcutBindings: null }) }))
  vi.stubGlobal('watchEffect', Vue.watchEffect)
  vi.stubGlobal('onBeforeUnmount', Vue.onBeforeUnmount)

  const host = document.createElement('div')
  document.body.append(host)
  app = Vue.createApp({
    setup() {
      shortcuts.useKeyboardShortcuts({
        canvasId: Vue.ref(canvasId),
        isDrawingMode: Vue.ref(false),
        drawingMode: Vue.ref('select'),
        viewMode: Vue.ref('default'),
        selectedPolygonIds: Vue.ref([]),
        selectedPolylineIds: Vue.ref([]),
        polygons: Vue.ref([]),
        polylines: Vue.ref([]),
        selectedPolygonIndex: Vue.ref(-1),
        selectedPolylineIndex: Vue.ref(-1),
        callbacks: {
          handleUndo: () => undoSessionCommand(canvasId),
          handleRedo: () => redoSessionCommand(canvasId),
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
          centerOnSelection: vi.fn()
        }
      })
      return () => Vue.h(componentExports.default!, { canvasId })
    }
  })
  for (const name of ['EditorTextSharedCommentPopover', 'USkeleton', 'Icon', 'UBadge', 'USeparator', 'UButton', 'UPopover']) {
    app.component(name, passthrough)
  }
  app.mount(host)
  return { textarea: host.querySelector('textarea')!, nextTextarea: host.querySelectorAll('textarea')[1], commander, line, nextLine, region, selectPolygonById }
}

function pressHistory(target: HTMLTextAreaElement, shiftKey = false) {
  const event = new KeyboardEvent('keydown', {
    key: shiftKey ? 'Z' : 'z',
    code: 'KeyZ',
    metaKey: true,
    shiftKey,
    bubbles: true,
    cancelable: true
  })
  target.dispatchEvent(event)
  return event.defaultPrevented
}

describe('Full Text history', () => {
  it.each(['Next prediction', 'Corrected text'])('saves "%s" as GT and focuses the previous line on Shift+Enter', async (text) => {
    const { textarea, nextTextarea, line, nextLine, selectPolygonById } = mountFullText({ withNextLine: true })
    const target = nextTextarea!
    target.focus()
    if (text !== target.value) {
      target.value = text
      target.dispatchEvent(new Event('input', { bubbles: true }))
    }
    const event = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true, cancelable: true })
    target.dispatchEvent(event)
    await Vue.nextTick()

    expect(event.defaultPrevented).toBe(true)
    expect(nextLine.textContentVariants?.find(variant => variant.index === 0)?.unicode).toBe(text)
    expect(line.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(document.activeElement).toBe(textarea)
    expect(selectPolygonById).toHaveBeenLastCalledWith('line-1', { focusMode: 'fit-width' })
  })

  it.each(['OCR prediction', 'Corrected text'])('saves "%s" as GT and focuses the next line on Enter', async (text) => {
    const { textarea, nextTextarea, line, nextLine, selectPolygonById } = mountFullText({ withNextLine: true })
    textarea.focus()
    if (text !== textarea.value) {
      textarea.value = text
      textarea.dispatchEvent(new Event('input', { bubbles: true }))
    }
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }))
    await Vue.nextTick()

    expect(line.textContentVariants?.find(variant => variant.index === 0)?.unicode).toBe(text)
    expect(nextLine.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(document.activeElement).toBe(nextTextarea)
    expect(selectPolygonById).toHaveBeenLastCalledWith('line-2', { focusMode: 'fit-width' })
  })

  it('saves unchanged recognition as GT on Enter and supports undo and redo', async () => {
    const { textarea, commander, line, region } = mountFullText()
    textarea.focus()
    const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    textarea.dispatchEvent(event)
    await Vue.nextTick()

    expect(event.defaultPrevented).toBe(true)
    expect(line.textContentVariants?.find(variant => variant.index === 0)?.unicode).toBe('OCR prediction')
    expect(line.textContentVariants?.find(variant => variant.index === 1)?.unicode).toBe('OCR prediction')
    expect(region.textContentVariants?.find(variant => variant.index === 0)?.unicode).toBe('OCR prediction')
    expect(document.activeElement).toBe(textarea)

    // Confirming the same GT twice must not add another undo step.
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }))
    expect(pressHistory(textarea)).toBe(true)
    await Vue.nextTick()
    expect(line.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(region.textContentVariants?.find(variant => variant.index === 0)).toBeUndefined()
    expect(commander.canUndo()).toBe(false)
    expect(commander.canRedo()).toBe(true)
    expect(pressHistory(textarea, true)).toBe(true)
    await Vue.nextTick()
    expect(line.textContentVariants?.find(variant => variant.index === 0)?.unicode).toBe('OCR prediction')
  })

  it('saves edited text at the configured GT index on Enter before blur', async () => {
    const { textarea, line, region } = mountFullText({ gtIndex: 3 })
    textarea.focus()
    textarea.value = 'Corrected text'
    textarea.dispatchEvent(new Event('input', { bubbles: true }))
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }))
    await Vue.nextTick()

    expect(line.textContentVariants?.find(variant => variant.index === 3)?.unicode).toBe('Corrected text')
    expect(line.textContentVariants?.find(variant => variant.index === 1)?.unicode).toBe('OCR prediction')
    expect(region.textContentVariants?.find(variant => variant.index === 3)?.unicode).toBe('Corrected text')
    expect(shortcuts.getTextViewHistoryDraft(canvasId)?.hasChanges.value).toBe(false)
    expect(document.activeElement).toBe(textarea)
  })

  it('does not confirm recognition while composing or using modified Enter', async () => {
    const { textarea, commander, line } = mountFullText()
    for (const init of [{ isComposing: true }, { altKey: true }, { ctrlKey: true }, { metaKey: true }]) {
      textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...init }))
    }
    await Vue.nextTick()

    expect(line.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(commander.canUndo()).toBe(false)
  })

  it('does not create GT on Enter when the canvas is read-only', async () => {
    const { textarea, commander, line } = mountFullText({ editable: false })
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }))
    await Vue.nextTick()

    expect(line.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(commander.canUndo()).toBe(false)
  })

  it('undoes and redoes an in-progress transcription edit without moving focus', async () => {
    const { textarea, commander, line, region } = mountFullText()
    expect(textarea.value).toBe('OCR prediction')
    textarea.focus()
    textarea.value = 'Corrected text'
    textarea.dispatchEvent(new Event('input', { bubbles: true }))
    await Vue.nextTick()

    expect(shortcuts.getTextViewHistoryDraft(canvasId)?.hasChanges.value).toBe(true)
    expect(pressHistory(textarea)).toBe(true)
    await Vue.nextTick()
    expect(textarea.value).toBe('OCR prediction')
    expect(line.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(commander.canRedo()).toBe(true)
    expect(shortcuts.getTextViewHistoryDraft(canvasId)?.hasChanges.value).toBe(false)

    expect(pressHistory(textarea, true)).toBe(true)
    await Vue.nextTick()
    expect(textarea.value).toBe('Corrected text')
    expect(document.activeElement).toBe(textarea)
    expect(region.textContentVariants?.find(variant => variant.index === 0)?.unicode).toBe('Corrected text')
  })
})
