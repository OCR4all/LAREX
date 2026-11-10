import type { Ref } from 'vue'
import {
  actionImageVariantOptions,
  actionInputLevelForTarget,
  compatibleActionImages,
  defaultActionImageVariants,
  selectableActionImages
} from '@/utils/action-input-requirements'
import { actionParameterDefaultValue, actionParameterLabel, applyDiscoveredActionParameterDefaults, isBlankActionParameterValue } from '@/utils/action-parameter-values'
import { extractApiErrorMessage } from '@/utils/api-error'
import type {
  ActionDefinitionResponse,
  ActionInputLevel,
  ActionParameterChoice,
  ActionParameterValue,
  StartActionRunResponse,
  TrainingInputImage,
  TrainingInputItem,
  TrainingInputResponse
} from '@/types/action'
import type { DatasetDetail, DatasetSummary } from '@/types/dataset'

export type DatasetRunKind = 'training' | 'evaluation'
export type DatasetSplit = TrainingInputItem['split']
export type DatasetRunSelectionMode = 'ALL' | 'REVIEWED' | 'SELECTED'

export const DATASET_SPLIT_LABELS: Record<DatasetSplit, string> = {
  TRAIN: 'Training',
  VAL: 'Validation',
  TEST: 'Test'
}

const DATASET_SPLITS = Object.keys(DATASET_SPLIT_LABELS) as DatasetSplit[]
const ALL_PAGES_SPLIT_PREFERENCE: DatasetSplit[] = ['TEST', 'VAL', 'TRAIN']

export function pageCountLabel(count: number): string {
  return `${count} page${count === 1 ? '' : 's'}`
}

export function useDatasetRun(options: {
  kind: DatasetRunKind
  workspaceId: Ref<string | null | undefined>
  datasets: Ref<DatasetSummary[]>
  processors: Ref<ActionDefinitionResponse[]>
  initialDatasetId?: string
  /** Pages chosen before opening the form, as source page IDs. */
  initialPageIds?: string[]
  /** Project pages are not curated like a dataset, so they start from the pages marked Done. */
  usesProjectPages?: Ref<boolean>
}) {
  const { kind, workspaceId, datasets, processors, initialDatasetId, usesProjectPages } = options
  let pendingPageIds = options.initialPageIds?.length ? new Set(options.initialPageIds) : null
  const toast = useToast()

  const selectedDatasetId = ref<string | undefined>(
    datasets.value.find(dataset => dataset.id === initialDatasetId)?.id ?? datasets.value[0]?.id
  )
  const selectedProcessorId = ref<string | undefined>(processors.value[0]?.id)
  const selectionMode = ref<DatasetRunSelectionMode>('ALL')
  const evaluateAllPages = ref(kind === 'evaluation' && Boolean(options.initialPageIds?.length))
  const selectedItemIds = ref<Set<string>>(new Set())
  const globalVariants = ref<string[]>([])
  const itemImageIdLists = ref<Record<string, string[]>>({})
  const parameters = ref<Record<string, ActionParameterValue>>({})
  const parameterChoices = ref<Record<string, ActionParameterChoice[]>>({})
  const inputs = ref<TrainingInputResponse | null>(null)
  const datasetDetail = ref<DatasetDetail | null>(null)
  const loadingInputs = ref(false)
  const inputError = ref<string>()
  const starting = ref(false)
  let inputRequestSequence = 0
  let parameterRequestSequence = 0

  const selectedDataset = computed(() => datasets.value.find(dataset => dataset.id === selectedDatasetId.value))
  const selectedProcessor = computed(() => processors.value.find(processor => processor.id === selectedProcessorId.value))
  const allItems = computed(() => inputs.value?.items ?? [])

  const splitRequirements = computed(() => kind === 'training'
    ? selectedProcessor.value?.trainingSplits
    : selectedProcessor.value?.evaluation?.splits)
  // Training always receives image/XML pairs; evaluation follows the declared input level.
  const imageLevel = computed<ActionInputLevel>(() => kind === 'training' || !selectedProcessor.value
    ? 'REQUIRED'
    : actionInputLevelForTarget(selectedProcessor.value.inputs.images, 'PAGE'))
  const acceptsImages = computed(() => imageLevel.value !== 'NONE')
  const multipleImages = computed(() => selectedProcessor.value?.inputs.images.multiple === true)

  function splitLevel(split: DatasetSplit): ActionInputLevel {
    return splitRequirements.value?.[split.toLowerCase() as 'train' | 'val' | 'test'] ?? 'NONE'
  }

  // Keep in sync with allPagesSplit in ActionTrainingSnapshotService.
  const allPagesSplit = computed<DatasetSplit | null>(() => {
    if (kind !== 'evaluation') return null
    const accepted = ALL_PAGES_SPLIT_PREFERENCE.filter(split => splitLevel(split) !== 'NONE')
    const required = accepted.filter(split => splitLevel(split) === 'REQUIRED')
    if (accepted.length === DATASET_SPLITS.length || required.length > 1) return null
    return required[0] ?? accepted[0] ?? null
  })
  const usesAllPages = computed(() => evaluateAllPages.value && allPagesSplit.value !== null)
  const allPagesHint = computed(() => allPagesSplit.value && !usesAllPages.value && allItems.value.length
    ? ' Choose All selected pages to evaluate every page.'
    : '')

  function runSplit(item: TrainingInputItem): DatasetSplit {
    return usesAllPages.value ? allPagesSplit.value! : item.split
  }

  function usesItem(item: TrainingInputItem): boolean {
    return splitLevel(runSplit(item)) !== 'NONE'
  }

  function compatibleImages(item: TrainingInputItem): TrainingInputImage[] {
    return acceptsImages.value ? compatibleActionImages(item.images, selectedProcessor.value?.inputs.images) : []
  }

  function selectableImages(item: TrainingInputItem): TrainingInputImage[] {
    return selectableActionImages(compatibleImages(item), selectedProcessor.value?.inputs.images, true)
  }

  function skipReason(item: TrainingInputItem): string | null {
    if (item.status !== 'READY') return item.brokenReason || 'Dataset page is broken'
    if (!item.xmlAvailable) return 'No PAGE XML'
    if (imageLevel.value === 'REQUIRED' && compatibleImages(item).length === 0) return 'No usable image'
    return null
  }

  const itemsInUsedSplits = computed(() => allItems.value.filter(usesItem))
  const usableItems = computed(() => itemsInUsedSplits.value.filter(item => skipReason(item) === null))
  const skippedItems = computed(() => itemsInUsedSplits.value.filter(item => skipReason(item) !== null))
  const reviewedItems = computed(() => usableItems.value.filter(item => item.workflowState === 'DONE'))
  const activeItems = computed(() => {
    if (selectionMode.value === 'ALL') return usableItems.value
    if (selectionMode.value === 'REVIEWED') return reviewedItems.value
    return usableItems.value.filter(item => selectedItemIds.value.has(item.itemId))
  })
  const trainingOverlapCount = computed(() => activeItems.value.filter(item => item.usedForTraining).length)

  const saveableItems = computed(() => {
    const items = allItems.value.filter(item => item.status === 'READY' && item.xmlAvailable)
    if (selectionMode.value === 'REVIEWED') return items.filter(item => item.workflowState === 'DONE')
    if (selectionMode.value === 'SELECTED') return items.filter(item => selectedItemIds.value.has(item.itemId))
    return items
  })

  const skippedReasons = computed(() => {
    const counts = new Map<string, number>()
    for (const item of skippedItems.value) {
      const reason = skipReason(item)!
      counts.set(reason, (counts.get(reason) ?? 0) + 1)
    }
    return [...counts.entries()].map(([reason, count]) => ({ reason, count })).sort((left, right) => right.count - left.count)
  })

  const splitSummary = computed(() => DATASET_SPLITS.map((split) => {
    const level = splitLevel(split)
    return {
      split,
      label: DATASET_SPLIT_LABELS[split],
      level,
      count: activeItems.value.filter(item => runSplit(item) === split).length,
      unusedCount: level === 'NONE' && !usesAllPages.value ? allItems.value.filter(item => item.split === split).length : 0
    }
  }))

  const tagCounts = computed(() => {
    const activeIds = new Set(activeItems.value.map(item => item.itemId))
    const counts: Record<string, number> = {}
    for (const item of datasetDetail.value?.items ?? []) {
      if (!activeIds.has(item.id)) continue
      for (const tag of item.sourcePageTags ?? []) counts[tag] = (counts[tag] ?? 0) + 1
    }
    return Object.entries(counts).sort((left, right) => right[1] - left[1])
  })

  const variantOptions = computed(() => actionImageVariantOptions(activeItems.value.flatMap(selectableImages)))

  function resolvedImages(item: TrainingInputItem): TrainingInputImage[] {
    const compatible = compatibleImages(item)
    const override = itemImageIdLists.value[item.itemId]
    if (override?.length) return compatible.filter(image => override.includes(image.id))
    if (globalVariants.value.length) return compatible.filter(image => globalVariants.value.includes(image.variant ?? ''))
    const confirmed = selectableImages(item)
    const images = confirmed.length ? confirmed : compatible
    return multipleImages.value ? images : images.slice(0, 1)
  }

  function selectedImageIds(item: TrainingInputItem): string[] {
    const selectableIds = new Set(selectableImages(item).map(image => image.id))
    return resolvedImages(item).map(image => image.id).filter(id => selectableIds.has(id))
  }

  const pagesMissingChosenImage = computed(() => activeItems.value.filter(item =>
    compatibleImages(item).length > 0 && resolvedImages(item).length === 0
  ))

  const emptySelectionMessage = computed(() => {
    if (!usableItems.value.length) return `There are no pages this Action can use.${allPagesHint.value}`
    if (selectionMode.value !== 'REVIEWED') return 'Select at least one page.'
    return allPagesHint.value && allItems.value.some(item => item.workflowState === 'DONE')
      ? `None of the held-out pages are marked Done.${allPagesHint.value}`
      : 'No pages are marked Done yet. Choose All usable pages to include unreviewed pages.'
  })

  const pageBlockers = computed(() => {
    if (loadingInputs.value || !selectedProcessor.value || !selectedDataset.value) return []
    if (inputError.value) return [inputError.value]
    const result: string[] = []
    if (!activeItems.value.length) {
      result.push(emptySelectionMessage.value)
    } else {
      for (const { label, level, count } of splitSummary.value) {
        if (level === 'REQUIRED' && count === 0) result.push(`This Action needs at least one ${label.toLowerCase()} page.${allPagesHint.value}`)
      }
    }
    if (pagesMissingChosenImage.value.length) {
      result.push(`${pageCountLabel(pagesMissingChosenImage.value.length)} missing the chosen image variant.`)
    }
    return result
  })

  const parameterBlockers = computed(() => Object.entries(selectedProcessor.value?.parameters ?? {})
    .filter(([key, definition]) => definition.required && isBlankActionParameterValue(parameters.value[key]))
    .map(([key]) => `Enter ${actionParameterLabel(key).toLowerCase()}.`))

  const blockers = computed(() => [...pageBlockers.value, ...parameterBlockers.value])

  const canStart = computed(() => Boolean(selectedDataset.value && selectedProcessor.value)
    && !loadingInputs.value && !blockers.value.length)

  function setItemSelected(itemId: string, selected: boolean) {
    const next = new Set(selectedItemIds.value)
    if (selected) next.add(itemId)
    else next.delete(itemId)
    selectedItemIds.value = next
  }

  function setItemsSelected(items: TrainingInputItem[], selected: boolean) {
    const next = new Set(selectedItemIds.value)
    for (const item of items) {
      if (!selected) next.delete(item.itemId)
      else if (skipReason(item) === null && usesItem(item)) next.add(item.itemId)
    }
    selectedItemIds.value = next
  }

  async function loadDataset(requestWorkspaceId: string, datasetId: string) {
    const requestSequence = ++inputRequestSequence
    loadingInputs.value = true
    inputError.value = undefined
    try {
      const [inputResponse, detailResponse] = await Promise.all([
        $fetch<TrainingInputResponse>(`/api/workspaces/${requestWorkspaceId}/actions/${kind}/datasets/${datasetId}/inputs`),
        $fetch<DatasetDetail>(`/api/workspaces/${requestWorkspaceId}/datasets/${datasetId}`)
      ])
      if (requestSequence !== inputRequestSequence) return
      inputs.value = inputResponse
      datasetDetail.value = detailResponse
      if (pendingPageIds) {
        const pageIds = pendingPageIds
        pendingPageIds = null
        selectedItemIds.value = new Set(inputResponse.items.filter(item => pageIds.has(item.sourcePageId)).map(item => item.itemId))
        selectionMode.value = 'SELECTED'
      }
    } catch (error: unknown) {
      if (requestSequence !== inputRequestSequence) return
      inputs.value = null
      datasetDetail.value = null
      inputError.value = extractApiErrorMessage(error, 'Could not load the pages of this dataset.')
    } finally {
      if (requestSequence === inputRequestSequence) loadingInputs.value = false
    }
  }

  async function loadParameterChoices(requestWorkspaceId: string, processorId: string) {
    const requestSequence = ++parameterRequestSequence
    parameterChoices.value = {}
    try {
      const response = await $fetch<{ values?: Record<string, ActionParameterChoice[]> }>(
        `/api/workspaces/${requestWorkspaceId}/actions/${kind}/processors/${processorId}/parameter-values`
      )
      if (requestSequence !== parameterRequestSequence) return
      parameterChoices.value = response.values ?? {}
      applyDiscoveredActionParameterDefaults(selectedProcessor.value?.parameters ?? {}, parameters.value, parameterChoices.value)
    } catch {
      // Parameters without discovered choices stay editable as plain inputs.
    }
  }

  async function start() {
    if (!canStart.value || starting.value) return
    starting.value = true
    try {
      // The server rejects incomplete pages in ALL mode, so skipped pages are left out by naming the rest.
      const selection = selectionMode.value === 'ALL' && !skippedItems.value.length
        ? { mode: 'ALL' }
        : { mode: 'SELECTED', itemIds: activeItems.value.map(item => item.itemId) }
      const itemImages: Record<string, string[]> = {}
      if (acceptsImages.value) {
        for (const item of activeItems.value) {
          const ids = selectedImageIds(item)
          if (ids.length) itemImages[item.itemId] = ids
        }
      }
      const response = await $fetch<StartActionRunResponse>(
        `/api/workspaces/${workspaceId.value}/actions/${kind}/datasets/${selectedDatasetId.value}/runs`,
        {
          method: 'POST',
          body: {
            processorDefinitionId: selectedProcessorId.value,
            selection,
            imageSelection: { globalVariants: acceptsImages.value ? globalVariants.value : [], itemImageIdLists: itemImages },
            parameters: parameters.value,
            enqueueIfBusy: true,
            ...(usesAllPages.value ? { allPages: true } : {})
          }
        }
      )
      const title = kind === 'training' ? 'Training' : 'Evaluation'
      toast.add({ title: `${title} ${response.run.status === 'QUEUED' ? 'queued' : 'started'}`, color: 'success' })
      await navigateTo(`/${kind}`)
    } catch (error: unknown) {
      toast.add({ title: `Could not start ${kind}`, description: extractApiErrorMessage(error, 'Please try again.'), color: 'error' })
    } finally {
      starting.value = false
    }
  }

  watch(datasets, (items) => {
    if (!items.some(item => item.id === selectedDatasetId.value)) selectedDatasetId.value = items[0]?.id
  })
  watch(processors, (items) => {
    if (!items.some(item => item.id === selectedProcessorId.value)) selectedProcessorId.value = items[0]?.id
  })

  watch([workspaceId, selectedDatasetId], ([currentWorkspaceId, datasetId]) => {
    selectionMode.value = usesProjectPages?.value ? 'REVIEWED' : 'ALL'
    selectedItemIds.value = new Set()
    itemImageIdLists.value = {}
    globalVariants.value = []
    inputs.value = null
    datasetDetail.value = null
    if (currentWorkspaceId && datasetId) void loadDataset(currentWorkspaceId, datasetId)
  }, { immediate: true })

  watch(selectionMode, (mode, previousMode) => {
    if (mode !== 'SELECTED' || selectedItemIds.value.size > 0) return
    const startingItems = previousMode === 'REVIEWED' && reviewedItems.value.length ? reviewedItems.value : usableItems.value
    selectedItemIds.value = new Set(startingItems.map(item => item.itemId))
  })

  watch(selectedProcessor, (processor) => {
    parameters.value = {}
    for (const [key, definition] of Object.entries(processor?.parameters ?? {})) {
      parameters.value[key] = actionParameterDefaultValue(definition)
    }
    if (workspaceId.value && processor) void loadParameterChoices(workspaceId.value, processor.id)
  }, { immediate: true })

  watch([selectedProcessor, activeItems], () => {
    if (!acceptsImages.value) {
      globalVariants.value = []
      return
    }
    const validVariants = new Set(variantOptions.value.map(option => option.value))
    const retained = globalVariants.value.filter(variant => validVariants.has(variant))
    globalVariants.value = retained.length
      ? (multipleImages.value ? retained : retained.slice(0, 1))
      : defaultActionImageVariants(activeItems.value.map(selectableImages), multipleImages.value)
    for (const item of activeItems.value) {
      const ids = new Set(selectableImages(item).map(image => image.id))
      const retainedIds = (itemImageIdLists.value[item.itemId] ?? []).filter(id => ids.has(id))
      if (retainedIds.length) itemImageIdLists.value[item.itemId] = multipleImages.value ? retainedIds : retainedIds.slice(0, 1)
      else delete itemImageIdLists.value[item.itemId]
    }
  })

  return {
    selectedDatasetId,
    selectedProcessorId,
    selectedDataset,
    selectedProcessor,
    selectionMode,
    evaluateAllPages,
    allPagesSplit,
    selectedItemIds,
    globalVariants,
    itemImageIdLists,
    parameters,
    parameterChoices,
    loadingInputs,
    inputError,
    starting,
    allItems,
    usableItems,
    reviewedItems,
    activeItems,
    saveableItems,
    trainingOverlapCount,
    skippedItems,
    skippedReasons,
    splitSummary,
    tagCounts,
    variantOptions,
    acceptsImages,
    multipleImages,
    blockers,
    canStart,
    usesItem,
    skipReason,
    resolvedImages,
    selectableImages,
    selectedImageIds,
    setItemSelected,
    setItemsSelected,
    start
  }
}
