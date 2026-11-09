// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { computed, effectScope, nextTick, reactive, ref, shallowReactive, watch } from 'vue'
import { Commander } from '@/commands/editor/commander'
import { UpdateReadingOrderCommand } from '@/commands/editor/update-reading-order-command'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import { TextContentVariant, TextLine } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { useCanvasTextCorrection } from '../use-canvas-text-correction'

const editorStore = reactive({
  projectTextDefaultGtIndex: 0,
  projectTextDefaultRecognitionIndices: [1],
  projectCodecId: null,
  projectCodecCharacters: [],
  projectDictionaryId: null
})
const editorUiStore = reactive({
  textViewFontSize: 32,
  canvasTextCorrectionOverlaySnapToLine: true,
  highlightUnknownCodecChars: false,
  highlightUnknownDictionaryTokens: false,
  preferencesLoaded: true,
  textViewAutoSelectFirstLine: false,
  bumpReadingOrderVersion: vi.fn(),
  setCanvasTextCorrectionZoom: vi.fn(),
  setCanvasTextCorrectionOverlaySnapToLine: vi.fn(),
  setCanvasTextCorrectionOverlayPosition: vi.fn()
})
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => editorStore }))
vi.mock('@/stores/editor/editor.ui.store', () => ({ useEditorUiStore: () => editorUiStore }))
vi.mock('@/stores/editor/editor.session.store', () => ({
  useEditorSessionStore: () => ({ textViewSettings: { showDiff: false, showComments: false, showRecognition: true } })
}))
vi.mock('@/stores/workspace.store', () => ({ useWorkspaceStore: () => ({ selectedWorkspaceId: null }) }))
vi.mock('@/composables/use-dictionary-token-lookup', () => ({
  useDictionaryTokenLookup: () => ({ ensureTokenResults: vi.fn(), getTokenResult: vi.fn(), isTokenPending: vi.fn(), hasSuggestionsLoaded: vi.fn() })
}))

const scopes: ReturnType<typeof effectScope>[] = []
afterEach(() => {
  scopes.splice(0).forEach(scope => scope.stop())
  editorUiStore.textViewAutoSelectFirstLine = false
})

function setup({ separateRegions = false } = {}) {
  const lines = [
    new TextLine({ id: 'line-1', coords: new Polygon([]), textContentVariants: [] }),
    new TextLine({ id: 'line-2', coords: new Polygon([]), textContentVariants: [new TextContentVariant('OCR', undefined, 0.9, 1)] })
  ]
  if (separateRegions) lines.push(new TextLine({ id: 'line-3', coords: new Polygon([]), textContentVariants: [] }))
  const region = createTestTextRegion({ id: 'region-1', textLines: lines })
  const { session } = createMockSession(createTestDocument({
    regions: separateRegions
      ? lines.map((line, i) => createTestTextRegion({ id: `region-${i + 1}`, textLines: [line] }))
      : [region],
    readingOrder: separateRegions
      ? {
          root: {
            kind: 'OrderedGroup', id: 'root', elements: [
              { kind: 'RegionRefIndexed', id: 'ref-2', regionRef: 'region-2', index: 0 },
              { kind: 'RegionRefIndexed', id: 'ref-1', regionRef: 'region-1', index: 1 },
              { kind: 'RegionRefIndexed', id: 'ref-3', regionRef: 'region-3', index: 2 }
            ]
          }
        }
      : undefined
  }))
  const commander = new Commander()
  const context = createTestContext(session)
  editorStore.projectTextDefaultGtIndex = 0
  editorStore.projectTextDefaultRecognitionIndices = [1]
  const activeGtIndex = computed({
    get: () => editorStore.projectTextDefaultGtIndex,
    set: (value) => { editorStore.projectTextDefaultGtIndex = value }
  })
  const activeRecognitionIndices = computed({
    get: () => editorStore.projectTextDefaultRecognitionIndices,
    set: (value) => { editorStore.projectTextDefaultRecognitionIndices = value }
  })
  const selectedId = ref<string | null>(null)
  const isTextVisualMode = ref(true)
  const isCanvasWritable = ref(true)
  const toRenderable = (): RenderablePolygon[] => lines.map(line => ({
    id: line.id,
    type: 'textline',
    points: [{ x: 10, y: 10 }],
    get textContentVariants() { return line.textContentVariants }
  } as unknown as RenderablePolygon))
  const polygons = shallowReactive(toRenderable())
  const scope = effectScope()
  scopes.push(scope)
  const correction = scope.run(() => {
    // Renderable polygons follow the document revision, as they do in production.
    watch(session.document, () => polygons.splice(0, polygons.length, ...toRenderable()), { flush: 'sync' })
    return useCanvasTextCorrection({
      session,
      canvasControls: {
        commander,
        selectPolygonById: (id: string | null) => { selectedId.value = id },
        selectPolylineById: vi.fn()
      },
      polygons,
      selectedPolygonIds: computed(() => selectedId.value ? [selectedId.value] : []),
      selectedPolygonIndex: ref(-1),
      selectedPolylineIds: ref([]),
      selectedPolylineIndex: ref(-1),
      selectedRegionId: ref(null),
      pageId: ref('page-1'),
      view: reactive({ zoom: 1, offsetX: 0, offsetY: 0 }),
      aspectRatioScale: ref({ scaleX: 1, scaleY: 1 }),
      canvasDimensions: ref({ width: 1000, height: 800 }),
      imageSize: ref({ width: 1000, height: 800 }),
      setView: vi.fn(),
      isTextVisualMode,
      isCanvasWritable,
      canShowCanvasContent: ref(true),
      toScreenPoint: point => point,
      getCommandContext: () => context
    })
  })!
  const { correctionInputValue, ...handlers } = correction
  const textarea = document.createElement('textarea')
  textarea.addEventListener('input', handlers.handleCorrectionTextareaInput)
  const input = (text: string) => {
    textarea.value = text
    textarea.dispatchEvent(new Event('input'))
  }
  const tab = async (shiftKey = false) => {
    handlers.handleCorrectionOverlayKeydown(new KeyboardEvent('keydown', { key: 'Tab', shiftKey }))
    await nextTick()
  }
  const enter = async (init: KeyboardEventInit = {}) => {
    const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true, ...init })
    handlers.handleCorrectionTextareaKeydown(event)
    await nextTick()
    return event
  }
  return { ...handlers, input, tab, enter, scope, selectedId, activeGtIndex, activeRecognitionIndices, correctionInputValue, isTextVisualMode, isCanvasWritable, lines, region, commander, context }
}

describe('Canvas text correction', () => {
  it('automatically selects the first line in PAGE reading order', async () => {
    editorUiStore.textViewAutoSelectFirstLine = true
    const editor = setup({ separateRegions: true })
    await nextTick()
    expect(editor.selectedId.value).toBe('line-2')
  })

  it('navigates between regions in PAGE reading order in both directions', async () => {
    const editor = setup({ separateRegions: true })
    editor.selectedId.value = 'line-2'
    await nextTick()

    await editor.tab()
    expect(editor.selectedId.value).toBe('line-1')
    await editor.tab()
    expect(editor.selectedId.value).toBe('line-3')
    await editor.tab()
    expect(editor.selectedId.value).toBe('line-2')
    await editor.tab(true)
    expect(editor.selectedId.value).toBe('line-3')
    expect(editor.commander.canUndo()).toBe(false)
  })

  it('refreshes navigation after reading-order edits and undo/redo', async () => {
    const editor = setup({ separateRegions: true })
    const readingOrder = editor.context.session!.document.value!.page.readingOrder!
    editor.selectedId.value = 'line-2'
    await nextTick()
    editor.commander.execute(new UpdateReadingOrderCommand({ readingOrder: {
      root: { ...readingOrder.root, elements: [...readingOrder.root.elements].reverse() }
    } }), editor.context)
    await editor.tab()
    expect(editor.selectedId.value).toBe('line-3')

    editor.commander.undo(editor.context)
    await editor.tab()
    expect(editor.selectedId.value).toBe('line-2')

    editor.commander.redo(editor.context)
    await editor.tab()
    expect(editor.selectedId.value).toBe('line-3')
  })

  it('saves pending input and populates the next line from recognition on Enter with undo/redo', async () => {
    const editor = setup()
    editor.selectedId.value = 'line-1'
    await nextTick()
    editor.input('Transcription')
    const event = await editor.enter()

    expect(event.defaultPrevented).toBe(true)
    expect(editor.selectedId.value).toBe('line-2')
    expect(editor.lines[0]!.textContentVariants?.[0]?.unicode).toBe('Transcription')
    expect(editor.lines[1]!.textContentVariants?.map(variant => [variant.index, variant.unicode]))
      .toEqual([[0, 'OCR'], [1, 'OCR']])
    expect(editor.correctionInputValue.value).toBe('OCR')
    expect(editor.region.textContentVariants?.[0]?.unicode).toBe('Transcription\nOCR')

    editor.commander.undo(editor.context)
    expect(editor.lines[1]!.textContentVariants?.map(variant => variant.index)).toEqual([1])
    editor.commander.redo(editor.context)
    expect(editor.lines[1]!.textContentVariants?.[0]?.unicode).toBe('OCR')
  })

  it.each(['Existing GT', '', '  '])('preserves existing GT %j on Enter', async (text) => {
    const editor = setup()
    editor.lines[1]!.textContentVariants!.unshift(new TextContentVariant(text, undefined, undefined, 0))
    editor.selectedId.value = 'line-1'
    await nextTick()
    await editor.enter()

    expect(editor.selectedId.value).toBe('line-2')
    expect(editor.correctionInputValue.value).toBe(text)
    expect(editor.commander.canUndo()).toBe(false)
  })

  it('wraps in both directions and creates empty GT when recognition is missing', async () => {
    const editor = setup()
    editor.selectedId.value = 'line-2'
    await nextTick()
    await editor.enter()

    expect(editor.selectedId.value).toBe('line-1')
    expect(editor.lines[0]!.textContentVariants?.map(variant => [variant.index, variant.unicode])).toEqual([[0, '']])
    await editor.enter({ shiftKey: true })
    expect(editor.selectedId.value).toBe('line-2')
    expect(editor.correctionInputValue.value).toBe('OCR')
  })

  it('uses the configured GT and recognition indices and normalizes copied text', async () => {
    const editor = setup()
    editor.activeGtIndex.value = 3
    editor.activeRecognitionIndices.value = [2, 1]
    editor.lines[1]!.textContentVariants!.push(new TextContentVariant('Preferred\nOCR', undefined, undefined, 2))
    editor.selectedId.value = 'line-1'
    await nextTick()
    await editor.enter()

    expect(editor.lines[1]!.textContentVariants?.find(variant => variant.index === 3)?.unicode).toBe('Preferred OCR')
    expect(editor.correctionInputValue.value).toBe('Preferred OCR')
  })

  it.each([{ isComposing: true }, { altKey: true }, { ctrlKey: true }, { metaKey: true }])(
    'does not navigate or populate GT on modified or composing Enter: %j', async (init) => {
      const editor = setup()
      editor.selectedId.value = 'line-1'
      await nextTick()
      await editor.enter(init)

      expect(editor.selectedId.value).toBe('line-1')
      expect(editor.commander.canUndo()).toBe(false)
    }
  )

  it('does not populate GT on a read-only canvas', async () => {
    const editor = setup()
    editor.isCanvasWritable.value = false
    editor.selectedId.value = 'line-1'
    await nextTick()
    await editor.enter()

    expect(editor.lines[1]!.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(editor.commander.canUndo()).toBe(false)
  })

  it('does not create GT or undo history when selecting and tabbing through untouched lines', async () => {
    const editor = setup()
    editor.selectedId.value = 'line-1'
    await nextTick()
    await editor.tab()
    expect(editor.selectedId.value).toBe('line-2')
    await editor.tab(true)
    expect(editor.selectedId.value).toBe('line-1')
    editor.handleCorrectionTextareaBlur()

    expect(editor.lines.map(line => line.textContentVariants?.map(variant => variant.index))).toEqual([[], [1]])
    expect(editor.region.textContentVariants).toEqual([])
    expect(editor.commander.canUndo()).toBe(false)
  })

  it('does not create GT when enabling Text–Canvas or changing the GT index', async () => {
    const editor = setup()
    editor.isTextVisualMode.value = false
    editor.selectedId.value = 'line-2'
    await nextTick()
    editor.isTextVisualMode.value = true
    await nextTick()
    editor.activeGtIndex.value = 2
    await nextTick()

    expect(editor.lines[1]!.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(editor.commander.canUndo()).toBe(false)
  })

  it('saves actual input to the original line on Tab and supports undo/redo', async () => {
    const editor = setup()
    editor.selectedId.value = 'line-1'
    await nextTick()
    editor.input('Transcription')
    // Tab before the debounced save runs; both lines initially display empty text.
    editor.handleCorrectionTextareaKeydown(new KeyboardEvent('keydown', { key: 'Tab' }))
    await nextTick()

    expect(editor.lines[0]!.textContentVariants?.[0]?.unicode).toBe('Transcription')
    expect(editor.lines[1]!.textContentVariants?.map(variant => variant.index)).toEqual([1])
    expect(editor.correctionInputValue.value).toBe('')
    expect(editor.region.textContentVariants?.[0]?.unicode).toBe('Transcription\n')
    expect(editor.commander.getState().totalCount).toBe(1)

    editor.commander.undo(editor.context)
    expect(editor.lines[0]!.textContentVariants).toBeUndefined()
    expect(editor.region.textContentVariants).toBeUndefined()
    editor.commander.redo(editor.context)
    expect(editor.lines[0]!.textContentVariants?.[0]?.unicode).toBe('Transcription')
  })

  it('preserves existing GT while navigating and saves an intentional deletion on blur', async () => {
    const editor = setup()
    editor.lines[0]!.textContentVariants = [new TextContentVariant('Existing GT', undefined, undefined, 0)]
    editor.selectedId.value = 'line-1'
    await nextTick()
    expect(editor.correctionInputValue.value).toBe('Existing GT')
    await editor.tab()
    await editor.tab(true)
    expect(editor.correctionInputValue.value).toBe('Existing GT')
    expect(editor.commander.canUndo()).toBe(false)

    editor.input('')
    editor.handleCorrectionTextareaBlur()
    expect(editor.lines[0]!.textContentVariants?.[0]?.unicode).toBe('')
    editor.commander.undo(editor.context)
    expect(editor.lines[0]!.textContentVariants?.[0]?.unicode).toBe('Existing GT')
  })

  it('creates GT only on the selected line when Copy to GT is used', async () => {
    const editor = setup()
    editor.selectedId.value = 'line-2'
    await nextTick()
    editor.createGtFromRecognition()
    await nextTick()

    expect(editor.lines[0]!.textContentVariants).toEqual([])
    expect(editor.lines[1]!.textContentVariants?.map(variant => [variant.index, variant.unicode]))
      .toEqual([[0, 'OCR'], [1, 'OCR']])
    expect(editor.correctionInputValue.value).toBe('OCR')
    expect(editor.commander.getState().totalCount).toBe(1)
    editor.commander.undo(editor.context)
    expect(editor.lines[1]!.textContentVariants?.map(variant => variant.index)).toEqual([1])
  })

  it('keeps pending typed text when Copy to GT is triggered by shortcut', async () => {
    const editor = setup()
    editor.selectedId.value = 'line-2'
    await nextTick()
    editor.input('Typed')

    expect(editor.createGtFromRecognition()).toBe(false)
    expect(editor.lines[1]!.textContentVariants?.find(variant => variant.index === 0)?.unicode).toBe('Typed')
  })

  it('saves input after the debounce without any navigation', async () => {
    const editor = setup()
    editor.selectedId.value = 'line-1'
    await nextTick()
    editor.input('Typed')
    expect(editor.commander.canUndo()).toBe(false)

    await new Promise(resolve => setTimeout(resolve, 120))
    expect(editor.lines[0]!.textContentVariants?.[0]?.unicode).toBe('Typed')
    expect(editor.commander.getState().totalCount).toBe(1)
  })

  it('removes overlay drag listeners when disposed mid-drag', async () => {
    const editor = setup()
    editor.selectedId.value = 'line-1'
    await nextTick()
    editor.correctionOverlayContainerRef.value = document.createElement('div')
    const removeListener = vi.spyOn(window, 'removeEventListener')

    editor.handleCorrectionOverlayPointerDown(new PointerEvent('pointerdown', { button: 0, pointerId: 1 }))
    editor.scope.stop()

    expect(removeListener.mock.calls.map(([type]) => type)).toEqual(['pointermove', 'pointerup', 'pointercancel'])
    removeListener.mockRestore()
  })
})
