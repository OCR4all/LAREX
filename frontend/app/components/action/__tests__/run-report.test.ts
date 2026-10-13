import { readFileSync } from 'node:fs'
import { describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as actionRunStatus from '@/utils/action-run-status'
import type { ActionRunReport } from '@/types/action'

const descriptor = parse(readFileSync(new URL('../run-report.vue', import.meta.url), 'utf8')).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'run-report-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
interface Setup {
  download: () => void
  search: Vue.Ref<string>
  outcome: Vue.Ref<string>
  page: Vue.Ref<number>
  filteredPages: Vue.ComputedRef<ActionRunReport['pageOutcomes']>
  visiblePages: Vue.ComputedRef<ActionRunReport['pageOutcomes']>
}
const exportsObject: { default?: { setup: (props: unknown, context: unknown) => Setup } } = {}
new Function('require', 'exports', compiled)((id: string) => {
  if (id === 'vue') return Vue
  if (id === '@/utils/action-run-status') return actionRunStatus
  throw new Error(`Unexpected dependency ${id}`)
}, exportsObject)

describe('Run report', () => {
  it('downloads the complete report rather than only the filtered page', async () => {
    const report: ActionRunReport = {
      schemaVersion: 1, counts: { total: 0, succeeded: 0, failed: 0, skipped: 0, unfinished: 0, unknown: 0 },
      issues: [{ severity: 'WARNING', message: 'Diagnostic detail' }], pageOutcomes: []
    }
    const link = { href: '', download: '', click: vi.fn() }
    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:report')
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.stubGlobal('document', { createElement: () => link })
    vi.useFakeTimers()
    const scope = Vue.effectScope()
    try {
      const setup = scope.run(() => exportsObject.default!.setup({ report, runId: 'run' }, { expose: () => {} }))!
      setup.download()
      expect(link.download).toBe('action-run-run-report.json')
      expect(link.click).toHaveBeenCalledOnce()
      const blob = create.mock.calls[0]?.[0] as Blob
      expect(JSON.parse(await blob.text())).toEqual(report)
      vi.runAllTimers()
      expect(revoke).toHaveBeenCalledWith('blob:report')
    } finally {
      scope.stop()
      vi.useRealTimers()
      vi.unstubAllGlobals()
      vi.restoreAllMocks()
    }
  })

  it('filters a thousand-page report and paginates results', async () => {
    const report: ActionRunReport = {
      schemaVersion: 1,
      counts: { total: 1000, succeeded: 998, failed: 2, skipped: 0, unfinished: 0, unknown: 0 },
      issues: [],
      pageOutcomes: Array.from({ length: 1000 }, (_, index) => ({
        pageId: `p${index}`, pageName: `Page ${index}`, status: index >= 998 ? 'FAILED' : 'SUCCEEDED',
        message: index >= 998 ? 'Invalid XML' : null, warnings: []
      }))
    }
    const scope = Vue.effectScope()
    try {
      const setup = scope.run(() => exportsObject.default!.setup({ report, runId: 'run' }, { expose: () => {} }))!
      expect(setup.visiblePages.value).toHaveLength(25)
      setup.page.value = 2
      expect(setup.visiblePages.value[0]?.pageId).toBe('p25')
      setup.outcome.value = 'FAILED'
      await Vue.nextTick()
      expect(setup.page.value).toBe(1)
      expect(setup.filteredPages.value.map(page => page.pageId)).toEqual(['p998', 'p999'])
      setup.search.value = 'page 999'
      await Vue.nextTick()
      expect(setup.visiblePages.value.map(page => page.pageId)).toEqual(['p999'])
      setup.search.value = 'missing'
      expect(setup.visiblePages.value).toEqual([])
    } finally {
      scope.stop()
    }
  })
})
