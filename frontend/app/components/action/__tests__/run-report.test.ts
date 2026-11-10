import { useClampedPage, useTablePagination } from '@/composables/use-table-pagination'
import { readFileSync } from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as actionRunStatus from '@/utils/action-run-status'
import type { ActionRunReport } from '@/types/action'

const screen = vi.hoisted(() => ({ small: null as unknown as Vue.Ref<boolean> }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => screen.small }))

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
  beforeEach(() => {
    screen.small = Vue.ref(false)
    vi.stubGlobal('useTablePagination', useTablePagination)
    vi.stubGlobal('useClampedPage', useClampedPage)
  })
  afterEach(() => vi.unstubAllGlobals())

  it.each([false, true])('preserves pagination and filters when the report refreshes (mobile: %s)', async (mobile) => {
    screen.small.value = mobile
    const report: ActionRunReport = {
      schemaVersion: 1,
      counts: { total: 80, succeeded: 80, failed: 0, skipped: 0, unfinished: 0, unknown: 0 },
      issues: [],
      pageOutcomes: Array.from({ length: 80 }, (_, index) => ({
        pageId: `p${index}`, pageName: `Page ${index}`, status: 'SUCCEEDED', message: null, warnings: []
      }))
    }
    const props = Vue.reactive({ report, runId: 'run' })
    const scope = Vue.effectScope()
    try {
      const setup = scope.run(() => exportsObject.default!.setup(props, { expose: () => {} }))!
      setup.search.value = 'Page'
      setup.outcome.value = 'SUCCEEDED'
      await Vue.nextTick()
      setup.page.value = 3
      props.report = { ...report, pageOutcomes: report.pageOutcomes.map(item => ({ ...item, message: 'Updated' })) }
      await Vue.nextTick()
      expect(setup.page.value).toBe(3)
      expect(setup.search.value).toBe('Page')
      expect(setup.outcome.value).toBe('SUCCEEDED')
      expect(setup.visiblePages.value).toHaveLength(mobile ? 75 : 25)
      expect(setup.visiblePages.value[0]?.message).toBe('Updated')

      // Shrinking results should move to the last valid page, not the first.
      props.report = { ...report, pageOutcomes: report.pageOutcomes.slice(0, 40) }
      await Vue.nextTick()
      expect(setup.page.value).toBe(2)
      expect(setup.visiblePages.value).toHaveLength(mobile ? 40 : 15)

      props.report = { ...report, pageOutcomes: [] }
      await Vue.nextTick()
      expect(setup.page.value).toBe(1)
      expect(setup.visiblePages.value).toEqual([])
    } finally {
      scope.stop()
    }
  })

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
