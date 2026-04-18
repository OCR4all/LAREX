<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import { globalKey } from '@/utils/fetch-keys'
import { showApiErrorToast } from '@/utils/error-toast'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')

const toast = useToast()

const datatableUi = {
  base: 'table-fixed border-separate border-spacing-0',
  thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
  tbody: '[&>tr]:last:[&>td]:border-b-0',
  th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
  td: 'border-b border-default',
  separator: 'h-0'
}

interface OrphanedFile {
  path: string
  type: 'image' | 'xml' | 'thumbnail' | 'temp'
  sizeBytes: number
  lastModified: string
}

interface StorageOverview {
  totalUsedBytes: number
  totalUsedFormatted: string
  totalImages: number
  totalXmlFiles: number
  totalThumbnails: number
  orphanedImages: number
  orphanedXmlFiles: number
  orphanedThumbnails: number
  orphanedTempFiles: number
  orphanedTotalBytes: number
  orphanedTotalFormatted: string
}

interface OrphanedFilesResponse {
  files: OrphanedFile[]
  totalCount: number
  totalSizeBytes: number
  totalSizeFormatted: string
}

interface CleanupResponse {
  deletedCount: number
  failedCount: number
  freedBytes: number
  freedFormatted: string
  errors: string[]
}

interface OrphanedFileTableRow {
  original: OrphanedFile
}

interface OrphanedFileTableHeaderContext {
  table: {
    getRowModel: () => {
      rows: OrphanedFileTableRow[]
    }
  }
}

const { data: overview, refresh: refreshOverview, pending: overviewPending } = await useFetch<StorageOverview>('/api/admin/storage/overview', {
  key: globalKey('admin', 'storage', 'overview')
})

const selectedFiles = ref<Set<string>>(new Set())
const isDeleting = ref(false)
const isDeletingAll = ref(false)

const typeFilter = ref<string | undefined>(undefined)
const searchQuery = ref('')

const page = ref(1)
const itemsPerPage = ref(25)
const itemsPerPageOptions = [10, 25, 50, 100].map(value => ({ label: `${value} per page`, value }))

const typeOptions = [
  { value: 'image', label: 'Images' },
  { value: 'xml', label: 'XML Files' },
  { value: 'thumbnail', label: 'Thumbnails' },
  { value: 'temp', label: 'Temp Files' }
]

const orphanedQuery = computed(() => {
  const query: Record<string, string | number> = {
    page: page.value,
    size: itemsPerPage.value
  }

  if (typeFilter.value) {
    query.type = typeFilter.value
  }

  const trimmedSearch = searchQuery.value.trim()
  if (trimmedSearch) {
    query.search = trimmedSearch
  }

  return query
})

const orphanedKey = computed(() => globalKey(
  'admin',
  'storage',
  'orphaned',
  page.value,
  itemsPerPage.value,
  typeFilter.value || 'all',
  searchQuery.value.trim() || 'none'
))

const { data: orphanedFiles, refresh: refreshOrphaned, pending: orphanedPending } = await useFetch<OrphanedFilesResponse>('/api/admin/storage/orphaned', {
  key: orphanedKey,
  query: orphanedQuery,
  watch: [orphanedQuery],
  default: () => ({
    files: [],
    totalCount: 0,
    totalSizeBytes: 0,
    totalSizeFormatted: '0 B'
  })
})

const currentPageFiles = computed(() => orphanedFiles.value?.files ?? [])
const totalItems = computed(() => orphanedFiles.value?.totalCount ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPage.value)))
const filteredTotalSize = computed(() => orphanedFiles.value?.totalSizeBytes ?? 0)
const showingFrom = computed(() => totalItems.value === 0 ? 0 : (page.value - 1) * itemsPerPage.value + 1)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, totalItems.value))
const hasActiveFilters = computed(() => Boolean(typeFilter.value || searchQuery.value.trim()))

watch([typeFilter, searchQuery, itemsPerPage], () => {
  page.value = 1
  clearSelection()
})

watch(totalPages, (newTotalPages) => {
  if (page.value > newTotalPages) {
    page.value = newTotalPages
  }
})

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function getErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message.length > 0) {
      return message
    }
  }

  return fallback
}

function toggleFileSelection(path: string) {
  const newSet = new Set(selectedFiles.value)
  if (newSet.has(path)) {
    newSet.delete(path)
  } else {
    newSet.add(path)
  }
  selectedFiles.value = newSet
}

function toggleAllOnPage() {
  const currentPagePaths = currentPageFiles.value.map(file => file.path)
  const allSelected = currentPagePaths.length > 0 && currentPagePaths.every(path => selectedFiles.value.has(path))

  const newSet = new Set(selectedFiles.value)
  if (allSelected) {
    currentPagePaths.forEach(path => newSet.delete(path))
  } else {
    currentPagePaths.forEach(path => newSet.add(path))
  }
  selectedFiles.value = newSet
}

function clearSelection() {
  selectedFiles.value = new Set()
}

function clearFilters() {
  typeFilter.value = undefined
  searchQuery.value = ''
  page.value = 1
}

async function refreshOrphanedWithPageGuard() {
  await refreshOrphaned()

  const maxPage = Math.max(1, Math.ceil((orphanedFiles.value?.totalCount ?? 0) / itemsPerPage.value))
  if (page.value > maxPage) {
    page.value = maxPage
    await refreshOrphaned()
  }
}

async function deleteSelectedFiles() {
  if (selectedFiles.value.size === 0) return

  isDeleting.value = true
  try {
    const result = await $fetch<CleanupResponse>('/api/admin/storage/cleanup', {
      method: 'POST',
      body: { paths: Array.from(selectedFiles.value) }
    })

    if (result.deletedCount > 0) {
      toast.add({
        title: 'Files Deleted',
        description: `Deleted ${result.deletedCount} files, freed ${result.freedFormatted}`,
        color: 'success',
        icon: 'i-lucide-check-circle'
      })
    }

    if (result.failedCount > 0) {
      toast.add({
        title: 'Some Deletions Failed',
        description: `${result.failedCount} files could not be deleted`,
        color: 'warning',
        icon: 'i-lucide-alert-triangle'
      })
    }

    clearSelection()
    await Promise.all([refreshOverview(), refreshOrphanedWithPageGuard()])
  } catch (error: unknown) {
    showApiErrorToast({
      title: 'Cleanup Failed',
      error,
      fallback: getErrorMessage(error, 'Failed to delete files')
    })
  } finally {
    isDeleting.value = false
  }
}

async function deleteSingleFile(path: string) {
  isDeleting.value = true
  try {
    const result = await $fetch<CleanupResponse>('/api/admin/storage/cleanup', {
      method: 'POST',
      body: { paths: [path] }
    })

    if (result.deletedCount > 0) {
      toast.add({
        title: 'File Deleted',
        description: `Freed ${result.freedFormatted}`,
        color: 'success',
        icon: 'i-lucide-check-circle'
      })
    } else if (result.failedCount > 0) {
      toast.add({
        title: 'Deletion Failed',
        description: result.errors?.[0] || 'Could not delete file',
        color: 'error',
        icon: 'i-lucide-alert-circle'
      })
    }

    await Promise.all([refreshOverview(), refreshOrphanedWithPageGuard()])
  } catch (error: unknown) {
    showApiErrorToast({
      title: 'Deletion Failed',
      error,
      fallback: getErrorMessage(error, 'Failed to delete file')
    })
  } finally {
    isDeleting.value = false
  }
}

async function deleteAllOrphanedFiles() {
  isDeletingAll.value = true
  try {
    const result = await $fetch<CleanupResponse>('/api/admin/storage/cleanup/all', {
      method: 'POST'
    })

    if (result.deletedCount > 0) {
      toast.add({
        title: 'Cleanup Complete',
        description: `Deleted ${result.deletedCount} orphaned files, freed ${result.freedFormatted}`,
        color: 'success',
        icon: 'i-lucide-check-circle'
      })
    }

    if (result.failedCount > 0) {
      toast.add({
        title: 'Some Deletions Failed',
        description: `${result.failedCount} files could not be deleted`,
        color: 'warning',
        icon: 'i-lucide-alert-triangle'
      })
    }

    clearSelection()
    await Promise.all([refreshOverview(), refreshOrphanedWithPageGuard()])
  } catch (error: unknown) {
    showApiErrorToast({
      title: 'Cleanup Failed',
      error,
      fallback: getErrorMessage(error, 'Failed to delete files')
    })
  } finally {
    isDeletingAll.value = false
  }
}

const typeColors: Record<string, string> = {
  image: 'primary',
  xml: 'info',
  thumbnail: 'warning',
  temp: 'error'
}

const columns: TableColumn<OrphanedFile>[] = [
  {
    id: 'select',
    header: ({ table }: OrphanedFileTableHeaderContext) => {
      const rows = table.getRowModel().rows
      const allSelected = rows.length > 0 && rows.every(row => selectedFiles.value.has(row.original.path))
      const someSelected = rows.some(row => selectedFiles.value.has(row.original.path)) && !allSelected
      return h('input', {
        type: 'checkbox',
        checked: allSelected,
        indeterminate: someSelected,
        onChange: toggleAllOnPage,
        class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
      })
    },
    cell: ({ row }: { row: OrphanedFileTableRow }) => h('input', {
      type: 'checkbox',
      checked: selectedFiles.value.has(row.original.path),
      onChange: () => toggleFileSelection(row.original.path),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'path',
    header: 'File Path',
    cell: ({ row }: { row: OrphanedFileTableRow }) => h('span', {
      class: 'font-mono text-xs truncate block max-w-md',
      title: row.original.path
    }, row.original.path)
  },
  {
    accessorKey: 'type',
    header: 'Type',
    cell: ({ row }: { row: OrphanedFileTableRow }) => h(UBadge, {
      color: typeColors[row.original.type] || 'neutral',
      variant: 'soft',
      size: 'sm'
    }, () => row.original.type)
  },
  {
    accessorKey: 'sizeBytes',
    header: 'Size',
    cell: ({ row }: { row: OrphanedFileTableRow }) => formatBytes(row.original.sizeBytes)
  },
  {
    accessorKey: 'lastModified',
    header: 'Last Modified'
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }: { row: OrphanedFileTableRow }) => h(UButton, {
      icon: 'i-lucide-trash-2',
      color: 'error',
      variant: 'ghost',
      size: 'xs',
      onClick: () => deleteSingleFile(row.original.path)
    })
  }
]

function getRowActions(file: OrphanedFile) {
  return [{
    label: 'Delete',
    icon: 'i-lucide-trash-2',
    color: 'error' as const,
    onSelect: () => deleteSingleFile(file.path)
  }]
}

const contextMenuFile = ref<OrphanedFile | null>(null)
const contextMenuItems = computed<DropdownMenuItem[][]>(() => {
  if (!contextMenuFile.value) return []
  return [getRowActions(contextMenuFile.value)]
})

function handleRowContextMenu(_event: Event, row: { original: OrphanedFile }) {
  contextMenuFile.value = row.original
}

async function refreshAll() {
  await Promise.all([refreshOverview(), refreshOrphaned()])
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Storage Cleanup" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            label="Refresh"
            :loading="overviewPending || orphanedPending"
            @click="refreshAll"
          />
          <UButton
            v-if="orphanedFiles && orphanedFiles.totalCount > 0"
            color="error"
            variant="subtle"
            icon="i-lucide-trash-2"
            label="Delete All Orphaned"
            :loading="isDeletingAll"
            @click="deleteAllOrphanedFiles"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="searchQuery"
            placeholder="Search file paths..."
            icon="i-lucide-search"
            class="w-full sm:w-72"
          >
            <template v-if="searchQuery" #trailing>
              <UButton
                color="neutral"
                variant="link"
                icon="i-lucide-x"
                :padded="false"
                @click="searchQuery = ''"
              />
            </template>
          </UInput>

          <USelectMenu
            v-model="typeFilter"
            :items="typeOptions"
            placeholder="All types"
            class="w-full sm:w-40"
            value-key="value"
          />

          <UButton
            v-if="hasActiveFilters"
            color="neutral"
            variant="ghost"
            size="sm"
            @click="clearFilters"
          >
            Clear Filters
          </UButton>
        </template>

        <template #right>
          <div class="flex items-center gap-2">
            <UBadge
              v-if="totalItems > 0"
              color="neutral"
              variant="subtle"
            >
              {{ totalItems }} files
            </UBadge>
            <UBadge
              v-if="totalItems > 0"
              color="neutral"
              variant="subtle"
            >
              {{ formatBytes(filteredTotalSize) }}
            </UBadge>
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="mb-6 grid grid-cols-1 gap-3 md:grid-cols-4">
        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Total Storage Used
          </p>
          <div class="mt-2 text-xl font-semibold text-highlighted">
            {{ overview?.totalUsedFormatted || '0 B' }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Images in Database
          </p>
          <div class="mt-2 text-xl font-semibold text-primary">
            {{ overview?.totalImages || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            XML Files in Database
          </p>
          <div class="mt-2 text-xl font-semibold text-info">
            {{ overview?.totalXmlFiles || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Thumbnails on Disk
          </p>
          <div class="mt-2 text-xl font-semibold text-warning">
            {{ overview?.totalThumbnails || 0 }}
          </div>
        </div>
      </div>

      <div class="mb-6 grid grid-cols-1 gap-3 md:grid-cols-5">
        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Orphaned Images
          </p>
          <div class="mt-2 text-lg font-semibold" :class="{ 'text-error': (overview?.orphanedImages || 0) > 0, 'text-highlighted': (overview?.orphanedImages || 0) === 0 }">
            {{ overview?.orphanedImages || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Orphaned XML
          </p>
          <div class="mt-2 text-lg font-semibold" :class="{ 'text-error': (overview?.orphanedXmlFiles || 0) > 0, 'text-highlighted': (overview?.orphanedXmlFiles || 0) === 0 }">
            {{ overview?.orphanedXmlFiles || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Orphaned Thumbnails
          </p>
          <div class="mt-2 text-lg font-semibold" :class="{ 'text-error': (overview?.orphanedThumbnails || 0) > 0, 'text-highlighted': (overview?.orphanedThumbnails || 0) === 0 }">
            {{ overview?.orphanedThumbnails || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Temp Files
          </p>
          <div class="mt-2 text-lg font-semibold" :class="{ 'text-error': (overview?.orphanedTempFiles || 0) > 0, 'text-highlighted': (overview?.orphanedTempFiles || 0) === 0 }">
            {{ overview?.orphanedTempFiles || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Total Orphaned Size
          </p>
          <div class="mt-2 text-lg font-semibold" :class="{ 'text-error': (overview?.orphanedTotalBytes || 0) > 0, 'text-highlighted': (overview?.orphanedTotalBytes || 0) === 0 }">
            {{ overview?.orphanedTotalFormatted || '0 B' }}
          </div>
        </div>
      </div>

      <div>
        <div class="mb-4 flex items-center justify-between">
          <h3 class="font-semibold">
            Orphaned Files
          </h3>
          <span v-if="totalItems > 0" class="text-sm text-muted">
            {{ totalItems }} files ({{ formatBytes(filteredTotalSize) }})
          </span>
        </div>

        <div v-if="!orphanedPending && totalItems === 0" class="py-12 text-center">
          <UIcon name="i-lucide-check-circle" class="mx-auto text-4xl text-success mb-4" />
          <p class="text-lg font-medium text-success">
            No orphaned files found
          </p>
          <p class="text-sm text-muted mt-1">
            All files in the upload directory are properly linked to database records.
          </p>
        </div>

        <template v-else>
          <UContextMenu :items="contextMenuItems">
            <AppTable
              table-id="admin-storage-orphaned-files"
              :columns="columns"
              :data="currentPageFiles"
              :loading="orphanedPending"
              :ui="datatableUi"
              @contextmenu="handleRowContextMenu"
            />
          </UContextMenu>
        </template>

        <UiFloatingSelectionMenu
          :selected-count="selectedFiles.size"
          @clear="clearSelection"
        >
          <UButton
            color="error"
            variant="ghost"
            size="sm"
            icon="i-lucide-trash-2"
            class="hover:bg-white/10"
            :loading="isDeleting"
            @click="deleteSelectedFiles"
          >
            Delete Selected
          </UButton>
        </UiFloatingSelectionMenu>

        <div v-if="totalItems > 0" class="mt-4 flex flex-col gap-4 border-t border-default pt-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="text-sm text-muted">
            Showing {{ showingFrom }} to {{ showingTo }} of {{ totalItems }} files
          </div>

          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPage"
              :items="itemsPerPageOptions"
              value-key="value"
              class="w-32"
              size="sm"
            />

            <UPagination
              v-model:page="page"
              :total="totalItems"
              :items-per-page="itemsPerPage"
              show-edges
              :sibling-count="1"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
