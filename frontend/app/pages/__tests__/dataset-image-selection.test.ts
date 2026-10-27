import { readFileSync } from 'node:fs'
import { ModuleKind, transpileModule } from 'typescript'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import * as inputRequirements from '@/utils/action-input-requirements'
import type { ActionDefinition, TrainingInputItem } from '@/types/action'

interface Setup {
  globalVariants: Vue.Ref<string[]>
  itemImageIdLists: Vue.Ref<Record<string, string[]>>
  selectedProcessorId: Vue.Ref<string>
  compatibleItems: Vue.ComputedRef<TrainingInputItem[]>
  variantOptions: Vue.ComputedRef<Array<{ value: string }>>
  selectedImageIds: (item: TrainingInputItem) => string[]
  canStart: Vue.ComputedRef<boolean>
  startTraining?: () => Promise<void>
  startEvaluation?: () => Promise<void>
}
const splits = { train: 'REQUIRED', val: 'NONE', test: 'NONE' } as const
const definition: ActionDefinition = {
  id: 'binary', processorKey: 'binary', name: 'Binary', description: null,
  yaml: '', endpointUrl: '', endpointTimeoutSeconds: 30, kind: 'TRAINING', executeRole: 'CURATOR',
  lockMode: 'NONE', tags: [], targets: ['PAGE'],
  inputs: { images: { level: 'REQUIRED', requiredForTargets: [], imageTypes: ['BINARY'] }, xml: { level: 'REQUIRED', requiredForTargets: [] } },
  acceptsImages: true, acceptsXml: true, outputsImages: false, outputsXml: false, outputsFiles: false,
  overwrites: {}, enabled: true, global: true, created: '', updated: '', trainingSplits: splits,
  evaluation: { profile: 'test', profileVersion: 1, splits }, parameters: {}
}
const item: TrainingInputItem = {
  itemId: 'item', sourcePageId: 'page', pageName: 'Page', split: 'TRAIN', status: 'READY',
  brokenReason: null, xmlAvailable: true, images: [
    { id: 'pending', fileName: 'scan.png', variant: 'a-pending', imageType: null },
    { id: 'color', fileName: 'color.png', variant: 'original', imageType: 'COLOR' },
    { id: 'binary', fileName: 'binary.png', variant: 'processed', imageType: 'BINARY' }
  ]
}

for (const kind of ['training', 'evaluation'] as const) {
  const descriptor = parse(readFileSync(new URL(`../${kind}/new.vue`, import.meta.url), 'utf8')).descriptor
  const compiled = transpileModule(compileScript(descriptor, { id: `${kind}-image-selection-test` }).content, {
    compilerOptions: { module: ModuleKind.CommonJS }
  }).outputText
  const component: { default?: { setup: (props: unknown, context: unknown) => Promise<Setup> } } = {}
  new Function('require', 'exports', compiled)((name: string) => {
    if (name === 'vue') return { ...Vue, withAsyncContext: (fn: () => unknown) => [fn(), () => {}] }
    if (name === '@/utils/action-input-requirements') return inputRequirements
    throw new Error(`Unexpected import ${name}`)
  }, component)

  describe(`${kind} image choices`, () => {
    const fetch = vi.fn()
    let scope: Vue.EffectScope
    let setup: Setup
    let inputs: TrainingInputItem[]
    let processors: Vue.Ref<ActionDefinition[]>

    async function create() {
      setup = await component.default!.setup({}, { expose: () => {} })
      await Vue.nextTick()
      await Vue.nextTick()
    }
    beforeEach(() => {
      vi.clearAllMocks()
      scope = Vue.effectScope()
      inputs = structuredClone([item])
      processors = Vue.ref([structuredClone(definition), { ...structuredClone(definition), id: 'color', inputs: { ...definition.inputs, images: { ...definition.inputs.images, imageTypes: ['COLOR'] } } }])
      vi.stubGlobal('watch', (...args: unknown[]) => scope.run(() => Reflect.apply(Vue.watch, undefined, args)))
      vi.stubGlobal('useWorkspaceBootstrap', async () => {})
      vi.stubGlobal('useWorkspaceStore', () => ({ selectedWorkspaceId: 'workspace' }))
      vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
      vi.stubGlobal('navigateTo', vi.fn())
      vi.stubGlobal('useFetch', async (url: () => string) => ({ data: url().endsWith('/processors') ? processors : Vue.ref([{ id: 'dataset', name: 'Dataset' }]) }))
      vi.stubGlobal('$fetch', fetch)
      fetch.mockImplementation(async (url: string, options?: { method?: string }) => {
        if (options?.method === 'POST') return { run: { id: 'run', status: 'QUEUED' } }
        if (url.endsWith('/inputs')) return { datasetId: 'dataset', datasetName: 'Dataset', items: inputs }
        return { items: [] }
      })
    })
    afterEach(() => {
      scope.stop()
      vi.unstubAllGlobals()
    })

    it('offers matching defaults and submits the displayed variant', async () => {
      await create()
      expect(setup.variantOptions.value.map(option => option.value)).toEqual(['processed'])
      expect(setup.globalVariants.value).toEqual(['processed'])
      expect(setup.selectedImageIds(setup.compatibleItems.value[0]!)).toEqual(['binary'])
      expect(setup.canStart.value).toBe(true)
      await (setup.startTraining ?? setup.startEvaluation)!()
      expect(fetch).toHaveBeenLastCalledWith(expect.stringContaining('/runs'), expect.objectContaining({ body: expect.objectContaining({
        imageSelection: { globalVariants: ['processed'], itemImageIdLists: { item: ['binary'] } }
      }) }))
    })

    it('updates defaults and removes incompatible overrides when switching Actions', async () => {
      await create()
      setup.itemImageIdLists.value.item = ['binary']
      setup.selectedProcessorId.value = 'color'
      await Vue.nextTick()
      expect(setup.globalVariants.value).toEqual(['original'])
      expect(setup.variantOptions.value.map(option => option.value)).toEqual(['original'])
      expect(setup.selectedImageIds(setup.compatibleItems.value[0]!)).toEqual(['color'])
      expect(setup.canStart.value).toBe(true)
    })

    it('selects all matching images in multiple mode even when the definition marks images optional', async () => {
      processors.value[0]!.inputs.images = { level: 'OPTIONAL', requiredForTargets: [], imageTypes: ['BINARY'], multiple: true }
      inputs[0]!.images.push({ id: 'other', fileName: 'other.png', variant: 'another', imageType: 'BINARY' })
      await create()
      expect(setup.globalVariants.value).toEqual(['another', 'processed'])
      expect(setup.selectedImageIds(setup.compatibleItems.value[0]!)).toEqual(['binary', 'other'])
      expect(setup.variantOptions.value.map(option => option.value)).toEqual(['another', 'processed'])
      expect(setup.canStart.value).toBe(true)
    })

    it('chooses one stable default when several files share a variant in single-image mode', async () => {
      inputs[0]!.images.push({ id: 'other', fileName: 'a.png', variant: 'processed', imageType: 'BINARY' })
      await create()
      expect(setup.globalVariants.value).toEqual([])
      expect(setup.selectedImageIds(setup.compatibleItems.value[0]!)).toEqual(['other'])
      expect(setup.canStart.value).toBe(true)
      await (setup.startTraining ?? setup.startEvaluation)!()
      expect(fetch).toHaveBeenLastCalledWith(expect.stringContaining('/runs'), expect.objectContaining({ body: expect.objectContaining({
        imageSelection: { globalVariants: [], itemImageIdLists: { item: ['other'] } }
      }) }))
    })

    it('keeps pending-only inputs available for server validation without offering them as confirmed matches', async () => {
      inputs = [{ ...item, images: [item.images[0]!] }]
      await create()
      expect(setup.variantOptions.value).toEqual([])
      expect(setup.globalVariants.value).toEqual([])
      expect(setup.selectedImageIds(setup.compatibleItems.value[0]!)).toEqual([])
      expect(setup.canStart.value).toBe(true)
    })
  })
}
