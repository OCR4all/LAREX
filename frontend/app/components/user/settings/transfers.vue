<script setup lang="ts">
import type { WorkspaceToolkitResourceType } from '@/types/capabilities'

const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')

const toast = useToast()
const { refreshUserTransfers, refreshWorkspaceTransfers } = useDataRefresh()

type TransferRequest = {
  id: string
  projectId?: string
  projectName?: string
  resourceId?: string
  resourceName?: string
  resourceType?: WorkspaceToolkitResourceType
  sourceWorkspaceId: string
  sourceWorkspaceName: string
  targetWorkspaceId: string
  targetWorkspaceName: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED'
  transferType: 'MOVE' | 'COPY'
  message?: string
  rejectionReason?: string
  created: string
}

type TransferRow = TransferRequest & { type: 'project' | 'resource' }

const { data: projectTransfers, refresh: refreshProjectTransfers } = await useFetch<TransferRequest[]>('/api/project-transfers/my-requests', {
  key: globalKey('user', 'project-transfers', 'my-requests'),
  default: () => []
})
const { data: resourceTransfers, refresh: refreshResourceTransfers } = await useFetch<TransferRequest[]>('/api/resource-transfers/my-requests', {
  key: globalKey('user', 'resource-transfers', 'my-requests'),
  default: () => []
})

const allTransfers = computed<TransferRow[]>(() => [
  ...(projectTransfers.value || []).map(t => ({ ...t, type: 'project' as const })),
  ...(resourceTransfers.value || []).map(t => ({ ...t, type: 'resource' as const }))
].sort((a, b) => new Date(b.created).getTime() - new Date(a.created).getTime()))

async function cancelRequest(request: TransferRequest & { type: string }) {
  try {
    const endpoint = request.type === 'project' ? `/api/project-transfers/${request.id}/cancel` : `/api/resource-transfers/${request.id}/cancel`
    await $fetch(endpoint, { method: 'POST' })
    toast.add({ title: 'Request cancelled', color: 'success' })
    await Promise.all([
      refreshUserTransfers(),
      refreshWorkspaceTransfers(request.sourceWorkspaceId),
      refreshWorkspaceTransfers(request.targetWorkspaceId)
    ])
  } catch {
    toast.add({ title: 'Failed to cancel', color: 'error' })
  }
}

function getRowActions(row: TransferRow) {
  if (row.status !== 'PENDING') return []

  return [{
    label: 'Cancel',
    icon: 'i-lucide-x',
    onSelect: () => cancelRequest(row)
  }]
}

const statusColors: Record<string, 'warning' | 'success' | 'error' | 'neutral'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  COMPLETED: 'success',
  REJECTED: 'error',
  CANCELLED: 'neutral'
}

const columns = [
  {
    accessorKey: 'name',
    header: 'Resource',
    cell: ({ row }: { row: { original: TransferRow } }) => h('div', [
      h('p', { class: 'font-medium' }, row.original.projectName || row.original.resourceName),
      h('p', { class: 'text-xs text-muted' }, row.original.resourceType || 'Project')
    ])
  },
  {
    accessorKey: 'transferType',
    header: 'Type',
    cell: ({ row }: { row: { original: TransferRow } }) => h(UBadge, { color: 'neutral', variant: 'soft', size: 'sm' }, () => row.original.transferType)
  },
  {
    accessorKey: 'target',
    header: 'From → To',
    cell: ({ row }: { row: { original: TransferRow } }) => h('span', { class: 'text-sm' }, `${row.original.sourceWorkspaceName} → ${row.original.targetWorkspaceName}`)
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }: { row: { original: TransferRow } }) => h(UBadge, { color: statusColors[row.original.status] || 'neutral', variant: 'soft', size: 'sm' }, () => row.original.status)
  },
  {
    accessorKey: 'created',
    header: 'Created'
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }: { row: { original: TransferRow } }) => getRowActions(row.original).length > 0
      ? h(UButton, { icon: 'i-lucide-x', color: 'neutral', variant: 'ghost', size: 'sm', onClick: () => cancelRequest(row.original) }, () => 'Cancel')
      : null
  }
]

const contextMenuTransfer = ref<TransferRow | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuTransfer.value) return []
  const actions = getRowActions(contextMenuTransfer.value)
  if (actions.length === 0) return []
  return [actions]
})

function handleRowContextMenu(_event: Event, row: { original: TransferRow }) {
  contextMenuTransfer.value = row.original as TransferRow
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold text-highlighted">
          Transfer Requests
        </h2>
        <p class="mt-1 text-sm text-muted">
          Manage your project and resource transfers.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <AppTableColumnsDropdown table-id="settings-transfers" :columns="columns" />
        <UButton
          icon="i-lucide-refresh-cw"
          color="neutral"
          variant="ghost"
          label="Refresh"
          @click="() => { refreshProjectTransfers(); refreshResourceTransfers() }"
        />
      </div>
    </div>
    <div v-if="allTransfers.length === 0" class="text-center py-12">
      <UIcon name="i-lucide-send" class="mx-auto size-10 text-muted mb-4" />
      <p class="text-muted">
        No transfer requests yet
      </p>
    </div>
    <UContextMenu v-else :items="contextMenuItems as any">
      <AppTable
        table-id="settings-transfers"
        :columns="columns"
        :data="allTransfers"
        @contextmenu="handleRowContextMenu"
      />
    </UContextMenu>
  </div>
</template>
