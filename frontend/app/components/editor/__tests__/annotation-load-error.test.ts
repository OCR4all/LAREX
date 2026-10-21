// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import * as apiError from '@/utils/api-error'
import source from '../annotation-load-error.vue?raw'

const confirm = vi.fn()
const openXml = vi.fn()
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'annotation-error-test', inlineTemplate: true })
const compiled = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const component: { default?: Vue.Component } = {}
new Function('require', 'exports', compiled)((id: string) => {
  if (id === 'vue') return Vue
  if (id === '#components') return { LazyProjectSlideoverXmlEditor: {} }
  if (id.endsWith('use-overlay-dialogs')) return { useOverlayDialogs: () => ({ confirm }) }
  if (id.endsWith('editor.session.store')) return { useEditorSessionStore: () => ({ workspaceId: 'workspace' }) }
  if (id.endsWith('api-error')) return apiError
  throw new Error(`Unexpected dependency: ${id}`)
}, component)

const mounted: Vue.App[] = []
afterEach(() => {
  mounted.splice(0).forEach(app => app.unmount())
  vi.unstubAllGlobals()
  vi.resetAllMocks()
})

function mount({ invalid = true, locked = false, manager = true } = {}) {
  const canvas = Vue.reactive({
    projectId: 'project', pageId: 'page', imageSrc: '/page.png', imageVariantId: 'image',
    annotationContext: { mode: 'PROJECT', basePath: '/api/projects/project/pages/page/annotations' },
    annotationLoadError: { xmlId: 'xml', invalidXml: invalid, message: invalid ? 'Invalid XML' : 'Service unavailable',
      errors: invalid ? [{ line: 3, column: 12, message: 'Missing imageWidth' }] : [] }
  })
  const load = vi.fn().mockResolvedValue('/page.png')
  const invalidate = vi.fn()
  const fetch = vi.fn().mockResolvedValue({ deletedAnnotationCount: 1 })
  vi.stubGlobal('useEditorStore', () => ({ canvases: { canvas }, getPage: () => ({ label: 'Page', locked }), loadPageIntoCanvas: load, invalidateAnnotationCache: invalidate }))
  vi.stubGlobal('useWorkspaceCapabilities', () => ({ capabilities: Vue.ref({ canManageProjects: manager }) }))
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  vi.stubGlobal('useOverlay', () => ({ create: () => ({ open: openXml }) }))
  vi.stubGlobal('$fetch', fetch)
  const app = Vue.createApp(component.default!, { canvasId: 'canvas' })
  app.component('UIcon', { render: () => Vue.h('span') })
  app.component('UButton', Vue.defineComponent({
    props: ['disabled', 'loading', 'icon', 'color', 'variant'],
    setup: (props, { slots }) => () => Vue.h('button', { disabled: props.disabled || props.loading }, slots.default?.())
  }))
  const host = document.createElement('div')
  app.mount(host)
  mounted.push(app)
  return { host, load, fetch, invalidate }
}

async function click(host: HTMLElement, label: string) {
  Array.from(host.querySelectorAll('button')).find(button => button.textContent?.trim() === label)!.click()
  await new Promise(resolve => setTimeout(resolve, 0))
}

it('shows validation locations and reloads repaired XML with its cache cleared', async () => {
  const { host, load, invalidate } = mount()
  expect(host.textContent).toContain('Line 3, column 12: Missing imageWidth')
  expect(host.querySelector('img')?.src).toContain('/page.png')
  openXml.mockReturnValue({ result: Promise.resolve('saved') })
  await click(host, 'Fix in XML editor')
  expect(openXml).toHaveBeenCalledWith(expect.objectContaining({ xmlId: 'xml', readOnly: false }))
  expect(invalidate).toHaveBeenCalledWith('page', 'project')
  expect(load).toHaveBeenCalledOnce()
})

it.each([{ invalid: false }, { locked: true }, { manager: false }])('does not offer discard for $0', (options) => {
  const { host } = mount(options)
  expect(host.textContent).not.toContain('Discard and start fresh')
})

it('requires confirmation before discarding, then refreshes the page files', async () => {
  const { host, fetch, load } = mount()
  confirm.mockResolvedValueOnce(false)
  await click(host, 'Discard and start fresh')
  expect(fetch).not.toHaveBeenCalled()
  confirm.mockResolvedValueOnce(true)
  await click(host, 'Discard and start fresh')
  expect(fetch).toHaveBeenCalledWith('/api/projects/project/pages/annotations/batch', { method: 'DELETE', body: ['page'] })
  expect(load).toHaveBeenCalledWith('canvas', 'project', 'page', 'image', expect.objectContaining({ refreshPageData: true }))
})
