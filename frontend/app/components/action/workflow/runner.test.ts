import { readFileSync } from 'node:fs'
import { ModuleKind, transpileModule } from 'typescript'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import * as errors from '~/utils/api-error'
import * as impacts from '~/utils/action-impact'
import * as statuses from '~/utils/action-run-status'
import type { ActionRunImpact, ExecutableActionProcessor } from '~/types/action'
import type { ActionWorkflow, ActionWorkflowStep } from '~/types/action-workflow'

// Exercise the production review/submission state machine without a Nuxt server.
const descriptor = parse(readFileSync(new URL('./runner.vue', import.meta.url), 'utf8')).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'workflow-runner-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
const dependencies: Record<string, unknown> = {
  'vue': Vue,
  '~/utils/api-error': errors,
  '~/utils/action-impact': impacts,
  '~/utils/action-run-status': statuses
}
interface Runner {
  workflows: Vue.Ref<ActionWorkflow[]>
  selectedId: Vue.Ref<string>
  steps: Vue.Ref<ActionWorkflowStep[]>
  scope: Vue.Ref<'selection' | 'all'>
  impact: Vue.Ref<ActionRunImpact | null>
  error: Vue.Ref<string>
  review: () => Promise<void>
  start: () => Promise<void>
  include: (pageId: string, include: boolean) => void
}
const component: { default?: { setup: (props: unknown, context: unknown) => Runner } } = {}
new Function('require', 'exports', compiled)((id: string) => dependencies[id], component)
const processor = { id: 'processor', parameters: {} }
const workflow: ActionWorkflow = {
  id: 'workflow', name: 'Workflow', description: null, enabled: true, version: 4,
  steps: [
    { processorDefinitionId: 'processor', parameters: { threshold: 0.3 }, imageVariantSelection: null },
    { processorDefinitionId: 'processor', parameters: { threshold: 0.8 }, imageVariantSelection: null }
  ]
}
const preview: ActionRunImpact = {
  target: 'PAGE', skippedPages: [], pages: ['one', 'two'].map(pageId => ({
    pageId, name: pageId, affectedLevels: ['TEXT'], warningPrecision: 'DECLARED', affected: true,
    targetSelection: { pageId, regionIds: [], textLineIds: [] }
  }))
}

describe('Workflow run review', () => {
  const fetch = vi.fn()
  const emit = vi.fn()
  let effects: Vue.EffectScope
  let runner: Runner
  let props: { processors: ExecutableActionProcessor[] }
  beforeEach(async () => {
    vi.clearAllMocks()
    vi.stubGlobal('$fetch', fetch)
    vi.stubGlobal('toRaw', Vue.toRaw)
    vi.stubGlobal('useActionRunsStore', () => ({ refreshProjectRuns: vi.fn(), runsById: new Map() }))
    vi.stubGlobal('onMounted', () => {})
    vi.stubGlobal('onBeforeUnmount', () => {})
    effects = Vue.effectScope()
    props = Vue.reactive({ workspaceId: 'workspace', projectId: 'project', pageIds: ['one', 'two'], processors: [
      { processor, executable: true, blockedReason: null, assignmentId: null }
    ] }) as unknown as typeof props
    runner = effects.run(() => component.default!.setup(props, { expose: () => {}, emit }))!
    runner.workflows.value = structuredClone([workflow])
    runner.selectedId.value = workflow.id
    await Vue.nextTick()
    await Vue.nextTick()
    fetch.mockClear()
  })
  afterEach(() => {
    effects.stop()
    vi.unstubAllGlobals()
  })

  it('requires review and submits only retained pages with independent step overrides and a saved version', async () => {
    await runner.start()
    expect(fetch).not.toHaveBeenCalled()
    runner.steps.value[1]!.parameters.threshold = 0.6
    await Vue.nextTick()
    fetch.mockResolvedValueOnce(structuredClone(preview))
    await runner.review()
    expect(fetch).toHaveBeenCalledTimes(1)
    runner.include('two', false)
    fetch.mockResolvedValueOnce({ id: 'run' }).mockResolvedValueOnce([])
    await runner.start()
    expect(fetch.mock.calls[1]![1].body).toEqual({
      workflowId: 'workflow', version: 4, pageIds: ['one'],
      steps: [workflow.steps[0], { ...workflow.steps[1], parameters: { threshold: 0.6 } }]
    })
    expect(workflow.steps[1]!.parameters.threshold).toBe(0.8)
    expect(runner.impact.value).toBeNull()
    expect(emit).toHaveBeenCalledWith('started')
  })

  it('invalidates review after a parameter, image selection or page scope changes', async () => {
    for (const change of [
      () => { runner.steps.value[0]!.parameters.threshold = 0.4 },
      () => { runner.steps.value[1]!.imageVariantSelection = { mode: 'GLOBAL', variants: ['binarized'], fallbackImage: false } },
      () => { runner.scope.value = 'all' }
    ]) {
      fetch.mockResolvedValueOnce(structuredClone(preview))
      await runner.review()
      expect(runner.impact.value).not.toBeNull()
      change()
      await Vue.nextTick()
      expect(runner.impact.value).toBeNull()
    }
  })

  it('blocks review when any processor is unavailable or execution is forbidden', async () => {
    props.processors = []
    await Vue.nextTick()
    await runner.review()
    expect(fetch).not.toHaveBeenCalled()
    props.processors = [{ processor, executable: false, blockedReason: 'Curator required' }] as ExecutableActionProcessor[]
    await Vue.nextTick()
    await runner.review()
    expect(fetch).not.toHaveBeenCalled()
  })

  it('does not start when all reviewed pages were excluded and retains review after a rejected submission', async () => {
    fetch.mockResolvedValueOnce(structuredClone(preview))
    await runner.review()
    runner.include('one', false)
    runner.include('two', false)
    await runner.start()
    expect(fetch).toHaveBeenCalledTimes(1)
    runner.include('one', true)
    fetch.mockRejectedValueOnce(new Error('Unavailable'))
    await runner.start()
    expect(runner.impact.value).not.toBeNull()
    expect(runner.error.value).toBeTruthy()
    expect(emit).not.toHaveBeenCalledWith('started')
  })
})
