// @vitest-environment happy-dom
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { afterEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import source from './link-items.vue?raw'

// Run the installed Nuxt fetch/async-data implementations with a minimal app
// context and controlled transport to cover Nuxt's reactive fetching semantics.
function nuxtFetch(fetch: ReturnType<typeof vi.fn>) {
  const require = createRequire(import.meta.url)
  const root = dirname(require.resolve('nuxt/package.json'))
  const app = {
    payload: { data: {}, _errors: {}, serverRendered: false },
    static: { data: {} }, _asyncData: {}, _asyncDataPromises: {},
    isHydrating: false,
    hook: () => () => {}
  }
  const cache = new Map<string, Record<string, unknown>>()
  function load(path: string): Record<string, unknown> {
    const cached = cache.get(path)
    if (cached) return cached
    const code = readFileSync(path, 'utf8').replace(/import\.meta\.(client|server|dev|prerender)/g, (_, flag) => String(flag === 'client'))
    const compiled = ts.transpileModule(code, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
    const exports = {}
    const dependencies: Record<string, unknown> = {
      'vue': Vue,
      '../nuxt.js': { useNuxtApp: () => app },
      '../utils.js': {},
      './error.js': { createError: (error: unknown) => error },
      '../utils/hash.js': { hashKey: JSON.stringify },
      '../internal/tracing.js': {},
      '../components/client-only.js': { clientOnlySymbol: Symbol() },
      './ready.js': {},
      './ssr.js': {},
      '#build/fetch': { $fetch: fetch },
      '#build/nuxt.config.mjs': {
        asyncDataDefaults: { deep: false }, fetchDefaults: {},
        alwaysRunFetchOnKeyChange: false, granularCachedData: true,
        pendingWhenIdle: false, purgeCachedData: true
      }
    }
    new Function('require', 'exports', compiled)((name: string) => {
      if (name.includes('/diagnostics/')) return {}
      if (name in dependencies) return dependencies[name]
      if (name.startsWith('.')) return load(resolve(dirname(path), name))
      return require(name)
    }, exports)
    cache.set(path, exports)
    return exports
  }
  return load(resolve(root, 'dist/app/composables/fetch.js')).useFetch
}

interface Picker {
  selectedProjectId: Vue.Ref<string | undefined>
  availablePages: Vue.ComputedRef<{ id: string, name: string }[]>
  pagesStatus: Vue.Ref<string>
  toggleSelectAll: () => void
  selectPages: () => void
}

const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'task-page-picker-test' })
const compiled = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const exports: { default?: { setup: (props: unknown, context: unknown) => Promise<Picker> } } = {}
new Function('require', 'exports', compiled)(() => Vue, exports)
let app: Vue.App

afterEach(() => {
  app?.unmount()
  vi.unstubAllGlobals()
})

async function picker(excludedPageIds: string[] = []) {
  const fetch = vi.fn(async (url: string) => {
    if (url === '/api/workspaces/workspace/projects') return [
      { id: 'project-a', name: 'A', pageCount: 2, tags: [] },
      { id: 'project-b', name: 'B', pageCount: 1, tags: [] }
    ]
    if (url === '/api/projects/project-a/pages') return [
      { id: 'page-1', name: 'Page 1', tags: [] },
      { id: 'page-2', name: 'Page 2', tags: [] }
    ]
    if (url === '/api/projects/project-b/pages') return [{ id: 'page-3', name: 'Page 3', tags: [] }]
    throw new Error(`Unexpected request: ${url}`)
  })
  vi.stubGlobal('useFetch', nuxtFetch(fetch))
  vi.stubGlobal('useId', () => 'picker')
  vi.stubGlobal('resolveComponent', () => 'input')
  vi.stubGlobal('wsKey', (...parts: string[]) => parts.join(':'))
  const emit = vi.fn()
  let state!: Picker
  const probe = Vue.defineComponent({
    async setup() {
      state = await exports.default!.setup({ workspaceId: 'workspace', excludedPageIds }, { expose: () => {}, emit })
      return () => null
    }
  })
  app = Vue.createApp({ render: () => Vue.h(Vue.Suspense, null, { default: () => Vue.h(probe) }) })
  app.mount(document.createElement('div'))
  await settle()
  return { state, fetch, emit }
}

async function settle() {
  await Vue.nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  await Vue.nextTick()
}

it('loads and selects pages when the first project is chosen for a new assignment', async () => {
  const { state, fetch, emit } = await picker()
  expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/workspaces/workspace/projects'])
  state.selectedProjectId.value = 'project-a'
  await settle()
  expect(fetch.mock.calls.map(([url]) => url)).toEqual([
    '/api/workspaces/workspace/projects', '/api/projects/project-a/pages'
  ])
  expect(state.pagesStatus.value).toBe('success')
  expect(state.availablePages.value.map(page => page.id)).toEqual(['page-1', 'page-2'])
  state.toggleSelectAll()
  state.selectPages()
  expect(emit).toHaveBeenCalledWith('close', [
    { pageId: 'page-1', pageName: 'Page 1', projectId: 'project-a', projectName: 'A' },
    { pageId: 'page-2', pageName: 'Page 2', projectId: 'project-a', projectName: 'A' }
  ])
})

it('excludes linked pages and retains selections when switching projects', async () => {
  const { state, emit } = await picker(['page-1'])
  state.selectedProjectId.value = 'project-a'
  await settle()
  expect(state.availablePages.value.map(page => page.id)).toEqual(['page-2'])
  state.toggleSelectAll()
  state.selectedProjectId.value = 'project-b'
  await settle()
  expect(state.availablePages.value.map(page => page.id)).toEqual(['page-3'])
  state.toggleSelectAll()
  state.selectPages()
  expect(emit).toHaveBeenCalledWith('close', [
    { pageId: 'page-2', pageName: 'Page 2', projectId: 'project-a', projectName: 'A' },
    { pageId: 'page-3', pageName: 'Page 3', projectId: 'project-b', projectName: 'B' }
  ])
})

it('clears stale rows while a different project is loading and can revisit a project', async () => {
  const { state, fetch } = await picker()
  state.selectedProjectId.value = 'project-a'
  await settle()
  expect(state.availablePages.value).toHaveLength(2)

  let finish!: (pages: { id: string, name: string, tags: string[] }[]) => void
  fetch.mockImplementationOnce(() => new Promise((resolve) => {
    finish = resolve
  }))
  state.selectedProjectId.value = 'project-b'
  await Vue.nextTick()
  expect(state.pagesStatus.value).toBe('pending')
  expect(state.availablePages.value).toEqual([])
  finish([{ id: 'page-3', name: 'Page 3', tags: [] }])
  await settle()
  expect(state.availablePages.value.map(page => page.id)).toEqual(['page-3'])

  state.selectedProjectId.value = 'project-a'
  await settle()
  expect(state.availablePages.value.map(page => page.id)).toEqual(['page-1', 'page-2'])
  expect(fetch.mock.calls.map(([url]) => url)).toEqual([
    '/api/workspaces/workspace/projects', '/api/projects/project-a/pages',
    '/api/projects/project-b/pages', '/api/projects/project-a/pages'
  ])
})
