import { readFileSync } from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as apiError from '@/utils/api-error'
import * as impactHelpers from '@/utils/action-impact'
import * as inputRequirements from '@/utils/action-input-requirements'
import * as parameterHelpers from '@/utils/action-parameter-values'
import type { ActionDefinition, ActionRun, ActionRunImpact, ExecutableActionProcessor } from '@/types/action'

// Compile the production setup script without mounting Nuxt or touching project data.
// Requests are mocked; these tests exercise the actual review/start/retry state machine.
const descriptor = parse(readFileSync(new URL('../run.vue', import.meta.url), 'utf8')).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'run-review-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
const dependencies: Record<string, unknown> = {
  'vue': Vue,
  '@/utils/api-error': apiError,
  '@/utils/action-impact': impactHelpers,
  '@/utils/action-input-requirements': inputRequirements,
  '@/utils/action-parameter-values': parameterHelpers,
  '@/composables/editor/use-canvas-interaction-blocker': { useBlockEditorCanvasInteractions: () => {} }
}
interface ReviewSetup {
  processors: Vue.Ref<ExecutableActionProcessor[]>
  parameterValues: Record<string, string>
  impact: Vue.Ref<ActionRunImpact | null>
  impactLoading: Vue.Ref<boolean>
  impactError: Vue.Ref<string | null>
  excludedPageIds: Vue.Ref<string[]>
  includedImpactPages: Vue.ComputedRef<unknown[]>
  startRun: () => Promise<void>
  retryRun: (run: ActionRun) => Promise<void>
  confirmReviewedRun: () => Promise<void>
  resetReview: () => void
  close: () => void
  setPageIncluded: (pageId: string, included: boolean) => void
}
const exportsObject: { default?: { setup: (props: unknown, context: unknown) => ReviewSetup } } = {}
new Function('require', 'exports', compiled)((id: string) => {
  if (!(id in dependencies)) throw new Error(`Unexpected dependency: ${id}`)
  return dependencies[id]
}, exportsObject)

const preview: ActionRunImpact = {
  target: 'PAGE', skippedPages: [], pages: [
    { pageId: 'affected', name: 'Affected', affected: true, affectedLevels: ['TEXT'], warningPrecision: 'DECLARED',
      targetSelection: { pageId: 'affected', regionIds: [], textLineIds: [] } },
    { pageId: 'safe', name: 'Safe', affected: false, affectedLevels: [], warningPrecision: 'DECLARED',
      targetSelection: { pageId: 'safe', regionIds: [], textLineIds: [] } }
  ]
}
const processor: ActionDefinition = {
  id: 'processor', processorKey: 'processor', name: 'Processor', description: null, yaml: '', endpointUrl: '',
  endpointTimeoutSeconds: 30, kind: 'PROCESSING', executeRole: 'CURATOR', lockMode: 'PAGES', category: 'WORKFLOW', targets: ['PAGE'],
  inputs: { images: { level: 'NONE', requiredForTargets: [] }, xml: { level: 'OPTIONAL', requiredForTargets: [] } },
  acceptsImages: false, acceptsXml: true, outputsImages: false, outputsXml: true, outputsFiles: false, overwrites: {},
  enabled: true, global: true, created: '', updated: '', trainingSplits: null, parameters: {}
}

describe('Action run review flow', () => {
  const fetch = vi.fn()
  const addToast = vi.fn()
  const emit = vi.fn()
  let scope: Vue.EffectScope
  let setup: ReviewSetup

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('$fetch', fetch)
    vi.stubGlobal('useToast', () => ({ add: addToast }))
    vi.stubGlobal('useActionRunsStore', () => ({ runsArray: [], upsertRun: vi.fn() }))
    vi.stubGlobal('onMounted', () => {})
    scope = Vue.effectScope()
    setup = scope.run(() => exportsObject.default!.setup({
      workspaceId: 'workspace', projectId: 'project', pageIds: ['affected', 'safe'],
      pages: preview.pages.map(page => ({ id: page.pageId, name: page.name, xmlFileCount: 1, imageCount: 1 }))
    }, { expose: () => {}, emit }))!
    setup.processors.value = [{ assignmentId: null, processor, executable: true, blockedReason: null }]
  })

  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('reviews risk before submitting and excludes unchecked pages', async () => {
    fetch.mockResolvedValueOnce(preview).mockResolvedValueOnce({ run: { id: 'run' } })
    await setup.startRun()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(setup.impact.value).toEqual(preview)
    setup.setPageIncluded('affected', false)
    await setup.confirmReviewedRun()
    expect(fetch.mock.calls[1]![1].body.pageIds).toEqual(['safe'])
    expect(fetch.mock.calls[1]![1].body.targetSelection.pages.map((page: { pageId: string }) => page.pageId)).toEqual(['safe'])
    expect(emit).toHaveBeenCalledWith('close', true)
  })

  it('starts immediately with explicit scope when there is no overwrite risk', async () => {
    fetch.mockResolvedValueOnce({ ...preview, pages: preview.pages.map(page => ({ ...page, affected: false })) })
      .mockResolvedValueOnce({ run: { id: 'run' } })
    await setup.startRun()
    expect(fetch.mock.calls[1]![1].body.pageIds).toEqual(['affected', 'safe'])
  })

  it('blocks failed previews and an empty remaining selection', async () => {
    fetch.mockRejectedValueOnce(new Error('Inspection failed'))
    await setup.startRun()
    expect(setup.impactError.value).toContain('Inspection failed')
    expect(fetch).toHaveBeenCalledTimes(1)
    fetch.mockResolvedValueOnce(preview)
    await setup.startRun()
    setup.setPageIncluded('affected', false)
    setup.setPageIncluded('safe', false)
    await setup.confirmReviewedRun()
    expect(setup.includedImpactPages.value).toHaveLength(0)
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('Back clears review without creating a run', async () => {
    fetch.mockResolvedValueOnce(preview)
    await setup.startRun()
    setup.setPageIncluded('affected', false)
    setup.resetReview()
    expect(setup.impact.value).toBeNull()
    expect(setup.excludedPageIds.value).toEqual([])
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('ignores a preview that finishes after the panel closes', async () => {
    let complete!: (value: ActionRunImpact) => void
    fetch.mockReturnValueOnce(new Promise<ActionRunImpact>((resolve) => {
      complete = resolve
    }))
    const pending = setup.startRun()
    setup.close()
    complete({ ...preview, pages: preview.pages.map(page => ({ ...page, affected: false })) })
    await pending
    expect(setup.impact.value).toBeNull()
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('invalidates review when configuration changes', async () => {
    fetch.mockResolvedValueOnce(preview)
    await setup.startRun()
    setup.parameterValues.model = 'different'
    expect(setup.impact.value).toBeNull()
    await setup.confirmReviewedRun()
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('preserves the reviewed scope when scheduling after a concurrency conflict', async () => {
    fetch.mockResolvedValueOnce(preview)
      .mockRejectedValueOnce({ data: { code: 'ACTION_CONCURRENCY_LIMIT_REACHED', message: 'Busy' } })
      .mockResolvedValueOnce({ run: { id: 'queued' } })
    await setup.startRun()
    setup.setPageIncluded('affected', false)
    await setup.confirmReviewedRun()
    const schedule = addToast.mock.calls[0]![0].actions[0].onClick
    setup.parameterValues.model = 'different'
    schedule()
    await Vue.nextTick()
    expect(fetch.mock.calls[2]![1].body).toMatchObject({ pageIds: ['safe'], enqueueIfBusy: true, parameters: {} })
  })

  it('reviews retries and excludes skipped as well as unchecked pages', async () => {
    fetch.mockResolvedValueOnce({ ...preview, skippedPages: [{ pageId: 'skipped', name: 'Skipped', reason: 'Missing input' }] })
      .mockResolvedValueOnce({ run: { id: 'retry' } })
    await setup.retryRun({ id: 'source' } as ActionRun)
    expect(fetch.mock.calls[0]![0]).toContain('/source/impact')
    setup.setPageIncluded('affected', false)
    await setup.confirmReviewedRun()
    expect(fetch.mock.calls[1]![0]).toContain('/source/retry')
    expect(fetch.mock.calls[1]![1].body.excludedPageIds).toEqual(['affected', 'skipped'])
  })
})
