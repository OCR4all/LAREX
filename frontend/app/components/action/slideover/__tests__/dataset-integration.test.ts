import * as actionRunStatus from '@/utils/action-run-status'
import { readFileSync } from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as apiError from '@/utils/api-error'
import * as inputRequirements from '@/utils/action-input-requirements'
import * as parameterHelpers from '@/utils/action-parameter-values'
import type { ActionDefinition, ActionRun, IntegrationInputItem } from '@/types/action'

const descriptor = parse(readFileSync(new URL('../dataset-integration.vue', import.meta.url), 'utf8')).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'dataset-integration-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
const dependencies: Record<string, unknown> = {
  'vue': Vue,
  '@/utils/action-run-status': actionRunStatus,
  '@/utils/api-error': apiError,
  '@/utils/action-input-requirements': inputRequirements,
  '@/utils/action-parameter-values': parameterHelpers
}
interface Setup {
  load: () => Promise<void>
  start: () => Promise<void>
  canStart: Vue.ComputedRef<boolean>
  multipleImages: Vue.ComputedRef<boolean>
  globalVariants: Vue.Ref<string[]>
  selectionMode: Vue.Ref<'ALL' | 'SELECTED'>
  compatibleItems: Vue.ComputedRef<IntegrationInputItem[]>
  skippedCount: Vue.ComputedRef<number>
  parameters: Vue.Ref<Record<string, string | number | boolean>>
  itemImageIdLists: Vue.Ref<Record<string, string[]>>
  history: Vue.Ref<ActionRun[]>
  changed: Vue.Ref<boolean>
  discoveryError: Vue.Ref<string | null>
}
const exportsObject: { default?: { setup: (props: unknown, context: unknown) => Setup } } = {}
new Function('require', 'exports', compiled)((name: string) => {
  if (!(name in dependencies)) throw new Error(`Unexpected import ${name}`)
  return dependencies[name]
}, exportsObject)

const definition: ActionDefinition = {
  id: 'integration', processorKey: 'integration', name: 'Integration', description: null,
  yaml: '', endpointUrl: '', endpointTimeoutSeconds: 30, kind: 'INTEGRATION', executeRole: 'CURATOR',
  lockMode: 'NONE', tags: [], targets: ['PAGE'],
  inputs: { images: { level: 'NONE', requiredForTargets: [] }, xml: { level: 'REQUIRED', requiredForTargets: [] } },
  acceptsImages: false, acceptsXml: true, outputsImages: false, outputsXml: false, outputsFiles: false,
  overwrites: {}, enabled: true, global: true, created: '', updated: '', trainingSplits: null,
  parameters: { endpoint: { type: 'string', default: 'external' } }
}
const item: IntegrationInputItem = {
  itemId: 'item', sourcePageId: 'page', pageName: 'Page', split: null, status: 'READY',
  brokenReason: null, xmlAvailable: true, images: []
}

describe('Dataset Integration run flow', () => {
  const fetch = vi.fn()
  const upsertRun = vi.fn()
  let scope: Vue.EffectScope
  let setup: Setup
  let activeDefinition: ActionDefinition
  let inputs: IntegrationInputItem[]

  function create(itemIds: string[] = []) {
    scope = Vue.effectScope()
    setup = scope.run(() => exportsObject.default!.setup({ workspaceId: 'workspace', datasetId: 'dataset', datasetName: 'Dataset', itemIds }, { expose: () => {}, emit: vi.fn() }))!
  }

  beforeEach(() => {
    vi.clearAllMocks()
    activeDefinition = definition
    inputs = [item]
    vi.stubGlobal('onMounted', () => {})
    vi.stubGlobal('useActionRunsStore', () => ({ runsById: new Map(), upsertRun, upsertRuns: vi.fn() }))
    vi.stubGlobal('$fetch', fetch)
    fetch.mockImplementation(async (url: string, options?: { method?: string }) => {
      if (url.endsWith('/processors')) return [activeDefinition]
      if (url.endsWith('/inputs')) return { datasetId: 'dataset', datasetName: 'Dataset', items: inputs }
      if (options?.method === 'POST') return { run: { id: 'run', kind: 'INTEGRATION', datasetId: 'dataset' } }
      if (url.endsWith('/runs')) return []
      throw new Error(`Unexpected request ${url}`)
    })
    create()
  })
  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('starts XML-only inputs across all splits and records returned run feedback', async () => {
    await setup.load()
    await Vue.nextTick()
    expect(setup.canStart.value).toBe(true)
    await setup.start()
    expect(fetch).toHaveBeenLastCalledWith('/api/workspaces/workspace/actions/integration/datasets/dataset/runs', {
      method: 'POST', body: {
        processorDefinitionId: 'integration', selection: { mode: 'ALL', itemIds: [] }, imageSelection: null,
        parameters: { endpoint: 'external' }, enqueueIfBusy: true
      }
    })
    expect(upsertRun).toHaveBeenCalledWith(expect.objectContaining({ kind: 'INTEGRATION', datasetId: 'dataset' }), 'Dataset')
    expect(setup.history.value).toHaveLength(1)
    expect(setup.changed.value).toBe(true)
  })

  it('preserves selected item IDs and blocks incompatible selections', async () => {
    scope.stop()
    create(['item', 'missing-xml'])
    inputs = [item, { ...item, itemId: 'missing-xml', xmlAvailable: false }]
    await setup.load()
    await Vue.nextTick()
    expect(setup.selectionMode.value).toBe('SELECTED')
    expect(setup.skippedCount.value).toBe(1)
    expect(setup.canStart.value).toBe(false)
    await setup.start()
    expect(fetch.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
    setup.selectionMode.value = 'ALL'
    expect(setup.canStart.value).toBe(true)
  })

  it('requires image selection when multiple variants are available', async () => {
    activeDefinition = { ...definition, inputs: { images: { level: 'REQUIRED', requiredForTargets: [], multiple: true }, xml: { level: 'NONE', requiredForTargets: [] } } }
    inputs = [{ ...item, xmlAvailable: false, images: [
      { id: 'image-a', fileName: 'a.png', variant: 'a' }, { id: 'image-b', fileName: 'b.png', variant: 'b' }
    ] }]
    await setup.load()
    await Vue.nextTick()
    expect(setup.canStart.value).toBe(false)
    expect(setup.multipleImages.value).toBe(true)
    setup.itemImageIdLists.value.item = ['image-a', 'image-b']
    expect(setup.canStart.value).toBe(true)
    await setup.start()
    expect(fetch).toHaveBeenLastCalledWith(expect.any(String), expect.objectContaining({ body: expect.objectContaining({
      imageSelection: { globalVariants: [], itemImageIdLists: { item: ['image-a', 'image-b'] } }
    }) }))
  })

  it('defaults to single-image mode and trims selections when opting out', async () => {
    await setup.load()
    expect(setup.multipleImages.value).toBe(false)
    activeDefinition = { ...definition, inputs: { images: { level: 'REQUIRED', requiredForTargets: [], multiple: true }, xml: { level: 'NONE', requiredForTargets: [] } } }
    await setup.load()
    await Vue.nextTick()
    setup.globalVariants.value = ['a', 'b']
    setup.itemImageIdLists.value.item = ['image-a', 'image-b']
    activeDefinition = { ...activeDefinition, inputs: { ...activeDefinition.inputs, images: { level: 'REQUIRED', requiredForTargets: [] } } }
    await setup.load()
    await Vue.nextTick()
    expect(setup.multipleImages.value).toBe(false)
    expect(setup.globalVariants.value).toEqual(['a'])
    expect(setup.itemImageIdLists.value.item).toEqual(['image-a'])
  })

  it('blocks submission if dynamic parameter discovery fails', async () => {
    activeDefinition = { ...definition, parameters: { destination: { type: 'string', allowedValues: { provider: 'destinations' } } } }
    await setup.load()
    await Vue.nextTick()
    await Vue.nextTick()
    expect(setup.discoveryError.value).toBeTruthy()
    expect(setup.canStart.value).toBe(false)
  })
})
