// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { computed, effectScope, nextTick, ref, watch } from 'vue'
import { parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import { Commander } from '@/commands/editor/commander'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import { TextContentVariant, TextLine } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { getAdjacentTextlineId } from '@/utils/editor/textline-navigation'
import { setGtVariantUnicode } from '@/utils/editor/text-variants'
import { createTextlineVariantsUpdateCommand } from '../text/shared/textline-variant-update'
import editorSource from '../index.vue?raw'

// Run the production navigation, input handlers and selection watcher together,
// with real undoable document commands and without mounting the WebGL editor.
const ast = ts.createSourceFile('editor.ts', parse(editorSource).descriptor.scriptSetup!.content, ts.ScriptTarget.Latest, true)
const functionNames = [
  'commitTextlineVariants', 'normalizeSingleLineText',
  'updateTextlineGtTextById', 'flushPendingCorrectionCommit', 'navigateCanvasCorrectionTextline',
  'handleCorrectionTextareaKeydown', 'handleCorrectionOverlayKeydown',
  'handleCorrectionTextareaInput', 'handleCorrectionTextareaBlur', 'createGtFromRecognition'
]
const productionCode = ast.statements.filter(node => (
  ts.isFunctionDeclaration(node) && functionNames.includes(node.name?.text ?? '')
) || (
  ts.isExpressionStatement(node) && node.getText(ast).startsWith('watch(')
  && (node.getText(ast).includes('prevGtIndex') || node.getText(ast).startsWith('watch(selectedTextlineGtText,'))
)).map(node => node.getText(ast)).join('\n')
const compiled = ts.transpileModule(productionCode, {}).outputText
const scopes: ReturnType<typeof effectScope>[] = []
afterEach(() => scopes.splice(0).forEach(scope => scope.stop()))

type CorrectionHandlers = {
  handleCorrectionTextareaKeydown: (event: KeyboardEvent) => void
  handleCorrectionOverlayKeydown: (event: KeyboardEvent) => void
  handleCorrectionTextareaInput: (event: Event) => void
  handleCorrectionTextareaBlur: () => void
  createGtFromRecognition: () => void
}

function setup() {
  const lines = [
    new TextLine({ id: 'line-1', coords: new Polygon([]), textContentVariants: [] }),
    new TextLine({ id: 'line-2', coords: new Polygon([]), textContentVariants: [new TextContentVariant('OCR', undefined, 0.9, 1)] })
  ]
  const region = createTestTextRegion({ id: 'region-1', textLines: lines })
  const { session } = createMockSession(createTestDocument({ regions: [region] }))
  const commander = new Commander()
  const context = createTestContext(session)
  const selectedId = ref<string | null>(null)
  const selectedTextlinePolygon = computed(() => lines.find(line => line.id === selectedId.value))
  const activeGtIndex = ref(0)
  const selectedTextlineGtVariant = computed(() => {
    // Depend on the document revision, as the renderable polygons do in production.
    void session.document.value
    return selectedTextlinePolygon.value?.textContentVariants?.find(variant => variant.index === activeGtIndex.value)
  })
  const correctionInputValue = ref('')
  const pendingCorrectionCommit = ref<{ textlineId: string, text: string } | null>(null)
  const isTextVisualMode = ref(true)
  const isCanvasWritable = ref(true)
  const dependencies = {
    watch, session, activeGtIndex, selectedTextlinePolygon, selectedTextlineGtVariant,
    selectedTextlineGtText: computed(() => selectedTextlineGtVariant.value?.unicode ?? ''),
    selectedTextlineFirstRecognitionVariant: computed(() => selectedTextlinePolygon.value?.textContentVariants?.find(variant => variant.index === 1)),
    correctionInputValue, pendingCorrectionCommit, isTextVisualMode, isCanvasWritable,
    correctionViewInitialized: ref(false), pageOrderTextlineIds: ref(lines.map(line => line.id)),
    polygons: lines, setGtVariantUnicode, getAdjacentTextlineId,
    createTextlineVariantsUpdateCommand, getCommandContext: () => context,
    canvasControls: {
      commander,
      selectPolygonById: (id: string) => { selectedId.value = id },
      selectPolylineById: vi.fn()
    },
    queueCorrectionInputFocus: vi.fn(), commitCorrectionInputDebounced: vi.fn(),
    getPersistedCorrectionZoom: () => null
  }
  const scope = effectScope()
  scopes.push(scope)
  const handlers = scope.run<CorrectionHandlers>(() => new Function(
    ...Object.keys(dependencies),
    `${compiled}; return { handleCorrectionTextareaKeydown, handleCorrectionOverlayKeydown, handleCorrectionTextareaInput, handleCorrectionTextareaBlur, createGtFromRecognition }`
  )(...Object.values(dependencies)))!
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
  return { ...handlers, input, tab, selectedId, activeGtIndex, correctionInputValue, isTextVisualMode, isCanvasWritable, lines, region, commander, context }
}

describe('Canvas text correction', () => {
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
})
