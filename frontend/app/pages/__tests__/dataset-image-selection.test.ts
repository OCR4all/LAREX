import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { useDatasetRun } from '@/composables/use-dataset-run'
import type { ActionDefinition, ActionParameterDefinition, TrainingInputItem } from '@/types/action'
import type { DatasetSummary } from '@/types/dataset'

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
  brokenReason: null, xmlAvailable: true, workflowState: 'DONE', usedForTraining: false, images: [
    { id: 'pending', fileName: 'scan.png', variant: 'a-pending', imageType: null },
    { id: 'color', fileName: 'color.png', variant: 'original', imageType: 'COLOR' },
    { id: 'binary', fileName: 'binary.png', variant: 'processed', imageType: 'BINARY' }
  ]
}
const datasets = [{ id: 'first', name: 'First' }, { id: 'second', name: 'Second' }] as DatasetSummary[]

function withoutImageTypes(action: ActionDefinition): ActionDefinition {
  return { ...action, inputs: { ...action.inputs, images: { level: 'REQUIRED', requiredForTargets: [] } } }
}

for (const kind of ['training', 'evaluation'] as const) {
  describe(`${kind} run form`, () => {
    const fetch = vi.fn()
    let scope: Vue.EffectScope
    let run: ReturnType<typeof useDatasetRun>
    let inputs: TrainingInputItem[]
    let processors: Vue.Ref<ActionDefinition[]>

    async function create(initialDatasetId?: string) {
      run = scope.run(() => useDatasetRun({
        kind, workspaceId: Vue.ref('workspace'), datasets: Vue.ref(datasets), processors, initialDatasetId
      }))!
      await new Promise(resolve => setTimeout(resolve))
      await Vue.nextTick()
    }

    function lastRunBody() {
      const call = fetch.mock.calls.findLast(([url]) => String(url).endsWith('/runs'))
      return call?.[1].body
    }

    beforeEach(() => {
      vi.clearAllMocks()
      scope = Vue.effectScope()
      inputs = structuredClone([item])
      processors = Vue.ref([
        structuredClone(definition),
        { ...structuredClone(definition), id: 'color', inputs: { ...definition.inputs, images: { ...definition.inputs.images, imageTypes: ['COLOR'] } } }
      ])
      vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
      vi.stubGlobal('navigateTo', vi.fn())
      vi.stubGlobal('$fetch', fetch)
      fetch.mockImplementation(async (url: string, options?: { method?: string }) => {
        if (options?.method === 'POST') return { run: { id: 'run', status: 'QUEUED' } }
        if (url.endsWith('/inputs')) return { datasetId: 'first', datasetName: 'First', items: inputs }
        return { items: [] }
      })
    })
    afterEach(() => {
      scope.stop()
      vi.unstubAllGlobals()
    })

    it('offers matching defaults and submits the displayed variant', async () => {
      await create()
      expect(run.variantOptions.value.map(option => option.value)).toEqual(['processed'])
      expect(run.globalVariants.value).toEqual(['processed'])
      expect(run.selectedImageIds(run.usableItems.value[0]!)).toEqual(['binary'])
      expect(run.canStart.value).toBe(true)
      await run.start()
      expect(lastRunBody().imageSelection).toEqual({ globalVariants: ['processed'], itemImageIdLists: { item: ['binary'] } })
    })

    it('updates defaults and removes incompatible overrides when switching Actions', async () => {
      await create()
      run.itemImageIdLists.value.item = ['binary']
      run.selectedProcessorId.value = 'color'
      await Vue.nextTick()
      expect(run.globalVariants.value).toEqual(['original'])
      expect(run.variantOptions.value.map(option => option.value)).toEqual(['original'])
      expect(run.selectedImageIds(run.usableItems.value[0]!)).toEqual(['color'])
      expect(run.canStart.value).toBe(true)
    })

    it('selects all matching images in multiple mode even when the definition marks images optional', async () => {
      processors.value[0]!.inputs.images = { level: 'OPTIONAL', requiredForTargets: [], imageTypes: ['BINARY'], multiple: true }
      inputs[0]!.images.push({ id: 'other', fileName: 'other.png', variant: 'another', imageType: 'BINARY' })
      await create()
      expect(run.globalVariants.value).toEqual(['another', 'processed'])
      expect(run.selectedImageIds(run.usableItems.value[0]!)).toEqual(['binary', 'other'])
      expect(run.variantOptions.value.map(option => option.value)).toEqual(['another', 'processed'])
      expect(run.canStart.value).toBe(true)
    })

    it('chooses one stable default when several files share a variant in single-image mode', async () => {
      inputs[0]!.images.push({ id: 'other', fileName: 'a.png', variant: 'processed', imageType: 'BINARY' })
      await create()
      expect(run.globalVariants.value).toEqual([])
      expect(run.selectedImageIds(run.usableItems.value[0]!)).toEqual(['other'])
      expect(run.canStart.value).toBe(true)
      await run.start()
      expect(lastRunBody().imageSelection).toEqual({ globalVariants: [], itemImageIdLists: { item: ['other'] } })
    })

    it('keeps pending-only inputs available for server validation without offering them as confirmed matches', async () => {
      inputs = [{ ...item, images: [item.images[0]!] }]
      await create()
      expect(run.variantOptions.value).toEqual([])
      expect(run.globalVariants.value).toEqual([])
      expect(run.selectedImageIds(run.usableItems.value[0]!)).toEqual([])
      expect(run.canStart.value).toBe(true)
    })

    it('picks a variant by itself when an Action declares no image types', async () => {
      processors.value = [withoutImageTypes(processors.value[0]!)]
      await create()
      expect(run.blockers.value).toEqual([])
      expect(run.globalVariants.value).toEqual(['a-pending'])
      expect(run.canStart.value).toBe(true)
    })

    it('still starts when pages differ and no variant is shared', async () => {
      processors.value = [withoutImageTypes(processors.value[0]!)]
      inputs = [
        { ...item, itemId: 'one', images: [item.images[1]!] },
        { ...item, itemId: 'two', images: [item.images[2]!] }
      ]
      await create()
      expect(run.globalVariants.value).toEqual([])
      await run.start()
      expect(lastRunBody().imageSelection.itemImageIdLists).toEqual({ one: ['color'], two: ['binary'] })
    })

    it('names the pages it leaves out instead of blocking on unusable pages', async () => {
      inputs = [item, { ...item, itemId: 'no-xml', xmlAvailable: false }, { ...item, itemId: 'broken', status: 'BROKEN', brokenReason: 'Source page removed' }]
      await create()
      expect(run.skippedReasons.value).toEqual([{ reason: 'No PAGE XML', count: 1 }, { reason: 'Source page removed', count: 1 }])
      expect(run.canStart.value).toBe(true)
      await run.start()
      expect(lastRunBody().selection).toEqual({ mode: 'SELECTED', itemIds: ['item'] })
    })

    it('sends ALL when every page in a used split is usable, and reports the unused splits as held out', async () => {
      inputs = [item, { ...item, itemId: 'test', split: 'TEST' }]
      await create()
      expect(run.splitSummary.value.find(row => row.split === 'TEST')?.unusedCount).toBe(1)
      await run.start()
      expect(lastRunBody().selection).toEqual({ mode: 'ALL' })
    })

    it('explains a missing required split', async () => {
      inputs = [{ ...item, split: 'VAL' }]
      processors.value[0]!.trainingSplits = { train: 'REQUIRED', val: 'OPTIONAL', test: 'NONE' }
      processors.value[0]!.evaluation!.splits = { train: 'REQUIRED', val: 'OPTIONAL', test: 'NONE' }
      await create()
      const hint = kind === 'evaluation' ? ' Choose All selected pages to evaluate every page.' : ''
      expect(run.blockers.value).toEqual([`This Action needs at least one training page.${hint}`])
      expect(run.canStart.value).toBe(false)
    })

    it('starts choosing pages from everything usable', async () => {
      inputs = [item, { ...item, itemId: 'second' }]
      await create()
      run.selectionMode.value = 'SELECTED'
      await Vue.nextTick()
      expect(run.activeItems.value).toHaveLength(2)
      run.setItemSelected('second', false)
      expect(run.activeItems.value.map(active => active.itemId)).toEqual(['item'])
      run.setItemsSelected(run.usableItems.value, false)
      expect(run.blockers.value).toEqual(['Select at least one page.'])
    })

    it('applies pages chosen in a project once they load', async () => {
      inputs = [item, { ...item, itemId: 'other', sourcePageId: 'other-page' }]
      run = scope.run(() => useDatasetRun({
        kind, workspaceId: Vue.ref('workspace'), datasets: Vue.ref(datasets), processors, initialPageIds: ['other-page']
      }))!
      await new Promise(resolve => setTimeout(resolve))
      await Vue.nextTick()
      expect(run.selectionMode.value).toBe('SELECTED')
      expect(run.activeItems.value.map(active => active.itemId)).toEqual(['other'])
    })

    describe('with project pages', () => {
      async function createForProject() {
        run = scope.run(() => useDatasetRun({
          kind, workspaceId: Vue.ref('workspace'), datasets: Vue.ref(datasets), processors, usesProjectPages: Vue.ref(true)
        }))!
        await new Promise(resolve => setTimeout(resolve))
        await Vue.nextTick()
      }

      it('starts from the pages marked Done and sends them by name', async () => {
        inputs = [item, { ...item, itemId: 'open', workflowState: 'OPEN' }]
        await createForProject()
        expect(run.selectionMode.value).toBe('REVIEWED')
        expect(run.activeItems.value.map(active => active.itemId)).toEqual(['item'])
        await run.start()
        expect(lastRunBody().selection).toEqual({ mode: 'SELECTED', itemIds: ['item'] })
      })

      it('points to All usable pages when nothing is marked Done', async () => {
        inputs = [{ ...item, workflowState: 'OPEN' }]
        await createForProject()
        expect(run.blockers.value).toEqual(['No pages are marked Done yet. Choose All usable pages to include unreviewed pages.'])
        run.selectionMode.value = 'ALL'
        await Vue.nextTick()
        expect(run.canStart.value).toBe(true)
      })

      it('keeps the Done pages when switching to choosing pages', async () => {
        inputs = [item, { ...item, itemId: 'open', workflowState: 'OPEN' }]
        await createForProject()
        run.selectionMode.value = 'SELECTED'
        await Vue.nextTick()
        expect(run.activeItems.value.map(active => active.itemId)).toEqual(['item'])
      })
    })

    it('asks for required parameters by their readable name', async () => {
      const required: ActionParameterDefinition = { type: 'string', required: true }
      processors.value[0]!.parameters = { modelName: required }
      await create()
      expect(run.blockers.value).toEqual(['Enter model name.'])
      run.parameters.value.modelName = 'catmus'
      expect(run.canStart.value).toBe(true)
    })

    it('preselects the dataset from the link', async () => {
      await create('second')
      expect(run.selectedDatasetId.value).toBe('second')
      expect(fetch).toHaveBeenCalledWith(expect.stringContaining(`/actions/${kind}/datasets/second/inputs`))
    })
  })
}

describe('evaluation page scope', () => {
  const testOnly = { train: 'NONE', val: 'NONE', test: 'REQUIRED' } as const
  const action: ActionDefinition = { ...definition, evaluation: { profile: 'test', profileVersion: 1, splits: testOnly } }
  const pages: TrainingInputItem[] = [
    { ...item, itemId: 'train', split: 'TRAIN', usedForTraining: true },
    { ...item, itemId: 'val', split: 'VAL', workflowState: 'OPEN' },
    { ...item, itemId: 'test', split: 'TEST' }
  ]
  const fetch = vi.fn()
  let scope: Vue.EffectScope

  async function create(
    items: TrainingInputItem[],
    options: { processor?: ActionDefinition, initialPageIds?: string[], usesProjectPages?: boolean } = {}
  ) {
    fetch.mockImplementation(async (url: string, request?: { method?: string }) => {
      if (request?.method === 'POST') return { run: { id: 'run', status: 'QUEUED' } }
      if (url.endsWith('/inputs')) return { datasetId: 'first', datasetName: 'First', items }
      return { items: [] }
    })
    const run = scope.run(() => useDatasetRun({
      kind: 'evaluation', workspaceId: Vue.ref('workspace'), datasets: Vue.ref(datasets),
      processors: Vue.ref([options.processor ?? action]), initialPageIds: options.initialPageIds,
      usesProjectPages: Vue.ref(options.usesProjectPages ?? false)
    }))!
    await new Promise(resolve => setTimeout(resolve))
    await Vue.nextTick()
    return run
  }

  function lastRunBody() {
    return fetch.mock.calls.findLast(([url]) => String(url).endsWith('/runs'))?.[1].body
  }

  beforeEach(() => {
    vi.clearAllMocks()
    scope = Vue.effectScope()
    vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
    vi.stubGlobal('navigateTo', vi.fn())
    vi.stubGlobal('$fetch', fetch)
  })
  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('uses held-out pages by default', async () => {
    const run = await create(pages)
    expect(run.activeItems.value.map(active => active.itemId)).toEqual(['test'])
    expect(run.splitSummary.value.find(row => row.split === 'TRAIN')?.unusedCount).toBe(1)
    await run.start()
    expect(lastRunBody().allPages).toBeUndefined()
  })

  it('counts every page in the evaluated split when all pages are chosen', async () => {
    const run = await create(pages)
    run.evaluateAllPages.value = true
    await Vue.nextTick()
    expect(run.activeItems.value).toHaveLength(3)
    expect(run.splitSummary.value.find(row => row.split === 'TEST')?.count).toBe(3)
    expect(run.splitSummary.value.find(row => row.split === 'TRAIN')?.unusedCount).toBe(0)
    await run.start()
    expect(lastRunBody().allPages).toBe(true)
    expect(lastRunBody().selection).toEqual({ mode: 'ALL' })
  })

  it('points to all pages when no page is held out', async () => {
    const run = await create([pages[0]!])
    expect(run.blockers.value).toEqual(['There are no pages this Action can use. Choose All selected pages to evaluate every page.'])
    run.evaluateAllPages.value = true
    await Vue.nextTick()
    expect(run.canStart.value).toBe(true)
  })

  it('evaluates pages picked on the project page whatever their split', async () => {
    const run = await create(pages, { initialPageIds: ['page'], usesProjectPages: true })
    expect(run.selectionMode.value).toBe('SELECTED')
    expect(run.activeItems.value).toHaveLength(3)
  })

  it('warns only about used pages that are part of the run', async () => {
    const run = await create(pages)
    expect(run.trainingOverlapCount.value).toBe(0)
    run.evaluateAllPages.value = true
    await Vue.nextTick()
    expect(run.trainingOverlapCount.value).toBe(1)
  })

  it('does not offer all pages when the Action needs several splits or already reads every split', async () => {
    const several = await create(pages, { processor: { ...action, evaluation: { profile: 'test', profileVersion: 1, splits: { train: 'REQUIRED', val: 'NONE', test: 'REQUIRED' } } } })
    expect(several.allPagesSplit.value).toBeNull()
    const every = await create(pages, { processor: { ...action, evaluation: { profile: 'test', profileVersion: 1, splits: { train: 'OPTIONAL', val: 'OPTIONAL', test: 'REQUIRED' } } } })
    expect(every.allPagesSplit.value).toBeNull()
  })

  it('never offers all pages for training', async () => {
    fetch.mockResolvedValue({ items: [] })
    const run = scope.run(() => useDatasetRun({
      kind: 'training', workspaceId: Vue.ref('workspace'), datasets: Vue.ref(datasets), processors: Vue.ref([action])
    }))!
    await Vue.nextTick()
    expect(run.allPagesSplit.value).toBeNull()
  })

  it('saves the chosen pages from every split', async () => {
    const run = await create(pages, { usesProjectPages: true })
    expect(run.selectionMode.value).toBe('REVIEWED')
    expect(run.saveableItems.value.map(saved => saved.itemId)).toEqual(['train', 'test'])
    run.selectionMode.value = 'ALL'
    await Vue.nextTick()
    expect(run.saveableItems.value).toHaveLength(3)
  })
})

describe('evaluation without images', () => {
  it('runs on PAGE XML alone', async () => {
    const fetch = vi.fn(async (url: string, options?: { method?: string }) => {
      if (options?.method === 'POST') return { run: { id: 'run', status: 'RUNNING' } }
      if (url.endsWith('/inputs')) return { datasetId: 'first', datasetName: 'First', items: [{ ...item, images: [] }] }
      return { items: [] }
    })
    vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
    vi.stubGlobal('navigateTo', vi.fn())
    vi.stubGlobal('$fetch', fetch)
    const scope = Vue.effectScope()
    const action = { ...definition, inputs: { ...definition.inputs, images: { level: 'NONE' as const, requiredForTargets: [] } } }
    const run = scope.run(() => useDatasetRun({
      kind: 'evaluation', workspaceId: Vue.ref('workspace'), datasets: Vue.ref(datasets), processors: Vue.ref([action])
    }))!
    await new Promise(resolve => setTimeout(resolve))

    expect(run.acceptsImages.value).toBe(false)
    expect(run.canStart.value).toBe(true)
    scope.stop()
    vi.unstubAllGlobals()
  })
})
