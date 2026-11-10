import { readFileSync } from 'node:fs'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'

const descriptor = parse(readFileSync(new URL('../run-logs-modal.vue', import.meta.url), 'utf8')).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'run-logs-modal-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
interface Setup {
  refresh: () => Promise<void>
  hasUpdates: Vue.ComputedRef<boolean>
  logs: Vue.ComputedRef<string>
}
const exportsObject: { default?: { setup: (props: unknown, context: unknown) => Setup } } = {}
new Function('require', 'exports', compiled)((id: string) => {
  if (id === 'vue') return Vue
  throw new Error(`Unexpected dependency ${id}`)
}, exportsObject)

describe('Run logs modal', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('keeps a stable snapshot during progress bursts and loads new details on demand', async () => {
    const updated = '2026-11-10T14:00:00Z'
    const store = Vue.reactive({ runsById: new Map([['run', { updated }]]) })
    const data = Vue.shallowRef({ run: { updated }, logEvents: [], logText: 'Initial logs' })
    const refresh = vi.fn(async () => {
      data.value = { run: { updated: store.runsById.get('run')!.updated }, logEvents: [], logText: 'Latest logs' }
    })
    vi.stubGlobal('useActionRunsStore', () => store)
    vi.stubGlobal('wsKey', (...parts: string[]) => parts.join(':'))
    vi.stubGlobal('useFetch', (_url: string, options: { watch?: false | Vue.WatchSource[] }) => {
      if (options.watch) {
        Vue.watch(options.watch, () => {
          void refresh()
        })
      }
      return { data, pending: Vue.ref(false), error: Vue.ref(null), refresh }
    })
    const scope = Vue.effectScope()
    try {
      const setup = scope.run(() => exportsObject.default!.setup({
        workspaceId: 'workspace', runId: 'run', title: 'Run logs', description: 'Run context'
      }, { expose: () => {}, emit: vi.fn() }))!
      expect(setup.hasUpdates.value).toBe(false)
      for (let index = 1; index <= 60; index++) {
        store.runsById.set('run', { updated: `2026-11-10T14:01:${String(index - 1).padStart(2, '0')}Z` })
        await Vue.nextTick()
      }
      expect(refresh).not.toHaveBeenCalled()
      expect(setup.logs.value).toBe('Initial logs')
      expect(setup.hasUpdates.value).toBe(true)

      await setup.refresh()
      expect(refresh).toHaveBeenCalledOnce()
      expect(setup.logs.value).toBe('Latest logs')
      expect(setup.hasUpdates.value).toBe(false)

      // A lagging status summary must not advertise older data as new.
      store.runsById.set('run', { updated })
      expect(setup.hasUpdates.value).toBe(false)
    } finally {
      scope.stop()
    }
  })
})
