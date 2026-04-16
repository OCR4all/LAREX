<script setup lang="ts">
import { h } from 'vue'
import { LazyDatasetSlideoverRelease, LazyDatasetSlideoverReleaseShare, LazyUiDeleteSlideover, NuxtLink, UBadge, UButton, UDropdownMenu, UPopover, USelect } from '#components'
import type { BreadcrumbItem, DropdownMenuItem, TableColumn, TabsItem } from '@nuxt/ui'
import type {
  DatasetCreateOrUpdateRequest,
  DatasetDetail,
  DatasetItem,
  DatasetItemMode,
  DatasetItemSplit,
  DatasetItemStatus,
  DatasetRelease,
  DatasetSplitAlgorithm,
  DatasetSplitTemplate,
  DatasetValidationResult
} from '@/types/dataset'
import { wsKey } from '@/utils/fetch-keys'
import { extractApiErrorMessage } from '@/utils/api-error'
import { SIMPLE_TAG_OPERATOR_OPTIONS } from '@/composables/use-resource-list-page'
import { createSortableHeader, renderSimpleTagCell } from '@/utils/resource-list-columns'

const route = useRoute()
const toast = useToast()
const overlay = useOverlay()
const { selectedWorkspace } = await useWorkspaceBootstrap()
const createReleaseSlideover = overlay.create(LazyDatasetSlideoverRelease)
const releaseShareSlideover = overlay.create(LazyDatasetSlideoverReleaseShare)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

const datasetId = route.params.id as string
const datasetKey = computed(() => wsKey(selectedWorkspace.value as string, 'datasets', datasetId))

const { data: dataset, pending, error, refresh } = await useFetch<DatasetDetail | null>(() => `/api/workspaces/${selectedWorkspace.value}/datasets/${datasetId}`, {
  key: datasetKey,
  watch: [selectedWorkspace],
  default: () => null
})

const datasetCapabilities = useResourceCapabilities(dataset, 'dataset')

const splitTemplateOptions: Array<{ label: string, value: DatasetSplitTemplate }> = [
  {
    label: 'Train / Validation / Test',
    value: 'TRAIN_VAL_TEST'
  },
  {
    label: 'Train / Validation',
    value: 'TRAIN_VAL'
  }
]

const splitAlgorithmOptions: Array<{ label: string, value: DatasetSplitAlgorithm }> = [
  {
    label: 'Random seeded split',
    value: 'RANDOM_SEEDED'
  },
  {
    label: 'Group by source project',
    value: 'GROUP_BY_SOURCE_PROJECT'
  },
  {
    label: 'Stratify by selected tags',
    value: 'MULTILABEL_STRATIFIED_BY_TAGS'
  }
]

const splitSelectionOptions: Array<{ label: string, value: DatasetItemSplit }> = [
  { label: 'Train', value: 'TRAIN' },
  { label: 'Validation', value: 'VAL' },
  { label: 'Test', value: 'TEST' }
]

const formName = ref('')
const formDescription = ref('')
const formTags = ref<string[]>([])
const formSplitTemplate = ref<DatasetSplitTemplate>('TRAIN_VAL_TEST')
const formSplitAlgorithm = ref<DatasetSplitAlgorithm>('RANDOM_SEEDED')
const formSplitSeed = ref(42)
const formStratifyTags = ref<string[]>([])
const trainPercentage = ref(70)
const valPercentage = ref(15)
const syncingForm = ref(false)

watch(dataset, (value) => {
  if (!value) return

  syncingForm.value = true
  formName.value = value.name
  formDescription.value = value.description || ''
  formTags.value = [...(value.tags || [])]
  formSplitTemplate.value = value.splitTemplate
  formSplitAlgorithm.value = value.splitAlgorithm
  formSplitSeed.value = value.splitSeed
  trainPercentage.value = value.trainPercentage
  valPercentage.value = value.valPercentage
  formStratifyTags.value = [...(value.stratifyTagIds || [])]
  syncingForm.value = false
}, { immediate: true })

watch(() => formSplitTemplate.value, (value) => {
  if (syncingForm.value) return

  if (value === 'TRAIN_VAL') {
    if (trainPercentage.value + valPercentage.value !== 100) {
      trainPercentage.value = 80
      valPercentage.value = 20
    } else {
      valPercentage.value = 100 - trainPercentage.value
    }
    return
  }

  if (trainPercentage.value + valPercentage.value >= 100 || testPercentage.value === 0) {
    trainPercentage.value = 70
    valPercentage.value = 15
  }
})

const validationResult = ref<DatasetValidationResult | null>(null)
const validating = ref(false)
const saving = ref(false)
const generating = ref(false)
const exporting = ref(false)
const deletingItemIds = ref<Set<string>>(new Set())
const activeTab = ref<'pages' | 'releases'>('pages')

type DatasetTableRow = {
  id: string
  name: string
  description: string
  sourcePageName: string
  project: string
  sourceProjectId: string
  xmlFileName: string
  imageCount: number
  split: DatasetItemSplit
  splitLabel: string
  mode: DatasetItemMode
  modeLabel: string
  status: DatasetItemStatus
  statusLabel: string
  tags: string[]
  pinned: boolean
  manualSplit: boolean
  brokenReason?: string | null
  item: DatasetItem
}

type DatasetReleaseRow = DatasetRelease

const testPercentage = computed(() => {
  if (formSplitTemplate.value === 'TRAIN_VAL') return 0
  return Math.max(0, 100 - trainPercentage.value - valPercentage.value)
})

const splitTemplateSelectOptions = computed(() =>
  splitTemplateOptions.map(option => ({ label: option.label, value: option.value }))
)

const splitAlgorithmSelectOptions = computed(() =>
  splitAlgorithmOptions.map(option => ({ label: option.label, value: option.value }))
)

const breadcrumbItems = computed<BreadcrumbItem[]>(() => [
  {
    label: 'Home',
    icon: 'i-lucide-home',
    to: '/'
  },
  {
    label: 'Datasets',
    icon: 'i-lucide-database',
    to: '/datasets'
  },
  {
    label: dataset.value?.name || 'Dataset'
  }
])

const visibleSplitOptions = computed(() => {
  if (formSplitTemplate.value === 'TRAIN_VAL_TEST') return splitSelectionOptions
  if (dataset.value?.items.some(item => item.assignedSplit === 'TEST')) return splitSelectionOptions
  return splitSelectionOptions.filter(option => option.value !== 'TEST')
})

const visibleWarnings = computed(() =>
  validationResult.value?.warnings?.length
    ? validationResult.value.warnings
    : (dataset.value?.lastValidationWarnings || [])
)

const splitSliderValue = computed<number[]>({
  get: () => formSplitTemplate.value === 'TRAIN_VAL'
    ? [trainPercentage.value]
    : [trainPercentage.value, trainPercentage.value + valPercentage.value],
  set: (value) => {
    const values = Array.isArray(value) ? value.map(entry => Number(entry)) : [Number(value)]
    const firstValue = values[0] ?? Number.NaN
    const firstThumb = Math.min(95, Math.max(5, Number.isFinite(firstValue) ? firstValue : 70))

    if (formSplitTemplate.value === 'TRAIN_VAL') {
      trainPercentage.value = firstThumb
      valPercentage.value = 100 - firstThumb
      return
    }

    const secondValue = values[1] ?? Number.NaN
    const secondThumb = Math.min(95, Math.max(firstThumb + 5, Number.isFinite(secondValue) ? secondValue : firstThumb + 15))
    trainPercentage.value = firstThumb
    valPercentage.value = secondThumb - firstThumb
  }
})

const tableRows = computed<DatasetTableRow[]>(() => (dataset.value?.items || []).map(item => ({
  id: item.id,
  name: item.sourcePageName,
  description: `${item.sourceProjectName} ${item.selectedSourceXmlFileName}`,
  sourcePageName: item.sourcePageName,
  project: item.sourceProjectName,
  sourceProjectId: item.sourceProjectId,
  xmlFileName: item.selectedSourceXmlFileName,
  imageCount: item.selectedSourceImageIds.length,
  split: item.assignedSplit,
  splitLabel: splitLabel(item.assignedSplit),
  mode: item.mode,
  modeLabel: modeLabel(item.mode),
  status: item.status,
  statusLabel: itemStatusLabel(item.status),
  tags: item.sourcePageTags,
  pinned: item.pinned,
  manualSplit: item.manualSplit,
  brokenReason: item.brokenReason,
  item
})))

const {
  sort,
  globalFilter,
  columnFilters,
  tagFilterOperator,
  filteredAndSortedData,
  resetAllFilters,
  setColumnFilter,
  clearColumnFilter,
  getUniqueColumnValues
} = useTableFilters(tableRows, { column: 'sourcePageName', direction: 'asc' })

const selectedTags = computed<string[]>({
  get: () => {
    const tags = columnFilters.value.tags
    return Array.isArray(tags) ? tags : []
  },
  set: (value) => {
    if (value.length === 0) {
      clearColumnFilter('tags')
      return
    }
    setColumnFilter('tags', value)
  }
})

const selectedSplits = computed<DatasetItemSplit[]>({
  get: () => {
    const splits = columnFilters.value.split
    return Array.isArray(splits) ? splits.filter((value): value is DatasetItemSplit => typeof value === 'string') : []
  },
  set: (value) => {
    if (value.length === 0) {
      clearColumnFilter('split')
      return
    }
    setColumnFilter('split', value)
  }
})

const selectedModes = computed<DatasetItemMode[]>({
  get: () => {
    const modes = columnFilters.value.mode
    return Array.isArray(modes) ? modes.filter((value): value is DatasetItemMode => value === 'LINK' || value === 'COPY') : []
  },
  set: (value) => {
    if (value.length === 0) {
      clearColumnFilter('mode')
      return
    }
    setColumnFilter('mode', value)
  }
})

const selectedProjects = computed<string[]>({
  get: () => {
    const projects = columnFilters.value.project
    return Array.isArray(projects) ? projects : []
  },
  set: (value) => {
    if (value.length === 0) {
      clearColumnFilter('project')
      return
    }
    setColumnFilter('project', value)
  }
})

const selectedStatuses = computed<string[]>({
  get: () => {
    const statuses = columnFilters.value.status
    return Array.isArray(statuses) ? statuses : []
  },
  set: (value) => {
    if (value.length === 0) {
      clearColumnFilter('status')
      return
    }
    setColumnFilter('status', value)
  }
})

const uniqueTags = computed(() => {
  const tagCounts = new Map<string, number>()

  for (const row of tableRows.value) {
    for (const tag of row.tags) {
      tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1)
    }
  }

  return Array.from(tagCounts.entries())
    .sort((left, right) => left[0].localeCompare(right[0]))
    .map(([value, count]) => ({ label: value, value, count }))
})

const splitFilterOptions = computed(() =>
  splitSelectionOptions
    .filter(option => tableRows.value.some(row => row.split === option.value))
    .map(option => ({ label: option.label, value: option.value }))
)

const modeFilterOptions = computed(() =>
  ['LINK', 'COPY']
    .filter(value => tableRows.value.some(row => row.mode === value))
    .map(value => ({ label: modeLabel(value as DatasetItemMode), value }))
)

const projectFilterOptions = computed(() =>
  getUniqueColumnValues('project').map(option => ({ label: option.label, value: String(option.value) }))
)

const statusFilterOptions = computed(() =>
  ['READY', 'BROKEN']
    .filter(value => tableRows.value.some(row => row.status === value))
    .map(value => ({ label: itemStatusLabel(value as DatasetItemStatus), value }))
)

const page = ref(1)
const itemsPerPage = ref(10)

const totalItems = computed(() => filteredAndSortedData.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPage.value)))
const paginatedRows = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  return filteredAndSortedData.value.slice(start, start + itemsPerPage.value)
})

const selectedItemIds = ref<Set<string>>(new Set())
const selectedItems = computed(() => tableRows.value.filter(row => selectedItemIds.value.has(row.id)))
const canDeleteSelectedItems = computed(() =>
  datasetCapabilities.value.canManageItems
  && selectedItems.value.length > 0
  && !selectedItems.value.some(row => deletingItemIds.value.has(row.id))
)
const allPageItemsSelected = computed(() =>
  paginatedRows.value.length > 0
  && paginatedRows.value.every(row => selectedItemIds.value.has(row.id))
)
const somePageItemsSelected = computed(() =>
  paginatedRows.value.some(row => selectedItemIds.value.has(row.id))
  && !allPageItemsSelected.value
)

function toggleItemSelection(itemId: string) {
  const next = new Set(selectedItemIds.value)
  if (next.has(itemId)) next.delete(itemId)
  else next.add(itemId)
  selectedItemIds.value = next
}

function toggleCurrentItemPageSelection() {
  const next = new Set(selectedItemIds.value)
  if (allPageItemsSelected.value) {
    paginatedRows.value.forEach(row => next.delete(row.id))
  } else {
    paginatedRows.value.forEach(row => next.add(row.id))
  }
  selectedItemIds.value = next
}

function clearItemSelection() {
  selectedItemIds.value = new Set()
}

watch(tableRows, (nextRows) => {
  const validIds = new Set(nextRows.map(row => row.id))
  selectedItemIds.value = new Set(Array.from(selectedItemIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const hasActiveFilters = computed(() =>
  Boolean(globalFilter.value)
  || selectedTags.value.length > 0
  || selectedSplits.value.length > 0
  || selectedModes.value.length > 0
  || selectedProjects.value.length > 0
  || selectedStatuses.value.length > 0
)

const nextReleaseTag = computed(() => {
  const nextVersion = Math.max(...(dataset.value?.releases || []).map(release => release.versionNumber), 0) + 1
  return `v${nextVersion}`
})

const contentTabItems = computed<TabsItem[]>(() => [
  {
    label: `Pages (${dataset.value?.items.length || 0})`,
    value: 'pages',
    icon: 'i-lucide-files'
  },
  {
    label: `Releases (${dataset.value?.releases.length || 0})`,
    value: 'releases',
    icon: 'i-lucide-tag'
  }
])

const isBusy = computed(() => saving.value || validating.value || generating.value || exporting.value)

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = []

  if (datasetCapabilities.value.canEdit) {
    items.push({
      label: 'Create Release',
      icon: 'i-lucide-tag',
      disabled: isBusy.value,
      onSelect: openCreateRelease
    })
  }

  if (datasetCapabilities.value.canExportPackage) {
    items.push({
      label: 'Export Package',
      icon: 'i-lucide-download',
      disabled: isBusy.value,
      onSelect: exportDatasetPackage
    })
  }

  if (datasetCapabilities.value.canGenerateSplit) {
    items.push({
      label: 'Regenerate Splits',
      icon: 'i-lucide-shuffle',
      disabled: isBusy.value,
      onSelect: generateSplit
    })
  }

  items.push({
    label: 'Validate',
    icon: 'i-lucide-shield-check',
    disabled: isBusy.value,
    onSelect: validateDataset
  })

  return items
})

watch([globalFilter, columnFilters], () => {
  page.value = 1
}, { deep: true })

const itemColumns = computed<TableColumn<DatasetTableRow>[]>(() => [
  {
    id: 'select',
    header: () => h('input', {
      type: 'checkbox',
      checked: allPageItemsSelected.value,
      indeterminate: somePageItemsSelected.value,
      onChange: toggleCurrentItemPageSelection,
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    }),
    cell: ({ row }) => h('input', {
      type: 'checkbox',
      checked: selectedItemIds.value.has(row.original.id),
      onChange: () => toggleItemSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'sourcePageName',
    header: createSortableHeader('Page', 'sourcePageName', sort, UButton),
    cell: ({ row }) => h('div', { class: 'truncate font-medium text-highlighted py-1' }, row.original.sourcePageName)
  },
  {
    accessorKey: 'project',
    header: createSortableHeader('Project', 'project', sort, UButton),
    cell: ({ row }) => h(NuxtLink, {
      to: `/project/${row.original.sourceProjectId}`,
      class: 'block truncate py-1 text-sm text-muted hover:text-primary'
    }, () => row.original.project)
  },
  {
    accessorKey: 'xmlFileName',
    header: createSortableHeader('XML', 'xmlFileName', sort, UButton),
    cell: ({ row }) => h('div', {
      class: 'max-w-56 truncate py-1 text-sm text-muted',
      title: row.original.xmlFileName
    }, row.original.xmlFileName)
  },
  {
    accessorKey: 'imageCount',
    header: createSortableHeader('Images', 'imageCount', sort, UButton, { align: 'end' }),
    cell: ({ row }) => h('div', { class: 'py-1 text-right tabular-nums' }, String(row.original.imageCount))
  },
  {
    id: 'mode',
    header: createSortableHeader('Mode', 'modeLabel', sort, UButton),
    cell: ({ row }) => h(UBadge, {
      color: row.original.mode === 'COPY' ? 'neutral' : 'primary',
      variant: 'soft'
    }, () => row.original.modeLabel)
  },
  {
    id: 'split',
    header: createSortableHeader('Split', 'splitLabel', sort, UButton),
    cell: ({ row }) => datasetCapabilities.value.canManageItems
      ? h(USelect, {
          'modelValue': row.original.split,
          'items': visibleSplitOptions.value,
          'valueKey': 'value',
          'class': 'min-w-36',
          'onUpdate:modelValue': (value: DatasetItemSplit) => updateItemSplit(row.original.item, value)
        })
      : h(UBadge, { color: splitColor(row.original.split), variant: 'soft' }, () => row.original.splitLabel)
  },
  {
    id: 'status',
    header: createSortableHeader('Status', 'statusLabel', sort, UButton),
    cell: ({ row }) => h('div', { class: 'space-y-1 py-1' }, [
      h(UBadge, {
        color: row.original.status === 'BROKEN' ? 'error' : 'success',
        variant: 'soft'
      }, () => row.original.statusLabel),
      row.original.brokenReason
        ? h('p', { class: 'max-w-xs text-xs text-error whitespace-normal' }, row.original.brokenReason)
        : null
    ].filter(Boolean))
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }) => renderSimpleTagCell(row.original.tags, { UBadge, UButton, UPopover }) || h('span', { class: 'text-sm text-muted' }, '—')
  },
  {
    accessorKey: 'manualSplit',
    header: createSortableHeader('Manual', 'manualSplit', sort, UButton),
    cell: ({ row }) => row.original.manualSplit
      ? h(UBadge, { color: 'warning', variant: 'soft' }, () => 'Manual')
      : h('span', { class: 'text-sm text-muted' }, '—')
  },
  {
    id: 'pinned',
    header: createSortableHeader('Pinned', 'pinned', sort, UButton),
    cell: ({ row }) => datasetCapabilities.value.canManageItems
      ? h(UButton, {
          color: 'neutral',
          variant: row.original.pinned ? 'soft' : 'ghost',
          size: 'xs',
          icon: row.original.pinned ? 'i-lucide-pin' : 'i-lucide-pin-off',
          onClick: () => updateItemPinned(row.original.item, !row.original.pinned)
        }, () => row.original.pinned ? 'Pinned' : 'Pin')
      : (row.original.pinned
          ? h(UBadge, { color: 'neutral', variant: 'soft' }, () => 'Pinned')
          : h('span', { class: 'text-sm text-muted' }, 'No'))
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => datasetCapabilities.value.canManageItems
      ? h(UButton, {
          color: 'error',
          variant: 'ghost',
          size: 'sm',
          icon: 'i-lucide-trash-2',
          loading: deletingItemIds.value.has(row.original.id),
          onClick: () => deleteItem(row.original.item)
        }, () => 'Remove')
      : null
  }
])

const releaseColumns = computed<TableColumn<DatasetReleaseRow>[]>(() => [
  {
    accessorKey: 'versionTag',
    header: 'Version',
    cell: ({ row }) => h('div', { class: 'space-y-1 py-1' }, [
      h('div', { class: 'font-medium text-highlighted' }, row.original.versionTag),
      h('div', { class: 'text-xs text-muted' }, `#${row.original.versionNumber}`)
    ])
  },
  {
    accessorKey: 'created',
    header: 'Created',
    cell: ({ row }) => h('div', { class: 'py-1 text-sm text-muted' }, formatDateTime(row.original.created))
  },
  {
    accessorKey: 'itemCount',
    header: 'Items',
    cell: ({ row }) => h('div', { class: 'py-1 tabular-nums' }, String(row.original.itemCount))
  },
  {
    accessorKey: 'validationStatus',
    header: 'Validation',
    cell: ({ row }) => h(UBadge, {
      color: row.original.validationStatus === 'VALID' ? 'success' : row.original.validationStatus === 'INVALID' ? 'error' : 'neutral',
      variant: 'soft'
    }, () => row.original.validationStatus.replaceAll('_', ' '))
  },
  {
    accessorKey: 'packageFileSize',
    header: 'Package',
    cell: ({ row }) => h('div', { class: 'space-y-1 py-1 text-sm' }, [
      h('div', { class: 'text-highlighted' }, row.original.packageFileName || '—'),
      h('div', { class: 'text-muted' }, formatBytes(row.original.packageFileSize))
    ])
  },
  {
    id: 'share',
    header: 'External access',
    cell: ({ row }) => h('div', { class: 'space-y-1 py-1 text-sm' }, [
      h(UBadge, {
        color: row.original.shareEnabled ? 'success' : 'neutral',
        variant: 'soft'
      }, () => row.original.shareEnabled ? 'Enabled' : 'Disabled'),
      h('div', { class: 'text-xs text-muted' }, row.original.shareEnabled
        ? `Expires ${formatDateTime(row.original.shareExpiresAt)}`
        : 'No share secret active'),
      h('div', { class: 'text-xs text-muted' }, `Downloads ${row.original.shareDownloadCount ?? 0}`)
    ])
  },
  {
    accessorKey: 'packageChecksumSha256',
    header: 'Checksum',
    cell: ({ row }) => h('code', { class: 'block max-w-40 truncate py-1 text-xs text-muted' }, shortChecksum(row.original.packageChecksumSha256))
  },
  {
    accessorKey: 'notes',
    header: 'Notes',
    cell: ({ row }) => h('div', {
      class: 'max-w-80 truncate py-1 text-sm text-muted',
      title: row.original.notes || ''
    }, row.original.notes || '—')
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => h('div', { class: 'flex flex-wrap justify-end gap-2 py-1' }, [
      datasetCapabilities.value.canEdit
        ? h(UButton, {
            color: 'neutral',
            variant: 'outline',
            size: 'sm',
            icon: 'i-lucide-key-round',
            disabled: row.original.status !== 'READY',
            onClick: () => openReleaseShare(row.original)
          }, () => 'Share')
        : null,
      h(UButton, {
        color: 'neutral',
        variant: 'ghost',
        size: 'sm',
        icon: 'i-lucide-download',
        disabled: row.original.status !== 'READY',
        onClick: () => downloadReleasePackage(row.original)
      }, () => 'Download')
    ])
  }
])

function modeLabel(mode: DatasetItemMode) {
  return mode === 'COPY' ? 'Frozen copy' : 'Live link'
}

function splitLabel(split: DatasetItemSplit) {
  switch (split) {
    case 'TRAIN': return 'Train'
    case 'VAL': return 'Validation'
    case 'TEST': return 'Test'
  }
}

function splitColor(split: DatasetItemSplit) {
  switch (split) {
    case 'TRAIN': return 'primary'
    case 'VAL': return 'neutral'
    case 'TEST': return 'warning'
  }
}

function itemStatusLabel(status: DatasetItemStatus) {
  return status === 'BROKEN' ? 'Broken' : 'Ready'
}

function formatDateTime(value?: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

function formatBytes(value?: number | null) {
  if (!value || value <= 0) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`
  return `${(value / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

function shortChecksum(value?: string | null) {
  if (!value) return '—'
  return value.slice(0, 12)
}

function resetContentFilters() {
  resetAllFilters()
}

function createPayload(): DatasetCreateOrUpdateRequest {
  return {
    name: formName.value.trim(),
    description: formDescription.value.trim() || null,
    tags: formTags.value,
    splitTemplate: formSplitTemplate.value,
    splitAlgorithm: formSplitAlgorithm.value,
    splitSeed: Number(formSplitSeed.value) || 42,
    trainPercentage: trainPercentage.value,
    valPercentage: valPercentage.value,
    testPercentage: testPercentage.value,
    stratifyTagIds: formStratifyTags.value
  }
}

async function saveDataset() {
  if (!selectedWorkspace.value || !dataset.value) return

  saving.value = true
  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}`, {
      method: 'PUT',
      body: createPayload()
    })
    toast.add({ title: 'Dataset saved', color: 'success' })
    validationResult.value = null
    await refresh()
  } catch (cause: unknown) {
    toast.add({ title: 'Save failed', description: extractApiErrorMessage(cause, 'Failed to save dataset'), color: 'error' })
  } finally {
    saving.value = false
  }
}

async function generateSplit() {
  if (!selectedWorkspace.value || !dataset.value) return

  generating.value = true
  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}/split-generate`, {
      method: 'POST',
      body: createPayload()
    })
    toast.add({ title: 'Splits regenerated', color: 'success' })
    await refresh()
  } catch (cause: unknown) {
    toast.add({ title: 'Split generation failed', description: extractApiErrorMessage(cause, 'Failed to generate splits'), color: 'error' })
  } finally {
    generating.value = false
  }
}

async function validateDataset() {
  if (!selectedWorkspace.value || !dataset.value) return

  validating.value = true
  try {
    validationResult.value = await $fetch<DatasetValidationResult>(`/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}/validate`, {
      method: 'POST'
    })
    toast.add({
      title: 'Validation completed',
      color: validationResult.value.status === 'INVALID' ? 'warning' : 'success'
    })
    await refresh()
  } catch (cause: unknown) {
    toast.add({ title: 'Validation failed', description: extractApiErrorMessage(cause, 'Failed to validate dataset'), color: 'error' })
  } finally {
    validating.value = false
  }
}

async function exportDatasetPackage() {
  if (!selectedWorkspace.value || !dataset.value) return

  exporting.value = true
  try {
    const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}/export-package`, {
      method: 'POST'
    })
    if (!response.ok) {
      const message = await response.text()
      throw new Error(message || `Export failed (${response.status})`)
    }

    await downloadBlobResponse(response, `${dataset.value.name.replace(/\s+/g, '-').toLowerCase()}.larex-dataset.zip`)
    toast.add({ title: 'Dataset package exported', color: 'success' })
    await refresh()
  } catch (cause: unknown) {
    toast.add({ title: 'Export failed', description: extractApiErrorMessage(cause, 'Failed to export dataset package'), color: 'error' })
  } finally {
    exporting.value = false
  }
}

async function openCreateRelease() {
  if (!dataset.value) return

  const instance = createReleaseSlideover.open({
    datasetId: dataset.value.id,
    suggestedTag: nextReleaseTag.value
  })
  const createdReleaseId = await instance.result as string | null
  if (!createdReleaseId) return
  await refresh()
}

async function openReleaseShare(release: DatasetRelease) {
  if (!dataset.value) return

  const instance = releaseShareSlideover.open({
    datasetId: dataset.value.id,
    release
  })
  const shouldRefresh = await instance.result as boolean | null
  if (shouldRefresh) {
    await refresh()
  }
}

async function downloadReleasePackage(release: DatasetRelease) {
  if (!selectedWorkspace.value || !dataset.value) return

  try {
    const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}/releases/${release.id}/download`)
    if (!response.ok) {
      const message = await response.text()
      throw new Error(message || `Download failed (${response.status})`)
    }
    await downloadBlobResponse(response, release.packageFileName || `${dataset.value.name}-${release.versionTag}.zip`)
  } catch (cause: unknown) {
    toast.add({
      title: 'Release download failed',
      description: extractApiErrorMessage(cause, 'Failed to download release package'),
      color: 'error'
    })
  }
}

async function updateItemSplit(item: DatasetItem, split: DatasetItemSplit) {
  if (!selectedWorkspace.value || !dataset.value) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}/items/${item.id}`, {
      method: 'PATCH',
      body: { assignedSplit: split }
    })
    await refresh()
  } catch (cause: unknown) {
    toast.add({ title: 'Update failed', description: extractApiErrorMessage(cause, 'Failed to update item split'), color: 'error' })
  }
}

async function updateItemPinned(item: DatasetItem, pinned: boolean) {
  if (!selectedWorkspace.value || !dataset.value) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}/items/${item.id}`, {
      method: 'PATCH',
      body: { pinned }
    })
    await refresh()
  } catch (cause: unknown) {
    toast.add({ title: 'Update failed', description: extractApiErrorMessage(cause, 'Failed to update item pinning'), color: 'error' })
  }
}

async function deleteItem(item: DatasetItem) {
  if (!selectedWorkspace.value || !dataset.value) return

  const next = new Set(deletingItemIds.value)
  next.add(item.id)
  deletingItemIds.value = next

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}/items/${item.id}`, {
      method: 'DELETE'
    })
    toast.add({ title: 'Item removed', color: 'success' })
    await refresh()
  } catch (cause: unknown) {
    toast.add({ title: 'Remove failed', description: extractApiErrorMessage(cause, 'Failed to remove dataset item'), color: 'error' })
  } finally {
    const after = new Set(deletingItemIds.value)
    after.delete(item.id)
    deletingItemIds.value = after
  }
}

async function deleteSelectedItems() {
  if (!selectedWorkspace.value || !dataset.value || !canDeleteSelectedItems.value) return

  const count = selectedItems.value.length
  const instance = deleteSlideover.open({
    name: `${count} dataset item${count === 1 ? '' : 's'}`,
    entityType: 'Dataset Item',
    warningMessage: 'This action cannot be undone. The selected items will be removed from the dataset.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const ids = selectedItems.value.map(row => row.item.id)
  deletingItemIds.value = new Set([...deletingItemIds.value, ...ids])

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      `/api/workspaces/${selectedWorkspace.value}/datasets/${dataset.value.id}/items/bulk`,
      {
        method: 'DELETE',
        body: { ids }
      }
    )

    if (response.successCount > 0) {
      toast.add({ title: response.successCount === 1 ? 'Item removed' : 'Items removed', description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`, color: 'success' })
    }
    if (response.failedCount > 0) {
      toast.add({ title: 'Some removals failed', description: `${response.failedCount} item${response.failedCount === 1 ? '' : 's'} could not be removed.`, color: 'warning' })
    }

    clearItemSelection()
    await refresh()
  } catch (cause: unknown) {
    toast.add({ title: 'Remove failed', description: extractApiErrorMessage(cause, 'Failed to remove selected dataset items'), color: 'error' })
  } finally {
    deletingItemIds.value = new Set()
  }
}

async function downloadBlobResponse(response: Response, fallbackName: string) {
  const blob = await response.blob()
  const contentDisposition = response.headers.get('content-disposition')
  const match = contentDisposition?.match(/filename\*?=(?:UTF-8''|"?)([^";]+)/i)
  const fileName = match ? decodeURIComponent(match[1]!) : fallbackName
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

useHead({
  title: computed(() => dataset.value?.name ? `${dataset.value.name} - LAREX` : 'Dataset - LAREX')
})
</script>

<template>
  <UDashboardPanel id="dataset-detail" :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <UDashboardNavbar>
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>

        <template #title>
          <span class="truncate">{{ dataset?.name || 'Dataset' }}</span>
        </template>

        <template #right>
          <UFieldGroup>
            <UButton
              v-if="datasetCapabilities.canEdit"
              color="neutral"
              variant="outline"
              icon="i-lucide-save"
              :loading="saving"
              :disabled="isBusy && !saving"
              @click="saveDataset"
            >
              Save
            </UButton>

            <UDropdownMenu v-if="actionItems.length > 0" :items="actionItems" :content="{ align: 'end' }">
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-chevron-down"
                :loading="validating || generating || exporting"
                :disabled="isBusy && !validating && !generating && !exporting"
              />
            </UDropdownMenu>
          </UFieldGroup>
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UBreadcrumb :items="breadcrumbItems" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="pending" class="flex items-center justify-center py-16">
        <UIcon name="i-lucide-loader-2" class="size-8 animate-spin text-muted" />
      </div>

      <div v-else-if="error" class="p-4 lg:p-6">
        <UAlert
          color="error"
          variant="subtle"
          icon="i-lucide-alert-circle"
          title="Error loading dataset"
          :description="extractApiErrorMessage(error, 'Failed to load dataset')"
        />
      </div>

      <div v-else-if="!dataset" class="flex h-full items-center justify-center p-6">
        <UEmpty
          icon="i-lucide-database-zap"
          title="Dataset not found"
          description="This dataset may have been deleted, or you may no longer have access to it."
          :actions="[{ label: 'Back to datasets', to: '/datasets', color: 'neutral' }]"
        />
      </div>

      <div v-else class="h-full flex overflow-hidden">
        <aside class="w-96 shrink-0 overflow-y-auto border-r border-default bg-muted/20">
          <div class="space-y-6 p-4 lg:p-5">
            <h2 class="text-sm font-semibold">
              Metadata
            </h2>

            <UFormField label="Name" required>
              <UInput v-model="formName" :disabled="!datasetCapabilities.canEdit" />
            </UFormField>

            <UFormField label="Description">
              <UTextarea
                v-model="formDescription"
                :rows="4"
                :disabled="!datasetCapabilities.canEdit"
                placeholder="What does this dataset contain and what is it for?"
              />
            </UFormField>

            <UFormField label="Tags" hint="Use tags to group or search datasets later.">
              <UInputTags
                v-model="formTags"
                icon="i-lucide-tags"
                placeholder="e.g. handwriting, layout, german"
                :disabled="!datasetCapabilities.canEdit"
              />
            </UFormField>

            <h2 class="border-t border-default pt-6 text-sm font-semibold">
              Split Strategy
            </h2>

            <UFormField label="Split layout">
              <USelect
                v-model="formSplitTemplate"
                :items="splitTemplateSelectOptions"
                value-key="value"
                :disabled="!datasetCapabilities.canGenerateSplit"
              />
            </UFormField>

            <UFormField label="Assignment algorithm">
              <USelect
                v-model="formSplitAlgorithm"
                :items="splitAlgorithmSelectOptions"
                value-key="value"
                :disabled="!datasetCapabilities.canGenerateSplit"
              />
            </UFormField>

            <UFormField label="Random seed" hint="Use the same seed to reproduce the same assignment.">
              <UInput
                v-model.number="formSplitSeed"
                type="number"
                min="0"
                step="1"
                :disabled="!datasetCapabilities.canGenerateSplit"
              />
            </UFormField>

            <div class="space-y-4 rounded-lg border border-default bg-default p-4">
              <div class="flex flex-wrap gap-2">
                <UBadge color="primary" variant="soft">
                  Train {{ trainPercentage }}%
                </UBadge>
                <UBadge color="neutral" variant="soft">
                  Validation {{ valPercentage }}%
                </UBadge>
                <UBadge :color="formSplitTemplate === 'TRAIN_VAL' ? 'neutral' : 'warning'" variant="soft">
                  Test {{ testPercentage }}%
                </UBadge>
              </div>
              <USlider
                v-model="splitSliderValue"
                :min="5"
                :max="95"
                :step="1"
                :min-steps-between-thumbs="5"
                :disabled="!datasetCapabilities.canGenerateSplit"
                tooltip
              />
            </div>

            <UFormField
              label="Stratify tags"
              hint="Only used by the tag-stratified algorithm. Leave empty for a plain seeded split."
            >
              <UInputTags
                v-model="formStratifyTags"
                icon="i-lucide-tag"
                placeholder="e.g. print, marginalia, rubric"
                :disabled="!datasetCapabilities.canGenerateSplit"
              />
            </UFormField>
          </div>
        </aside>

        <section class="flex min-w-0 flex-1 flex-col overflow-hidden bg-neutral-50/70 dark:bg-neutral-900">
          <div class="flex-1 overflow-y-auto">
            <div v-if="visibleWarnings.length > 0" class="space-y-2">
              <UAlert
                v-for="warning in visibleWarnings"
                :key="warning"
                color="warning"
                variant="subtle"
                icon="i-lucide-triangle-alert"
                :title="warning"
              />
            </div>

            <div v-if="validationResult?.issues?.length" class="space-y-2">
              <UAlert
                v-for="issue in validationResult.issues"
                :key="issue.itemId"
                color="error"
                variant="subtle"
                icon="i-lucide-circle-alert"
                :title="issue.sourcePageName"
                :description="issue.reason"
              />
            </div>

            <div class="space-y-4 overflow-hidden">
              <UTabs
                v-model="activeTab"
                :items="contentTabItems"
                color="primary"
                variant="link"
              />

              <template v-if="activeTab === 'pages'">
                <UDashboardToolbar>
                  <template #left>
                    <UInput
                      v-model="globalFilter"
                      icon="i-lucide-search"
                      placeholder="Search page name..."
                      class="w-64"
                    />

                    <USelectMenu
                      v-model="selectedTags"
                      :items="uniqueTags"
                      value-key="value"
                      placeholder="Filter by tag"
                      multiple
                      searchable
                      clear-search-on-close
                      class="w-48"
                    />

                    <USelectMenu
                      v-if="selectedTags.length > 1"
                      v-model="tagFilterOperator"
                      :items="SIMPLE_TAG_OPERATOR_OPTIONS"
                      value-key="value"
                      class="w-40"
                    />

                    <USelectMenu
                      v-model="selectedSplits"
                      :items="splitFilterOptions"
                      value-key="value"
                      placeholder="Filter by split"
                      multiple
                      class="w-40"
                    />

                    <USelectMenu
                      v-model="selectedModes"
                      :items="modeFilterOptions"
                      value-key="value"
                      placeholder="Filter by mode"
                      multiple
                      class="w-44"
                    />

                    <USelectMenu
                      v-model="selectedProjects"
                      :items="projectFilterOptions"
                      value-key="value"
                      placeholder="Filter by project"
                      multiple
                      searchable
                      clear-search-on-close
                      class="w-52"
                    />

                    <USelectMenu
                      v-model="selectedStatuses"
                      :items="statusFilterOptions"
                      value-key="value"
                      placeholder="Filter by status"
                      multiple
                      class="w-44"
                    />
                  </template>

                  <template #right>
                    <UButton
                      v-if="hasActiveFilters"
                      color="neutral"
                      variant="ghost"
                      size="sm"
                      @click="resetContentFilters"
                    >
                      Clear Filters
                    </UButton>
                  </template>
                </UDashboardToolbar>

                <div v-if="hasActiveFilters" class="flex flex-wrap gap-2">
                  <UBadge
                    v-if="globalFilter"
                    color="neutral"
                    variant="soft"
                    class="cursor-pointer"
                    @click="globalFilter = ''"
                  >
                    Search: {{ globalFilter }} ×
                  </UBadge>
                  <UBadge
                    v-for="tag in selectedTags"
                    :key="`tag:${tag}`"
                    color="neutral"
                    variant="soft"
                    class="cursor-pointer"
                    @click="selectedTags = selectedTags.filter(value => value !== tag)"
                  >
                    Tag: {{ tag }} ×
                  </UBadge>
                  <UBadge
                    v-for="split in selectedSplits"
                    :key="`split:${split}`"
                    color="neutral"
                    variant="soft"
                    class="cursor-pointer"
                    @click="selectedSplits = selectedSplits.filter(value => value !== split)"
                  >
                    Split: {{ splitLabel(split as DatasetItemSplit) }} ×
                  </UBadge>
                  <UBadge
                    v-for="mode in selectedModes"
                    :key="`mode:${mode}`"
                    color="neutral"
                    variant="soft"
                    class="cursor-pointer"
                    @click="selectedModes = selectedModes.filter(value => value !== mode)"
                  >
                    Mode: {{ modeLabel(mode as DatasetItemMode) }} ×
                  </UBadge>
                  <UBadge
                    v-for="project in selectedProjects"
                    :key="`project:${project}`"
                    color="neutral"
                    variant="soft"
                    class="cursor-pointer"
                    @click="selectedProjects = selectedProjects.filter(value => value !== project)"
                  >
                    Project: {{ project }} ×
                  </UBadge>
                  <UBadge
                    v-for="status in selectedStatuses"
                    :key="`status:${status}`"
                    color="neutral"
                    variant="soft"
                    class="cursor-pointer"
                    @click="selectedStatuses = selectedStatuses.filter(value => value !== status)"
                  >
                    Status: {{ itemStatusLabel(status as DatasetItemStatus) }} ×
                  </UBadge>
                </div>

                <div v-if="dataset.items.length === 0" class="p-6">
                  <UEmpty
                    variant="naked"
                    icon="i-lucide-files"
                    title="No dataset items yet"
                    description="Add pages from a project using the project page bulk action."
                  />
                </div>

                <div v-else-if="filteredAndSortedData.length === 0" class="p-6">
                  <UEmpty
                    variant="naked"
                    icon="i-lucide-filter-x"
                    title="No dataset items match your filters"
                    description="Adjust the filters or clear them to see more pages."
                  />
                </div>

                <div v-else class="p-4">
                  <UTable
                    :data="paginatedRows"
                    :columns="itemColumns"
                    class="flex-1"
                    :ui="{
                      base: 'table-fixed border-separate border-spacing-0',
                      thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
                      tbody: '[&>tr]:last:[&>td]:border-b-0',
                      th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
                      td: 'border-b border-default align-top',
                      separator: 'h-0'
                    }"
                  />

                  <UiFloatingSelectionMenu
                    :selected-count="selectedItemIds.size"
                    @clear="clearItemSelection"
                  >
                    <UButton
                      icon="i-lucide-trash"
                      color="error"
                      variant="ghost"
                      size="sm"
                      class="hover:bg-white/10"
                      :disabled="!canDeleteSelectedItems"
                      @click="deleteSelectedItems"
                    >
                      Delete
                    </UButton>
                  </UiFloatingSelectionMenu>

                  <div v-if="filteredAndSortedData.length > 0 && totalPages > 1" class="flex items-center justify-between border-t border-default pt-4">
                    <div class="text-sm text-muted">
                      Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} items
                    </div>

                    <div class="flex items-center gap-4">
                      <USelect
                        v-model="itemsPerPage"
                        :items="[10, 25, 50, 100]"
                        class="w-32"
                        size="sm"
                      />

                      <UPagination
                        v-model:page="page"
                        :total="totalItems"
                        :items-per-page="itemsPerPage"
                      />
                    </div>
                  </div>
                </div>
              </template>

              <template v-else>
                <UDashboardToolbar>
                  <template #right>
                    <UButton
                      v-if="datasetCapabilities.canEdit"
                      color="neutral"
                      variant="outline"
                      size="sm"
                      icon="i-lucide-plus"
                      @click="openCreateRelease"
                    >
                      New Release
                    </UButton>
                  </template>
                </UDashboardToolbar>

                <div v-if="!dataset.releases.length" class="p-6">
                  <UEmpty
                    variant="naked"
                    icon="i-lucide-tag"
                    title="No releases yet"
                    description="Create an immutable release to freeze the current dataset state and keep a reusable package artifact."
                  />
                </div>

                <UTable
                  v-else
                  :data="dataset.releases"
                  :columns="releaseColumns"
                  class="flex-1"
                  :ui="{
                    base: 'table-fixed border-separate border-spacing-0',
                    thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
                    tbody: '[&>tr]:last:[&>td]:border-b-0',
                    th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
                    td: 'border-b border-default align-top',
                    separator: 'h-0'
                  }"
                />
              </template>
            </div>
          </div>
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
