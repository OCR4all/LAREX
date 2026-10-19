import { useTablePagination } from '@/composables/use-table-pagination'
import { readFileSync } from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as actionRunStatus from '@/utils/action-run-status'
import type { ActionRunDetail, EvaluationReport } from '@/types/action'
import type { ActionOutputFile } from '@/types/action-output'

const descriptor = parse(readFileSync(new URL('../[runId].vue', import.meta.url), 'utf8')).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'evaluation-results-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
interface Setup {
  load: () => Promise<void>
  downloadFile: (file: ActionOutputFile) => Promise<void>
  detail: Vue.Ref<ActionRunDetail | null>
  fileMode: Vue.ComputedRef<boolean>
  report: Vue.ComputedRef<EvaluationReport | null>
  output: Vue.ComputedRef<unknown>
  baselines: Vue.Ref<unknown[]>
}
const exportsObject: { default?: { setup: (props: unknown, context: unknown) => Promise<Setup> } } = {}
new Function('require', 'exports', compiled)((name: string) => {
  if (name === 'vue') return { ...Vue, withAsyncContext: (fn: () => unknown) => [fn(), () => {}] }
  if (name === '@/utils/action-run-status') return actionRunStatus
  throw new Error(`Unexpected import ${name}`)
}, exportsObject)

describe('Evaluation result downloads', () => {
  const fetchApi = vi.fn()
  const fetchFile = vi.fn()
  const toast = vi.fn()
  const prepareDownload = vi.fn()
  const downloadBlobResponse = vi.fn()
  const target = { id: 'download' }
  let setup: Setup
  const file: ActionOutputFile = { id: 'file', pageId: null, fileName: 'report.pdf', mimeType: 'application/pdf', sizeBytes: 12, checksumSha256: 'checksum', created: '' }

  beforeEach(async () => {
    vi.clearAllMocks()
    vi.stubGlobal('useTablePagination', useTablePagination)
    vi.stubGlobal('watch', vi.fn())
    vi.stubGlobal('useWorkspaceBootstrap', async () => {})
    vi.stubGlobal('useWorkspaceStore', () => ({ selectedWorkspaceId: 'workspace' }))
    vi.stubGlobal('useRoute', () => ({ params: { runId: 'run' } }))
    vi.stubGlobal('useToast', () => ({ add: toast }))
    vi.stubGlobal('extractApiErrorMessage', (_error: unknown, fallback: string) => fallback)
    vi.stubGlobal('$fetch', fetchApi)
    vi.stubGlobal('fetch', fetchFile)
    prepareDownload.mockResolvedValue(target)
    vi.stubGlobal('useBackgroundDownloads', () => ({
      prepareDownload, downloadBlobResponse,
      runBackgroundJob: async ({ task }: { task: (job: unknown) => Promise<void> }) => task({})
    }))
    setup = await exportsObject.default!.setup({}, { expose: () => {} })
  })
  afterEach(() => vi.unstubAllGlobals())

  function fileDetail() {
    return {
      run: { id: 'run', datasetId: 'dataset', status: 'COMPLETED' },
      evaluationOutputMode: 'FILES', evaluationReport: null,
      resultSummary: [{ type: 'file' }],
      evaluationOutput: { id: 'output', files: [file], expiresAt: null }
    }
  }

  it('loads file results without interpreting their summary as a report or requesting baselines', async () => {
    fetchApi.mockResolvedValue(fileDetail())
    await setup.load()
    expect(setup.fileMode.value).toBe(true)
    expect(setup.report.value).toBeNull()
    expect(setup.baselines.value).toEqual([])
    expect(fetchApi).toHaveBeenCalledTimes(1)
    expect(setup.output.value).toEqual(expect.objectContaining({ id: 'output' }))
  })

  it('downloads through the dataset-scoped endpoint and existing background flow', async () => {
    fetchApi.mockResolvedValue(fileDetail())
    await setup.load()
    const response = { ok: true }
    fetchFile.mockResolvedValue(response)
    await setup.downloadFile(file)
    expect(fetchFile).toHaveBeenCalledWith('/api/workspaces/workspace/datasets/dataset/outputs/output/files/file/download')
    expect(downloadBlobResponse).toHaveBeenCalledWith(response, 'report.pdf', {}, target)
  })

  it('reports failed downloads without saving an error response', async () => {
    fetchApi.mockResolvedValue(fileDetail())
    await setup.load()
    fetchFile.mockResolvedValue({ ok: false, status: 404 })
    await setup.downloadFile(file)
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: 'Download failed', color: 'error' }))
    expect(downloadBlobResponse).not.toHaveBeenCalled()
  })

  it('keeps unavailable file results separate from structured reports', async () => {
    fetchApi.mockResolvedValue({ ...fileDetail(), evaluationOutput: null })
    await setup.load()
    expect(setup.fileMode.value).toBe(true)
    expect(setup.output.value).toBeNull()
    expect(setup.report.value).toBeNull()
  })

  it('preserves report-mode loading and baseline selection', async () => {
    const report = { title: 'Quality', summary: [], tables: [], samples: [] }
    fetchApi.mockResolvedValueOnce({ run: { id: 'run' }, evaluationReport: report }).mockResolvedValueOnce([{ id: 'baseline' }])
    await setup.load()
    expect(setup.fileMode.value).toBe(false)
    expect(setup.report.value).toEqual(report)
    expect(setup.baselines.value).toEqual([{ id: 'baseline' }])
  })
})
