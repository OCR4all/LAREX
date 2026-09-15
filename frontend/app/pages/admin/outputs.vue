<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import { LazyUiDeleteSlideover } from '#components'
import { extractApiErrorMessage } from '@/utils/api-error'
import { getWorkspaceDisplayName } from '@/utils/workspace-display'

definePageMeta({ layout: 'admin', middleware: 'admin' })

type SortColumn = 'created' | 'totalSizeBytes'
type SortDirection = 'asc' | 'desc'

interface AdminOutput {
  id: string
  workspaceId: string
  projectId: string
  projectName: string
  sourceRunId: string
  processorDefinitionId: string
  processorKey: string
  processorName: string
  createdByUserId: string
  fileCount: number
  totalSizeBytes: number
  retentionDays: number | null
  expiresAt: string | null
  completedAt: string | null
  created: string
  updated: string
}

interface AdminOutputPage {
  outputs: AdminOutput[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface AdminWorkspace {
  id: string
  name: string
  isPersonal: boolean
  ownerUserId: string
  ownerUsername?: string | null
}

interface CleanupResponse {
  deletedCount: number
  failedCount: number
  freedBytes: number
  freedFormatted: string
  errors: string[]
}

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UDropdownMenu = resolveComponent('UDropdownMenu')

const overlay = useOverlay()
const toast = useToast()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

const page = ref(1)
const itemsPerPage = ref(25)
const sort = ref<{ column: SortColumn, direction: SortDirection }>({ column: 'created', direction: 'desc' })
const searchInput = ref('')
const debouncedSearch = ref('')
const deletingOutputId = ref<string | null>(null)
const isCleanupSlideoverOpen = ref(false)
const cleanupOlderThanDaysInput = ref('30')
const cleanupPreviewOutputs = ref<AdminOutput[]>([])
const cleanupPreviewReady = ref(false)
const cleanupPreviewError = ref<string | null>(null)
const selectedCleanupOutputIds = ref<Set<string>>(new Set())
const isCleanupPreviewLoading = ref(false)
const isBulkDeleting = ref(false)

const query = computed(() => ({
  page: page.value,
  size: itemsPerPage.value,
  sort: sort.value.column,
  direction: sort.value.direction,
  search: debouncedSearch.value || undefined
}))
const outputKey = computed(() => globalKey(
  'admin', 'outputs', page.value, itemsPerPage.value, sort.value.column, sort.value.direction,
  debouncedSearch.value || 'all'
))

const { data: outputPage, error, pending, refresh } = await useFetch<AdminOutputPage>('/api/admin/outputs', {
  key: outputKey,
  query,
  watch: [query],
  default: () => ({ outputs: [], page: 1, size: 25, totalElements: 0, totalPages: 1 })
})

const { data: workspaces } = await useFetch<AdminWorkspace[]>('/api/admin/workspaces', {
  key: globalKey('admin', 'outputs', 'workspaces'),
  default: () => []
})

const outputs = computed(() => outputPage.value?.outputs ?? [])
const totalItems = computed(() => outputPage.value?.totalElements ?? 0)
const totalPages = computed(() => Math.max(1, outputPage.value?.totalPages ?? 1))
const itemsPerPageModel = useItemsPerPageModel(page, itemsPerPage, totalItems)
const showingFrom = computed(() => totalItems.value === 0 ? 0 : (page.value - 1) * itemsPerPage.value + 1)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, totalItems.value))
const workspaceLabelById = computed(() => new Map(
  workspaces.value.map(workspace => [workspace.id, getWorkspaceDisplayName(workspace)])
))
const itemsPerPageOptions = [10, 25, 50, 100].map(value => ({ label: `${value} per page`, value }))

watch(searchInput, useDebounceFn((value: string) => {
  debouncedSearch.value = value.trim()
  page.value = 1
}, 250))

watch(totalPages, (value) => {
  if (page.value > value) page.value = value
})

watch(cleanupOlderThanDaysInput, () => {
  cleanupPreviewReady.value = false
  selectedCleanupOutputIds.value = new Set()
})

function toggleSort(column: SortColumn, initialDirection: SortDirection = 'desc') {
  if (sort.value.column === column) {
    sort.value = { column, direction: sort.value.direction === 'asc' ? 'desc' : 'asc' }
  } else {
    sort.value = { column, direction: initialDirection }
  }
  page.value = 1
}

function sortableHeader(label: string, column: SortColumn, initialDirection: SortDirection = 'desc') {
  return h('div', { class: 'flex items-center gap-2' }, [
    h('span', label),
    h(UButton, {
      'icon': sort.value.column === column
        ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
        : 'i-lucide-arrow-up-down',
      'size': 'xs',
      'variant': 'ghost',
      'color': sort.value.column === column ? 'primary' : 'neutral',
      'aria-label': `Sort by ${label}`,
      'onClick': () => toggleSort(column, initialDirection)
    })
  ])
}

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  const exponent = Math.floor(Math.log(bytes) / Math.log(1024))
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  return `${Number((bytes / Math.pow(1024, exponent)).toFixed(2))} ${sizes[exponent] ?? 'PB'}`
}

function formatDate(value: string | null) {
  return value
    ? new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '—'
}

function parseCleanupAge() {
  const value = Number.parseInt(String(cleanupOlderThanDaysInput.value).trim(), 10)
  return Number.isInteger(value) && value > 0 ? value : null
}

function workspaceLabel(output: AdminOutput) {
  return workspaceLabelById.value.get(output.workspaceId) || output.workspaceId
}

function retentionLabel(output: AdminOutput) {
  return output.expiresAt ? `Expires ${formatDate(output.expiresAt)}` : 'Retained indefinitely'
}

const selectedCleanupOutputs = computed(() => cleanupPreviewOutputs.value
  .filter(output => selectedCleanupOutputIds.value.has(output.id)))
const cleanupSelectedTotalSize = computed(() => selectedCleanupOutputs.value
  .reduce((total, output) => total + output.totalSizeBytes, 0))
const allCleanupOutputsSelected = computed(() => cleanupPreviewOutputs.value.length > 0
  && cleanupPreviewOutputs.value.every(output => selectedCleanupOutputIds.value.has(output.id)))

const columns = computed<TableColumn<AdminOutput>[]>(() => [
  {
    id: 'processor',
    header: 'Action',
    cell: ({ row }) => h('div', { class: 'min-w-48' }, [
      h('p', { class: 'truncate font-medium', title: row.original.processorName }, row.original.processorName),
      h('p', { class: 'truncate text-xs text-muted', title: row.original.processorKey }, row.original.processorKey)
    ])
  },
  {
    id: 'location',
    header: 'Workspace / Project',
    cell: ({ row }) => h('div', { class: 'min-w-52' }, [
      h('p', { class: 'truncate font-medium', title: workspaceLabel(row.original) }, workspaceLabel(row.original)),
      h('p', { class: 'truncate text-xs text-muted', title: row.original.projectName }, row.original.projectName)
    ])
  },
  {
    accessorKey: 'fileCount',
    header: 'Files',
    cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.fileCount))
  },
  {
    accessorKey: 'totalSizeBytes',
    header: () => sortableHeader('Size', 'totalSizeBytes'),
    cell: ({ row }) => h('span', { class: 'whitespace-nowrap tabular-nums' }, formatBytes(row.original.totalSizeBytes))
  },
  {
    accessorKey: 'created',
    header: () => sortableHeader('Created', 'created'),
    cell: ({ row }) => h('div', { class: 'whitespace-nowrap' }, [
      h('p', formatDate(row.original.created)),
      h('p', { class: 'text-xs text-muted' }, `Completed ${formatDate(row.original.completedAt)}`)
    ])
  },
  {
    id: 'retention',
    header: 'Retention',
    cell: ({ row }) => h(UBadge, {
      color: row.original.expiresAt ? 'warning' : 'neutral',
      variant: 'soft',
      title: row.original.expiresAt || undefined
    }, () => retentionLabel(row.original))
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => h(UDropdownMenu, {
      items: getRowActions(row.original),
      content: { align: 'end' }
    }, () => h(UButton, {
      'icon': 'i-lucide-ellipsis-vertical',
      'color': 'neutral',
      'variant': 'ghost',
      'size': 'xs',
      'square': true,
      'loading': deletingOutputId.value === row.original.id,
      'aria-label': `Actions for ${row.original.processorName}`
    }))
  }
])

function getRowActions(output: AdminOutput): DropdownMenuItem[] {
  return [{
    label: 'Delete',
    icon: 'i-lucide-trash-2',
    color: 'error',
    onSelect: () => deleteOutput(output)
  }]
}

async function deleteOutput(output: AdminOutput) {
  const confirmation = deleteSlideover.open({
    name: `${output.processorName} output`,
    entityType: 'Action output',
    confirmButtonLabel: 'Delete Output',
    warningDetails: [
      `${output.fileCount} file${output.fileCount === 1 ? '' : 's'} will be permanently removed.`,
      'Any active public share will be revoked.'
    ]
  })
  if (!await confirmation.result) return

  deletingOutputId.value = output.id
  try {
    const result = await $fetch<CleanupResponse>(`/api/admin/outputs/${output.id}`, { method: 'DELETE' })
    showCleanupToast(result, 'Output deleted', 'Output deletion is pending retry')
    await refreshWithPageGuard()
  } catch (error: unknown) {
    toast.add({
      title: 'Output deletion failed',
      description: extractApiErrorMessage(error, 'Could not delete output.'),
      color: 'error'
    })
  } finally {
    deletingOutputId.value = null
  }
}

function openCleanupSlideover() {
  cleanupPreviewOutputs.value = []
  cleanupPreviewReady.value = false
  cleanupPreviewError.value = null
  selectedCleanupOutputIds.value = new Set()
  isCleanupSlideoverOpen.value = true
}

async function previewCleanup() {
  const olderThanDays = parseCleanupAge()
  if (olderThanDays === null) {
    cleanupPreviewError.value = 'Enter a positive number of days.'
    return
  }

  isCleanupPreviewLoading.value = true
  cleanupPreviewReady.value = false
  cleanupPreviewError.value = null
  try {
    cleanupPreviewOutputs.value = await $fetch<AdminOutput[]>('/api/admin/outputs/cleanup-preview', {
      query: { olderThanDays }
    })
    selectedCleanupOutputIds.value = new Set(cleanupPreviewOutputs.value.map(output => output.id))
    cleanupPreviewReady.value = true
  } catch (error: unknown) {
    cleanupPreviewError.value = extractApiErrorMessage(error, 'Could not find old outputs.')
  } finally {
    isCleanupPreviewLoading.value = false
  }
}

function toggleCleanupOutput(outputId: string) {
  const selected = new Set(selectedCleanupOutputIds.value)
  if (selected.has(outputId)) selected.delete(outputId)
  else selected.add(outputId)
  selectedCleanupOutputIds.value = selected
}

function toggleAllCleanupOutputs() {
  selectedCleanupOutputIds.value = allCleanupOutputsSelected.value
    ? new Set()
    : new Set(cleanupPreviewOutputs.value.map(output => output.id))
}

async function bulkDeleteCleanupOutputs() {
  if (selectedCleanupOutputs.value.length === 0) return

  const selectedCount = selectedCleanupOutputs.value.length
  const confirmation = deleteSlideover.open({
    name: `${selectedCount} selected outputs`,
    entityType: 'Action output',
    confirmButtonLabel: 'Delete Selected Outputs',
    warningMessage: `Delete ${selectedCount} selected output${selectedCount === 1 ? '' : 's'}? This action cannot be undone.`,
    warningDetails: [
      'Any active public shares will be revoked.',
      'Outputs configured for indefinite retention are included.'
    ],
    items: selectedCleanupOutputs.value.map(output => ({
      id: output.id,
      label: `${output.processorName} · ${output.projectName}`
    }))
  })
  if (!await confirmation.result) return

  const olderThanDays = parseCleanupAge()
  if (olderThanDays === null) return
  isBulkDeleting.value = true
  try {
    const result = await $fetch<CleanupResponse>('/api/admin/outputs/cleanup', {
      method: 'POST',
      body: { olderThanDays, outputIds: selectedCleanupOutputs.value.map(output => output.id) }
    })
    showCleanupToast(result, 'Outputs deleted', 'Some output deletions are pending retry')
    isCleanupSlideoverOpen.value = false
    await refreshWithPageGuard()
  } catch (error: unknown) {
    toast.add({
      title: 'Cleanup failed',
      description: extractApiErrorMessage(error, 'Could not delete selected outputs.'),
      color: 'error'
    })
  } finally {
    isBulkDeleting.value = false
  }
}

function showCleanupToast(result: CleanupResponse, successTitle: string, pendingMessage: string) {
  if (result.deletedCount > 0) {
    toast.add({
      title: successTitle,
      description: `Deleted ${result.deletedCount} output${result.deletedCount === 1 ? '' : 's'}, freed ${result.freedFormatted}.`,
      color: 'success',
      icon: 'i-lucide-check-circle'
    })
  } else if (result.failedCount === 0) {
    toast.add({ title: 'No outputs matched', color: 'neutral' })
  }

  if (result.failedCount > 0) {
    toast.add({
      title: 'Some deletions failed',
      description: `${result.failedCount} output${result.failedCount === 1 ? '' : 's'} ${pendingMessage.toLowerCase()}.`,
      color: 'warning',
      icon: 'i-lucide-alert-triangle'
    })
  }
}

async function refreshWithPageGuard() {
  await refresh()
  if (page.value > totalPages.value) {
    page.value = totalPages.value
    await refresh()
  }
}
</script>

<template>
  <UDashboardPanel id="admin-outputs">
    <template #header>
      <UDashboardNavbar title="Outputs">
        <template #right>
          <UButton
            color="error"
            variant="subtle"
            icon="i-lucide-trash-2"
            label="Clean up old outputs"
            @click="openCleanupSlideover"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="searchInput"
            icon="i-lucide-search"
            placeholder="Search outputs, workspaces, projects, or Actions..."
            aria-label="Search outputs"
            class="w-full sm:w-96"
          >
            <template v-if="searchInput" #trailing>
              <UButton
                color="neutral"
                variant="link"
                icon="i-lucide-x"
                :padded="false"
                aria-label="Clear search"
                @click="searchInput = ''"
              />
            </template>
          </UInput>
        </template>
        <template #right>
          <UButton
            color="neutral"
            variant="ghost"
            icon="i-lucide-refresh-cw"
            label="Refresh"
            :loading="pending"
            aria-label="Refresh outputs"
            @click="refresh"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UAlert
        v-if="error"
        color="error"
        variant="soft"
        icon="i-lucide-alert-circle"
        title="Failed to load outputs"
        :description="extractApiErrorMessage(error, 'Could not load stored outputs.')"
        class="mb-4"
      />

      <div v-if="pending && outputs.length === 0" class="space-y-2 p-4">
        <USkeleton v-for="index in 4" :key="index" class="h-14 w-full" />
      </div>

      <UEmpty
        v-else-if="totalItems === 0"
        variant="naked"
        icon="i-lucide-archive"
        :title="debouncedSearch ? 'No matching outputs' : 'No stored outputs'"
        :description="debouncedSearch ? 'Try a different search.' : 'Completed Action outputs will appear here.'"
      />

      <div v-else class="flex min-h-0 flex-col">
        <AppTable
          table-id="admin-outputs"
          :data="outputs"
          :columns="columns"
          :loading="pending"
          class="px-4 pb-4"
        />

        <div class="flex flex-col gap-4 border-t border-default px-4 py-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="text-sm text-muted">
            Showing {{ showingFrom }} to {{ showingTo }} of {{ totalItems }} outputs
          </div>

          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPageModel"
              :items="itemsPerPageOptions"
              value-key="value"
              class="w-32"
              size="sm"
            />
            <UPagination
              v-model:page="page"
              :total="totalItems"
              :items-per-page="itemsPerPage"
              :disabled="totalPages <= 1"
              show-edges
              :sibling-count="1"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>

  <UiResponsiveSlideover
    v-model:open="isCleanupSlideoverOpen"
    :ui="{ content: 'max-w-3xl' }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Clean up old outputs"
        icon="i-lucide-trash-2"
        description="Review outputs by creation age before selecting which ones to remove."
      />
    </template>

    <template #body>
      <div class="space-y-5">
        <div class="flex flex-wrap items-end gap-3">
          <UFormField label="Older than" class="min-w-44">
            <UInput
              v-model="cleanupOlderThanDaysInput"
              type="number"
              min="1"
              aria-label="Older than days"
              @keyup.enter="previewCleanup"
            >
              <template #trailing>
                <span class="text-sm text-muted">days</span>
              </template>
            </UInput>
          </UFormField>
          <UButton
            color="primary"
            icon="i-lucide-search"
            label="Show matching outputs"
            :loading="isCleanupPreviewLoading"
            @click="previewCleanup"
          />
        </div>

        <UAlert
          v-if="cleanupPreviewError"
          color="error"
          variant="soft"
          icon="i-lucide-alert-circle"
          title="Could not load matching outputs"
          :description="cleanupPreviewError"
        />

        <div v-if="isCleanupPreviewLoading" class="space-y-2">
          <USkeleton v-for="index in 5" :key="index" class="h-16 w-full" />
        </div>

        <UEmpty
          v-else-if="cleanupPreviewReady && cleanupPreviewOutputs.length === 0"
          variant="naked"
          icon="i-lucide-archive"
          title="No matching outputs"
          description="No READY outputs were created before this timeframe."
        />

        <div v-else-if="cleanupPreviewReady" class="space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p class="font-medium">
                Matching outputs
              </p>
              <p class="text-sm text-muted">
                {{ selectedCleanupOutputs.length }} of {{ cleanupPreviewOutputs.length }} selected · {{ formatBytes(cleanupSelectedTotalSize) }} selected size
              </p>
            </div>
            <UButton
              size="sm"
              color="neutral"
              variant="ghost"
              :label="allCleanupOutputsSelected ? 'Clear all' : 'Select all'"
              :disabled="cleanupPreviewOutputs.length === 0"
              @click="toggleAllCleanupOutputs"
            />
          </div>

          <div class="max-h-[55svh] space-y-2 overflow-y-auto pr-1">
            <label
              v-for="output in cleanupPreviewOutputs"
              :key="output.id"
              class="flex cursor-pointer items-center gap-3 rounded-lg border border-default p-3 transition-colors hover:bg-elevated/50"
            >
              <UCheckbox
                :model-value="selectedCleanupOutputIds.has(output.id)"
                :aria-label="`Select ${output.processorName} output`"
                @update:model-value="toggleCleanupOutput(output.id)"
              />
              <div class="min-w-0 flex-1">
                <p class="truncate font-medium">{{ output.processorName }}</p>
                <p class="truncate text-sm text-muted">
                  {{ workspaceLabel(output) }} · {{ output.projectName }}
                </p>
              </div>
              <div class="shrink-0 text-right text-sm">
                <p class="tabular-nums">{{ formatBytes(output.totalSizeBytes) }}</p>
                <p class="text-xs text-muted">{{ formatDate(output.created) }}</p>
              </div>
            </label>
          </div>
        </div>

        <p v-else class="text-sm text-muted">
          Choose a timeframe and show the matching outputs before deleting anything. Indefinitely retained outputs are included.
        </p>
      </div>
    </template>

    <template #footer>
      <UButton
        color="neutral"
        variant="ghost"
        :disabled="isBulkDeleting"
        @click="isCleanupSlideoverOpen = false"
      >
        Cancel
      </UButton>
      <UButton
        color="error"
        icon="i-lucide-trash-2"
        :loading="isBulkDeleting"
        :disabled="!cleanupPreviewReady || selectedCleanupOutputs.length === 0"
        @click="bulkDeleteCleanupOutputs"
      >
        Delete {{ selectedCleanupOutputs.length }} selected
      </UButton>
    </template>
  </UiResponsiveSlideover>
</template>
