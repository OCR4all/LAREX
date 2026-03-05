<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import { globalKey } from '@/utils/fetch-keys'

const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')
const NuxtTime = resolveComponent('NuxtTime')

const toast = useToast()
const { refreshUserTransfers, refreshWorkspaceTransfers } = useDataRefresh()

type TransferRequest = {
  id: string
  projectId?: string
  projectName?: string
  resourceId?: string
  resourceName?: string
  resourceType?: 'CODEC' | 'VIRTUAL_KEYBOARD' | 'LABEL_SET'
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

const { data: projectTransfers, refresh: refreshProjectTransfers } = await useFetch<TransferRequest[]>('/api/project-transfers/my-requests', {
  key: globalKey('user', 'project-transfers', 'my-requests'),
  default: () => []
})
const { data: resourceTransfers, refresh: refreshResourceTransfers } = await useFetch<TransferRequest[]>('/api/resource-transfers/my-requests', {
  key: globalKey('user', 'resource-transfers', 'my-requests'),
  default: () => []
})

const allTransfers = computed(() => [
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
    cell: ({ row }: { row: any }) => h('div', [
      h('p', { class: 'font-medium' }, row.original.projectName || row.original.resourceName),
      h('p', { class: 'text-xs text-muted' }, row.original.resourceType || 'Project')
    ])
  },
  {
    accessorKey: 'transferType',
    header: 'Type',
    cell: ({ row }: { row: any }) => h(UBadge, { color: 'neutral', variant: 'soft', size: 'sm' }, () => row.original.transferType)
  },
  {
    accessorKey: 'target',
    header: 'From → To',
    cell: ({ row }: { row: any }) => h('span', { class: 'text-sm' }, `${row.original.sourceWorkspaceName} → ${row.original.targetWorkspaceName}`)
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }: { row: any }) => h(UBadge, { color: statusColors[row.original.status] || 'neutral', variant: 'soft', size: 'sm' }, () => row.original.status)
  },
  {
    accessorKey: 'created',
    header: 'Created',
    cell: ({ row }: { row: any }) => h(NuxtTime, { datetime: row.original.created })
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }: { row: any }) => row.original.status === 'PENDING'
      ? h(UButton, { icon: 'i-lucide-x', color: 'neutral', variant: 'ghost', size: 'sm', onClick: () => cancelRequest(row.original) }, () => 'Cancel')
      : null
  }
]
</script>

<template>
  <UDashboardPanel id="transfers">
    <template #header>
      <UDashboardNavbar title="Transfer Requests">
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            @click="() => { refreshProjectTransfers(); refreshResourceTransfers() }"
          >
            Refresh
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-6 p-4">
        <div v-if="allTransfers.length === 0" class="text-center py-12">
          <UIcon name="i-lucide-send" class="mx-auto text-4xl text-gray-400 mb-4" />
          <p class="text-muted">
            No transfer requests yet
          </p>
        </div>

        <UTable v-else :columns="columns" :data="allTransfers" />
      </div>
    </template>
  </UDashboardPanel>
</template>
