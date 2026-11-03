// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import DiffMatchPatch from 'diff-match-patch'
import { TooltipProvider } from 'reka-ui'
import * as highlighting from '../../shared/text-highlighting'
import * as inputGuards from '../../shared/text-input-guards'
import * as readingDirection from '../reading-direction'
import * as variantRenderKey from '../variant-render-key'
import * as cutoutSizing from '../cutout-sizing'
import * as editorImage from '@/services/editor/editor-image'
import source from '../item.vue?raw'
import { commentPopoverComponent, registerCommentControls } from '../../../../../../test/support/comment-popover'

// Compile the real component because the unit runner does not load Nuxt's Vue
// plugin. Stub only application services and UI controls unrelated to comments.
const descriptor = parse(source).descriptor
const script = compileScript(descriptor, { id: 'comment-indicator-test' })
const template = compileTemplate({
  source: descriptor.template!.content,
  filename: 'item.vue',
  id: 'comment-indicator-test',
  compilerOptions: { bindingMetadata: script.bindings }
})
if (template.errors.length) throw new Error(String(template.errors))
const dependencies: Record<string, unknown> = {
  'vue': Vue,
  '#components': { LazyUiConfirmModal: {} },
  'diff-match-patch': DiffMatchPatch,
  'reka-ui': { TooltipProvider },
  '../shared/text-highlighting': highlighting,
  '../shared/text-input-guards': inputGuards,
  './reading-direction': readingDirection,
  './variant-render-key': variantRenderKey,
  './cutout-sizing': cutoutSizing,
  '@/services/editor/editor-image': editorImage
}
const componentExports: { default?: Vue.Component, render?: Vue.ComponentOptions['render'] } = {}
const autoImports = {
  computed: Vue.computed,
  ref: Vue.ref,
  watch: Vue.watch,
  nextTick: Vue.nextTick,
  onMounted: Vue.onMounted,
  onBeforeUnmount: Vue.onBeforeUnmount,
  useOverlay: () => ({ create: () => ({}) }),
  useWorkspaceStore: () => ({ selectedWorkspaceId: null }),
  useDictionaryTokenLookup: () => ({})
}
new Function('require', 'exports', ...Object.keys(autoImports), transpileModule(
  `${script.content}\n${template.code}`.replaceAll('import.meta.client', 'false'),
  { compilerOptions: { module: ModuleKind.CommonJS } }
).outputText)((id: string) => {
  if (!(id in dependencies)) throw new Error(`Unexpected dependency ${id}`)
  return dependencies[id]
}, componentExports, ...Object.values(autoImports))

const apps: Vue.App[] = []
afterEach(() => {
  apps.splice(0).forEach(app => app.unmount())
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

interface Variant {
  pos: number
  index?: number
  text: string
  comments?: string
}

async function mountLine(layout: 'vertical' | 'horizontal', focusMode: boolean, readOnly = false, variants: Variant[] = []) {
  // The annotation has no image geometry; let the real visibility callback run
  // without attempting to load an image or draw a canvas.
  vi.stubGlobal('IntersectionObserver', undefined)
  const textline = Vue.reactive({ id: 'line-1', comments: 'Check this transcription', points: [] as Array<{ x: number, y: number }>, textContentVariants: variants })
  const props = Vue.reactive({ textline, imageUrl: '', padding: 0, layout, focusMode, readOnly, showComments: false, gtIndex: 0, recognitionIndices: [1, 2, -1], showDiff: false })
  const saveVariantComment = vi.fn()
  const component = { ...componentExports.default, render: componentExports.render } as Vue.Component
  const app = Vue.createApp({
    render: () => Vue.h(component, { ...props, onUpdateTextContentVariantComment: saveVariantComment })
  })
  const control = Vue.defineComponent({
    setup: (_, { slots }) => () => Vue.h('span', slots.default?.())
  })
  for (const name of ['UBadge', 'UTooltip', 'UPopover', 'UDropdownMenu', 'UButton', 'Icon', 'USkeleton', 'UTextarea', 'UCheckbox', 'UInput']) {
    app.component(name, control)
  }
  registerCommentControls(app)
  app.component('EditorTextSharedCommentPopover', commentPopoverComponent)
  const host = document.createElement('div')
  document.body.append(host)
  apps.push(app)
  app.mount(host)
  await Vue.nextTick()
  return { host, props, textline, saveVariantComment }
}

describe('textline comment visibility', () => {
  it.each([
    ['vertical', false, false],
    ['horizontal', false, false],
    ['vertical', true, false],
    ['horizontal', true, false],
    ['vertical', true, true]
  ] as const)('marks comments in %s layout (focus: %s, read-only: %s)', async (layout, focus, readOnly) => {
    const { host, textline } = await mountLine(layout, focus, readOnly)
    expect(host.querySelector('[aria-label="Has comment"]')).not.toBeNull()
    expect(host.querySelector('[class~="bg-info/5"]')).not.toBeNull()
    expect(host.querySelector('aside')).toBeNull()

    host.querySelector<HTMLButtonElement>('[aria-label="Has comment"]')!.click()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
    if (readOnly) {
      expect(document.querySelector('textarea')).toBeNull()
      expect(document.querySelector('[role="dialog"]')?.textContent).toContain('Check this transcription')
    } else {
      expect(document.querySelector('textarea')?.value).toBe('Check this transcription')
    }
    document.querySelector('[role="dialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())

    textline.comments = '  \n  '
    await Vue.nextTick()
    expect(host.querySelector('[aria-label="Has comment"]')).toBeNull()
    expect(host.querySelector('[class~="bg-info/5"]')).toBeNull()
    if (!readOnly) expect(host.querySelector('[aria-label="Add comment"]')).not.toBeNull()

    textline.comments = 'A new comment'
    await Vue.nextTick()
    expect(host.querySelector('[aria-label="Has comment"]')).not.toBeNull()
  })

  it.each([
    ['vertical', false], ['horizontal', false], ['vertical', true], ['horizontal', true]
  ] as const)('shows the full comment next to the icon in %s layout (focus: %s)', async (layout, focus) => {
    const { host, props } = await mountLine(layout, focus)
    expect(host.querySelector('[aria-label="Text-line comment"]')).toBeNull()
    props.showComments = true
    await Vue.nextTick()
    expect(host.querySelector('[aria-label="Text-line comment"]')?.textContent?.trim()).toBe('Check this transcription')
    expect(host.querySelector('[aria-label="Has comment"]')).not.toBeNull()
    expect(host.querySelector('[aria-label="Has comment"]')?.textContent?.trim()).toBe('')
    expect(host.querySelector('aside')).toBeNull()
    props.showComments = false
    await Vue.nextTick()
    expect(host.querySelector('[aria-label="Text-line comment"]')).toBeNull()
    expect(host.querySelector('[aria-label="Has comment"]')).not.toBeNull()
  })
})

describe('TextEquiv comment visibility', () => {
  const variants: Variant[] = [
    { pos: 0, index: 0, text: 'Ground truth' },
    { pos: 1, index: 1, text: 'First recognition', comments: 'Model A\nVersion 1' },
    { pos: 2, index: 2, text: 'Second recognition', comments: 'Model B' },
    { pos: 3, text: 'Unindexed recognition', comments: 'Unindexed model' },
    { pos: 4, index: 3, text: 'Blank comment', comments: ' \n ' }
  ]

  it.each([
    ['vertical', false, false], ['horizontal', false, false],
    ['vertical', true, false], ['horizontal', true, true]
  ] as const)('shows per-variant indicators and toggles text in %s layout (focus: %s, read-only: %s)', async (layout, focus, readOnly) => {
    const { host, props } = await mountLine(layout, focus, readOnly, variants.map(variant => ({ ...variant })))
    expect(host.querySelectorAll('[aria-label="Has variant comment"]')).toHaveLength(3)
    expect(host.textContent).not.toContain('Model A')
    props.showComments = true
    props.showDiff = true
    await Vue.nextTick()
    for (const [index, comment] of [[1, 'Model A\nVersion 1'], [2, 'Model B'], ['unindexed', 'Unindexed model']]) {
      expect(host.querySelector(`[aria-label="TextEquiv ${index} comment"]`)?.textContent?.trim()).toBe(comment)
    }
    expect(host.querySelector('[aria-label="TextEquiv 0 comment"]')).toBeNull()
    expect(host.querySelector('[aria-label="TextEquiv 3 comment"]')).toBeNull()
    props.showComments = false
    await Vue.nextTick()
    expect(host.textContent).not.toContain('Model A')
    expect(host.querySelectorAll('[aria-label="Has variant comment"]')).toHaveLength(3)
  })

  it('opens the correct full comment as read-only and follows updated variants', async () => {
    const { host, textline, props } = await mountLine('vertical', false, true, variants.map(variant => ({ ...variant })))
    expect(host.querySelector('[aria-label="Add variant comment"]')).toBeNull()
    host.querySelector<HTMLButtonElement>('[aria-label="Has variant comment"]')!.click()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
    expect(document.querySelector('[role="dialog"]')?.textContent).toContain('Model A\nVersion 1')
    expect(document.querySelector('[role="dialog"]')?.textContent).toContain('TextEquiv 1')
    expect(document.querySelector('[role="dialog"] textarea')).toBeNull()
    document.querySelector('[role="dialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    textline.textContentVariants = [{ pos: 1, index: 1, text: 'Updated', comments: 'New model' }]
    props.showComments = true
    await Vue.nextTick()
    expect(host.querySelectorAll('[aria-label="Has variant comment"]')).toHaveLength(1)
    expect(host.querySelector('[aria-label="TextEquiv 1 comment"]')?.textContent?.trim()).toBe('New model')
    textline.textContentVariants[0]!.comments = '  '
    await Vue.nextTick()
    expect(host.querySelector('[aria-label="Has variant comment"]')).toBeNull()
    expect(host.querySelector('[aria-label="TextEquiv 1 comment"]')).toBeNull()
  })

  it('adds and edits comments on recognition variants using the original variant position', async () => {
    const { host, saveVariantComment } = await mountLine('vertical', false, false, [
      { pos: 7, index: 1, text: 'Recognition A', comments: 'Model A' },
      { pos: 2, index: 2, text: 'Recognition B' }
    ])
    for (const [label, initial, comment, position] of [
      ['Has variant comment', 'Model A', '  Updated model\nVersion 2  ', 7],
      ['Add variant comment', '', 'Model B', 2],
      ['Has variant comment', 'Model A', ' \n ', 7]
    ] as const) {
      host.querySelector<HTMLButtonElement>(`[aria-label="${label}"]`)!.click()
      await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
      const textarea = document.querySelector<HTMLTextAreaElement>('[aria-label="TextEquiv comment"]')!
      expect(textarea.value).toBe(initial)
      textarea.value = comment
      textarea.dispatchEvent(new Event('input', { bubbles: true }))
      await Vue.nextTick()
      const save = [...document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')].find(button => button.textContent?.trim() === 'Save')!
      save.click()
      await Vue.nextTick()
      expect(saveVariantComment).toHaveBeenLastCalledWith('line-1', position, comment)
      await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    }
  })
})

describe('textline presentation cutouts', () => {
  it('draws a bottom-right crop from a reduced raster using original image coordinates', async () => {
    const drawImage = vi.fn()
    const context = { drawImage, setTransform: vi.fn(), clearRect: vi.fn() }
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context as unknown as CanvasRenderingContext2D)
    const image = document.createElement('img')
    Object.defineProperties(image, { naturalWidth: { value: 3200 }, naturalHeight: { value: 2102 } })
    vi.spyOn(editorImage, 'loadEditorImage').mockResolvedValue({ image, originalSize: { width: 19218, height: 12623 } })
    const { props, textline } = await mountLine('vertical', false)
    props.imageUrl = '/api/projects/p/pages/images/scan/presentation?size=detail'
    textline.points = [{ x: 18000, y: 12000 }, { x: 19000, y: 12000 }, { x: 19000, y: 12200 }, { x: 18000, y: 12200 }]
    await vi.waitFor(() => expect(drawImage).toHaveBeenCalled())
    const call = drawImage.mock.calls.at(-1)!
    expect(call[0]).toBe(image)
    expect(call[1]).toBeCloseTo(18000 / 19218 * 3200)
    expect(call[2]).toBeCloseTo(12000 / 12623 * 2102)
    expect(call[3]).toBeCloseTo(1000 / 19218 * 3200)
    expect(call[4]).toBeCloseTo(200 / 12623 * 2102)
  })
})
