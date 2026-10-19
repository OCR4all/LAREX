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
  './cutout-sizing': cutoutSizing
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
})

async function mountLine(layout: 'vertical' | 'horizontal', focusMode: boolean, readOnly = false) {
  // The annotation has no image geometry; let the real visibility callback run
  // without attempting to load an image or draw a canvas.
  vi.stubGlobal('IntersectionObserver', undefined)
  const textline = Vue.reactive({ id: 'line-1', comments: 'Check this transcription', points: [], textContentVariants: [] })
  const props = Vue.reactive({ textline, imageUrl: '', padding: 0, layout, focusMode, readOnly, showComments: false })
  const component = { ...componentExports.default, render: componentExports.render } as Vue.Component
  const app = Vue.createApp({
    render: () => Vue.h(component, props)
  })
  const control = Vue.defineComponent({
    setup: (_, { slots }) => () => Vue.h('span', slots.default?.())
  })
  for (const name of ['UBadge', 'UTooltip', 'UPopover', 'UDropdownMenu', 'UButton', 'Icon', 'USkeleton', 'UTextarea', 'UCheckbox', 'UInput']) {
    app.component(name, control)
  }
  app.component('UAlert', Vue.defineComponent({
    props: ['description'], setup: props => () => Vue.h('aside', props.description)
  }))
  registerCommentControls(app)
  app.component('EditorTextSharedCommentPopover', commentPopoverComponent)
  const host = document.createElement('div')
  document.body.append(host)
  apps.push(app)
  app.mount(host)
  await Vue.nextTick()
  return { host, props, textline }
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

  it('shows the full comment when enabled without hiding its indicator', async () => {
    const { host, props } = await mountLine('vertical', false)
    props.showComments = true
    await Vue.nextTick()
    expect(host.querySelector('aside')?.textContent).toBe('Check this transcription')
    expect(host.querySelector('[aria-label="Has comment"]')).not.toBeNull()
  })
})
