<script setup lang="ts">
import type { WorkspaceUtilityResourceType } from '@/types/capabilities'

const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')

await useWorkspaceBootstrap()

const toast = useToast()
const workspace = useWorkspaceStore()
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId)
const { refreshWorkspaceTransfers, refreshUserTransfers } = useDataRefresh()

type TransferRequest = {
  id: string
  projectId?: string
  projectName?: string
  resourceId?: string
  resourceName?: string
  resourceType?: WorkspaceUtilityResourceType
  sourceWorkspaceId: string
  sourceWorkspaceName: string
  targetWorkspaceId: string
  targetWorkspaceName: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED'
  transferType: 'MOVE' | 'COPY'
  message?: string
  created: string
}

const { data: incomingProjects } = await useFetch<TransferRequest[]>(
  () => `/api/project-transfers/workspace/${selectedWorkspace.value as string}/incoming`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'project-transfers', 'incoming')
      : globalKey('pending', 'project-transfers', 'incoming')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)
const { data: incomingResources } = await useFetch<TransferRequest[]>(
  () => `/api/resource-transfers/workspace/${selectedWorkspace.value as string}/incoming`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'resource-transfers', 'incoming')
      : globalKey('pending', 'resource-transfers', 'incoming')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)
const { data: outgoingProjects } = await useFetch<TransferRequest[]>(
  () => `/api/project-transfers/workspace/${selectedWorkspace.value as string}/outgoing`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'project-transfers', 'outgoing')
      : globalKey('pending', 'project-transfers', 'outgoing')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)
const { data: outgoingResources } = await useFetch<TransferRequest[]>(
  () => `/api/resource-transfers/workspace/${selectedWorkspace.value as string}/outgoing`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'resource-transfers', 'outgoing')
      : globalKey('pending', 'resource-transfers', 'outgoing')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const incoming = computed(() => [...(incomingProjects.value || []), ...(incomingResources.value || [])])
const outgoing = computed(() => [...(outgoingProjects.value || []), ...(outgoingResources.value || [])])

async function refreshAll() {
  await Promise.all([
    refreshWorkspaceTransfers(selectedWorkspace.value),
    refreshUserTransfers()
  ])
}

async function refreshTransferCaches(request: TransferRequest) {
  await Promise.all([
    refreshWorkspaceTransfers(request.sourceWorkspaceId),
    refreshWorkspaceTransfers(request.targetWorkspaceId),
    refreshUserTransfers()
  ])
}

async function approve(request: TransferRequest) {
  const endpoint = request.projectId ? `/api/project-transfers/${request.id}/approve` : `/api/resource-transfers/${request.id}/approve`
  try {
    await $fetch(endpoint, { method: 'POST' })
    toast.add({ title: 'Transfer approved', color: 'success' })
    await refreshTransferCaches(request)
  } catch {
    toast.add({ title: 'Failed to approve', color: 'error' })
  }
}

async function reject(request: TransferRequest) {
  const endpoint = request.projectId ? `/api/project-transfers/${request.id}/reject` : `/api/resource-transfers/${request.id}/reject`
  try {
    await $fetch(endpoint, { method: 'POST', body: { rejectionReason: '' } })
    toast.add({ title: 'Transfer rejected', color: 'neutral' })
    await refreshTransferCaches(request)
  } catch {
    toast.add({ title: 'Failed to reject', color: 'error' })
  }
}

async function cancel(request: TransferRequest) {
  const endpoint = request.projectId ? `/api/project-transfers/${request.id}/cancel` : `/api/resource-transfers/${request.id}/cancel`
  try {
    await $fetch(endpoint, { method: 'POST' })
    toast.add({ title: 'Request cancelled', color: 'neutral' })
    await refreshTransferCaches(request)
  } catch {
    toast.add({ title: 'Failed to cancel', color: 'error' })
  }
}

function getIncomingActions(request: TransferRequest) {
  return [
    { label: 'Reject', icon: 'i-lucide-x', onSelect: () => reject(request) },
    { label: 'Approve', icon: 'i-lucide-check', onSelect: () => approve(request) }
  ]
}

function getOutgoingActions(request: TransferRequest) {
  return [
    { label: 'Cancel', icon: 'i-lucide-x', onSelect: () => cancel(request) }
  ]
}

const incomingColumns = [
  {
    accessorKey: 'name',
    header: 'Resource',
    cell: ({ row }: { row: { original: TransferRequest } }) => h('div', [
      h('p', { class: 'font-medium' }, row.original.projectName || row.original.resourceName),
      h('p', { class: 'text-xs text-muted' }, row.original.resourceType || 'Project')
    ])
  },
  {
    accessorKey: 'transferType',
    header: 'Type',
    cell: ({ row }: { row: { original: TransferRequest } }) => h(UBadge, { color: 'neutral', variant: 'soft', size: 'sm' }, () => row.original.transferType)
  },
  {
    accessorKey: 'source',
    header: 'From',
    cell: ({ row }: { row: { original: TransferRequest } }) => h('span', { class: 'text-sm' }, row.original.sourceWorkspaceName)
  },
  {
    accessorKey: 'created',
    header: 'Requested'
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }: { row: { original: TransferRequest } }) => h('div', { class: 'flex gap-2' }, [
      h(UButton, { size: 'xs', color: 'neutral', variant: 'outline', onClick: () => reject(row.original) }, () => 'Reject'),
      h(UButton, { size: 'xs', onClick: () => approve(row.original) }, () => 'Approve')
    ])
  }
]

const outgoingColumns = [
  {
    accessorKey: 'name',
    header: 'Resource',
    cell: ({ row }: { row: { original: TransferRequest } }) => h('div', [
      h('p', { class: 'font-medium' }, row.original.projectName || row.original.resourceName),
      h('p', { class: 'text-xs text-muted' }, row.original.resourceType || 'Project')
    ])
  },
  {
    accessorKey: 'transferType',
    header: 'Type',
    cell: ({ row }: { row: { original: TransferRequest } }) => h(UBadge, { color: 'neutral', variant: 'soft', size: 'sm' }, () => row.original.transferType)
  },
  {
    accessorKey: 'target',
    header: 'To',
    cell: ({ row }: { row: { original: TransferRequest } }) => h('span', { class: 'text-sm' }, row.original.targetWorkspaceName)
  },
  {
    accessorKey: 'created',
    header: 'Requested'
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }: { row: { original: TransferRequest } }) => h(UButton, { size: 'xs', color: 'neutral', variant: 'ghost', onClick: () => cancel(row.original) }, () => 'Cancel')
  }
]

const incomingContextRequest = ref<TransferRequest | null>(null)
const incomingContextMenuItems = computed(() => {
  if (!incomingContextRequest.value) return []
  return [getIncomingActions(incomingContextRequest.value)]
})

const outgoingContextRequest = ref<TransferRequest | null>(null)
const outgoingContextMenuItems = computed(() => {
  if (!outgoingContextRequest.value) return []
  return [getOutgoingActions(outgoingContextRequest.value)]
})

function handleIncomingRowContextMenu(_event: Event, row: { original: TransferRequest }) {
  incomingContextRequest.value = row.original as TransferRequest
}

function handleOutgoingRowContextMenu(_event: Event, row: { original: TransferRequest }) {
  outgoingContextRequest.value = row.original as TransferRequest
}
</script>

<template>
  <div class="flex justify-end">
    <UButton
      icon="i-lucide-refresh-cw"
      color="neutral"
      variant="ghost"
      @click="refreshAll"
    >
      Refresh
    </UButton>
  </div>
  <UPageCard
    data-tour="workspace-requests-incoming"
    title="Incoming Requests"
    variant="subtle"
  >
    <div v-if="incoming.length === 0" class="text-center py-8 text-muted">
      <UIcon name="i-lucide-inbox" class="mx-auto text-3xl mb-2" />
      <p>No pending incoming requests</p>
    </div>
    <UContextMenu v-else :items="incomingContextMenuItems as any">
      <AppTable
        table-id="workspace-requests-incoming"
        :columns="incomingColumns"
        :data="incoming"
        @contextmenu="handleIncomingRowContextMenu"
      />
    </UContextMenu>
  </UPageCard>
  <UPageCard
    data-tour="workspace-requests-outgoing"
    title="Outgoing Requests"
    variant="subtle"
  >
    <div v-if="outgoing.length === 0" class="text-center py-8 text-muted">
      <UIcon name="i-lucide-send" class="mx-auto text-3xl mb-2" />
      <p>No pending outgoing requests</p>
    </div>
    <UContextMenu v-else :items="outgoingContextMenuItems as any">
      <AppTable
        table-id="workspace-requests-outgoing"
        :columns="outgoingColumns"
        :data="outgoing"
        @contextmenu="handleOutgoingRowContextMenu"
      />
    </UContextMenu>
  </UPageCard>
</template>
