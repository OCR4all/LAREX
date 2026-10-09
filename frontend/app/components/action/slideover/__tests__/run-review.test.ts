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
  view: Vue.Ref<'choose' | 'configure' | 'history'>
  viewHeader: Vue.Ref<HTMLElement | null>
  canStart: Vue.ComputedRef<boolean>
  scope: Vue.Ref<'all' | 'selection'>
  targetSummary: Vue.ComputedRef<string>
  selectedImageVariant: Vue.Ref<string>
  imageVariantMode: Vue.Ref<'global' | 'perPage'>
  fallbackImage: Vue.Ref<boolean>
  scopeWarnings: Vue.ComputedRef<Array<{ title: string }>>
  imageWarnings: Vue.ComputedRef<Array<{ title: string }>>
  scopedPages: Vue.ComputedRef<unknown[]>
  lockNote: Vue.ComputedRef<string | null>
  changeAction: () => void
  openHistory: () => void
  backFromHistory: () => void
  actionSearch: Vue.Ref<string>
  selectedActionTags: Vue.Ref<string[]>
  actionTablePage: Vue.Ref<number>
  actionNameDescending: Vue.Ref<boolean>
  selectedProcessorId: Vue.Ref<string>
  selectedProcessor: Vue.ComputedRef<ExecutableActionProcessor | null>
  filteredActionProcessors: Vue.ComputedRef<ExecutableActionProcessor[]>
  visibleActionProcessors: Vue.ComputedRef<ExecutableActionProcessor[]>
  actionTagOptions: Vue.ComputedRef<string[]>
  selectAction: (item: ExecutableActionProcessor) => void
  clearActionFilters: () => void
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
  endpointTimeoutSeconds: 30, kind: 'PROCESSING', executeRole: 'CURATOR', lockMode: 'PAGES', tags: [], targets: ['PAGE'],
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
    expect(setup.view.value).toBe('choose')
    setup.processors.value = [{ assignmentId: null, processor, executable: true, blockedReason: null }]
    setup.selectAction(setup.processors.value[0]!)
  })

  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('always keeps the chooser open until an executable Action is explicitly chosen', async () => {
    setup.changeAction()
    setup.processors.value = [{ assignmentId: null, processor, executable: true, blockedReason: null }]
    await Vue.nextTick()
    expect(setup.view.value).toBe('choose')
    expect(setup.canStart.value).toBe(false)
    await setup.startRun()
    expect(fetch).not.toHaveBeenCalled()
    setup.selectAction({ assignmentId: null, processor, executable: false, blockedReason: 'Unavailable' })
    expect(setup.view.value).toBe('choose')
    setup.selectAction(setup.processors.value[0]!)
    expect(setup.view.value).toBe('configure')
    expect(setup.canStart.value).toBe(true)
  })

  it('preserves configuration and chooser filters when changing Action or viewing history', async () => {
    await Vue.nextTick()
    setup.parameterValues.model = 'configured'
    setup.fallbackImage.value = true
    setup.imageVariantMode.value = 'perPage'
    setup.actionSearch.value = 'processor'
    setup.selectedActionTags.value = ['layout']
    await Vue.nextTick()
    setup.actionTablePage.value = 2
    setup.openHistory()
    expect(setup.view.value).toBe('history')
    expect(setup.canStart.value).toBe(false)
    setup.backFromHistory()
    expect(setup.view.value).toBe('configure')
    setup.changeAction()
    expect(setup.view.value).toBe('choose')
    setup.openHistory()
    setup.backFromHistory()
    expect(setup.view.value).toBe('choose')
    setup.selectAction(setup.processors.value[0]!)
    await Vue.nextTick()
    expect(setup.parameterValues.model).toBe('configured')
    expect(setup.fallbackImage.value).toBe(true)
    expect(setup.imageVariantMode.value).toBe('perPage')
    expect(setup.actionSearch.value).toBe('processor')
    expect(setup.selectedActionTags.value).toEqual(['layout'])
    expect(setup.actionTablePage.value).toBe(2)
  })

  it('initializes parameters when choosing a different Action', async () => {
    await Vue.nextTick()
    setup.parameterValues.model = 'old'
    const another = { assignmentId: null, processor: { ...processor, id: 'other', parameters: { count: { type: 'integer' as const, default: 3 } } }, executable: true, blockedReason: null }
    setup.processors.value = [...setup.processors.value, another]
    setup.changeAction()
    setup.selectAction(another)
    await Vue.nextTick()
    expect(setup.view.value).toBe('configure')
    expect(setup.parameterValues).toEqual({ count: 3 })
  })

  it('updates page scope, available variants and correctly placed input warnings', async () => {
    const scopedSetup = scope.run(() => exportsObject.default!.setup({
      workspaceId: 'workspace', projectId: 'project', pageIds: ['affected', 'safe'],
      pages: [
        { id: 'affected', name: 'Affected', imageCount: 1, xmlFileCount: 1, imageVariants: [{ id: 'image', fileName: 'image.png', variant: 'original' }] },
        { id: 'safe', name: 'Safe', imageCount: 0, xmlFileCount: 0 },
        { id: 'other', name: 'Other', imageCount: 1, xmlFileCount: 1, imageVariants: [{ id: 'scan', fileName: 'scan.png', variant: 'scan' }] }
      ]
    }, { expose: () => {}, emit }))!
    scopedSetup.processors.value = [{ assignmentId: null, executable: true, blockedReason: null, processor: {
      ...processor, inputs: { images: { level: 'REQUIRED', requiredForTargets: [] }, xml: { level: 'REQUIRED', requiredForTargets: [] } }
    } }]
    scopedSetup.selectAction(scopedSetup.processors.value[0]!)
    await Vue.nextTick()
    expect(scopedSetup.targetSummary.value).toBe('2 selected pages')
    expect(scopedSetup.scopedPages.value).toHaveLength(2)
    expect(scopedSetup.scopeWarnings.value[0]?.title).toContain('no XML')
    expect(scopedSetup.imageWarnings.value[0]?.title).toContain('no images')
    scopedSetup.scope.value = 'all'
    await Vue.nextTick()
    expect(scopedSetup.targetSummary.value).toBe('All pages (3)')
    expect(scopedSetup.scopedPages.value).toHaveLength(3)
    expect(scopedSetup.imageWarnings.value).toHaveLength(2)
    expect(scopedSetup.imageWarnings.value[1]?.title).toContain('variant is missing')
    scopedSetup.fallbackImage.value = true
    expect(scopedSetup.imageWarnings.value[1]?.title).toContain('fallback image')
  })

  it('preserves the fixed editor target without relying on separate pageIds', async () => {
    const editorSetup = scope.run(() => exportsObject.default!.setup({
      workspaceId: 'workspace', projectId: 'project',
      targetSummary: 'Current page',
      targetSelection: { type: 'PAGE', pages: [{ pageId: 'safe', regionIds: [], textLineIds: [] }] },
      pages: preview.pages.map(page => ({ id: page.pageId, name: page.name, xmlFileCount: 1, imageCount: 1 }))
    }, { expose: () => {}, emit }))!
    editorSetup.processors.value = [{ assignmentId: null, processor, executable: true, blockedReason: null }]
    editorSetup.selectAction(editorSetup.processors.value[0]!)
    await Vue.nextTick()
    expect(editorSetup.targetSummary.value).toBe('Current page')
    expect(editorSetup.scopedPages.value).toHaveLength(1)
    expect(editorSetup.canStart.value).toBe(true)
  })

  it('returns to the chooser when the configured Action becomes unavailable', async () => {
    await Vue.nextTick()
    setup.processors.value = []
    await Vue.nextTick()
    expect(setup.view.value).toBe('choose')
    expect(setup.canStart.value).toBe(false)
  })

  it('focuses the view heading and omits the lock note for unlocked Actions', async () => {
    const heading = { setAttribute: vi.fn(), focus: vi.fn() }
    setup.viewHeader.value = { querySelector: () => heading } as unknown as HTMLElement
    setup.openHistory()
    await Vue.nextTick()
    await Vue.nextTick()
    expect(heading.setAttribute).toHaveBeenCalledWith('tabindex', '-1')
    expect(heading.focus).toHaveBeenCalled()
    setup.processors.value = [{ assignmentId: null, processor: { ...processor, lockMode: 'NONE' }, executable: true, blockedReason: null }]
    expect(setup.lockNote.value).toBeNull()
  })

  it('blocks navigation and duplicate previews while checking an Action run', async () => {
    let complete!: (value: ActionRunImpact) => void
    fetch.mockReturnValueOnce(new Promise<ActionRunImpact>((resolve) => {
      complete = resolve
    }))
    const pending = setup.startRun()
    setup.changeAction()
    setup.openHistory()
    expect(setup.view.value).toBe('configure')
    await setup.startRun()
    expect(fetch).toHaveBeenCalledTimes(1)
    complete(preview)
    await pending
  })

  it('filters by any tag and searches name or description without losing selection or configuration', async () => {
    const tagged = { ...processor, tags: ['layout', 'segmentation'] }
    const another = { ...processor, id: 'other', name: 'Export', description: 'Named entities', tags: ['export'] }
    setup.processors.value = [
      { assignmentId: null, processor: tagged, executable: true, blockedReason: null },
      { assignmentId: null, processor: another, executable: true, blockedReason: null }
    ]
    await Vue.nextTick()
    setup.selectedProcessorId.value = processor.id
    await Vue.nextTick()
    setup.parameterValues.quality = 'configured'
    setup.selectedActionTags.value = ['export', 'missing']
    await Vue.nextTick()
    expect(setup.filteredActionProcessors.value.map(item => item.processor.id)).toEqual(['other'])
    expect(setup.selectedProcessor.value?.processor.id).toBe(processor.id)
    expect(setup.parameterValues.quality).toBe('configured')
    setup.actionSearch.value = 'ENTITIES'
    await Vue.nextTick()
    expect(setup.filteredActionProcessors.value).toHaveLength(1)
    setup.actionSearch.value = 'absent'
    await Vue.nextTick()
    expect(setup.filteredActionProcessors.value).toHaveLength(0)
    expect(setup.selectedProcessor.value?.processor.id).toBe(processor.id)
    setup.clearActionFilters()
    await Vue.nextTick()
    expect(setup.filteredActionProcessors.value).toHaveLength(2)
    expect(setup.actionTagOptions.value).toEqual(['export', 'layout', 'segmentation'])
  })

  it('shows blocked Actions but prevents selection and excludes incompatible targets', async () => {
    const blocked = { assignmentId: null, processor: { ...processor, id: 'blocked', tags: ['restricted'] }, executable: false, blockedReason: 'Curator required' }
    setup.processors.value = [
      { assignmentId: null, processor, executable: true, blockedReason: null }, blocked,
      { assignmentId: null, processor: { ...processor, id: 'region', tags: ['region'], targets: ['REGION'] }, executable: true, blockedReason: null }
    ]
    await Vue.nextTick()
    const selected = setup.selectedProcessorId.value
    setup.selectAction(blocked)
    expect(setup.selectedProcessorId.value).toBe(selected)
    expect(setup.filteredActionProcessors.value).toHaveLength(2)
    expect(setup.actionTagOptions.value).toEqual(['restricted'])
  })

  it('sorts and paginates Actions and resets the page when filtering', async () => {
    setup.processors.value = Array.from({ length: 12 }, (_, index) => ({
      assignmentId: null, processor: { ...processor, id: `p${index}`, name: `Action ${String(index).padStart(2, '0')}` }, executable: true, blockedReason: null
    }))
    await Vue.nextTick()
    expect(setup.visibleActionProcessors.value).toHaveLength(10)
    setup.actionTablePage.value = 2
    await Vue.nextTick()
    expect(setup.visibleActionProcessors.value).toHaveLength(2)
    const selected = setup.selectedProcessorId.value
    setup.actionNameDescending.value = true
    await Vue.nextTick()
    expect(setup.actionTablePage.value).toBe(1)
    expect(setup.visibleActionProcessors.value[0]?.processor.id).toBe('p11')
    setup.actionTablePage.value = 2
    setup.actionSearch.value = 'Action 01'
    await Vue.nextTick()
    expect(setup.actionTablePage.value).toBe(1)
    expect(setup.visibleActionProcessors.value).toHaveLength(1)
    expect(setup.selectedProcessorId.value).toBe(selected)
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
