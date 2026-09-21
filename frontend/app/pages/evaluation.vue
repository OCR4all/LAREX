<!-- The compact render callbacks below intentionally keep table cell definitions together. -->
<!-- eslint-disable @stylistic/max-statements-per-line -->
<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { ActionRun, ActionRunStatus } from '@/types/action'

await useWorkspaceBootstrap()
const workspaceStore = useWorkspaceStore()
const toast = useToast()
const realtime = useRealtimeSocket()
const workspaceId = computed(() => workspaceStore.selectedWorkspaceId)
const runs = ref<ActionRun[]>([])
const loading = ref(false)
const search = ref('')
const status = ref<'ALL' | ActionRunStatus>('ALL')
const page = ref(1)
const pageSize = ref(25)
const cancelling = ref<string | null>(null)
let unsubscribe: (() => void) | null = null
let timer: ReturnType<typeof setTimeout> | null = null

const statusOptions = [
  { label: 'All statuses', value: 'ALL' as const },
  ...(['QUEUED', 'PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED', 'COMPLETED', 'FAILED', 'CANCELLED'] as ActionRunStatus[]).map(value => ({ label: value.replaceAll('_', ' '), value }))
]
const filtered = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return runs.value.filter(run => status.value === 'ALL' || run.status === status.value).filter((run) => {
    if (!needle) return true
    return [run.datasetLabel, run.processorName, run.processorKey, run.status, run.statusMessage, run.errorMessage].some(value => value?.toLowerCase().includes(needle))
  }).sort((a, b) => Date.parse(b.updated) - Date.parse(a.updated))
})
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
watch([search, status], () => { page.value = 1 })
watch(totalPages, (value) => { if (page.value > value) page.value = value })

const UButtonComponent = resolveComponent('UButton')
const UBadgeComponent = resolveComponent('UBadge')
const UProgressComponent = resolveComponent('UProgress')
const columns = computed<TableColumn<ActionRun>[]>(() => [
  { id: 'dataset', header: 'Dataset', cell: ({ row }) => h('span', { class: 'truncate font-medium' }, row.original.datasetLabel || 'Dataset') },
  { id: 'action', header: 'Action', cell: ({ row }) => h('span', { class: 'truncate' }, row.original.processorName) },
  { id: 'status', header: 'Status', cell: ({ row }) => h(UBadgeComponent, { size: 'sm', variant: 'soft', color: statusColor(row.original.status) }, () => row.original.status.replaceAll('_', ' ')) },
  { id: 'inputs', header: 'Inputs', cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.inputCount ?? 0)) },
  { id: 'train', header: 'Train', cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.splitCounts?.TRAIN ?? 0)) },
  { id: 'val', header: 'Val', cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.splitCounts?.VAL ?? 0)) },
  { id: 'test', header: 'Test', cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.splitCounts?.TEST ?? 0)) },
  { id: 'progress', header: 'Progress', cell: ({ row }) => h(UProgressComponent, { modelValue: row.original.progressPercent, max: 100, size: 'xs', color: row.original.status === 'COMPLETED' ? 'success' : 'primary', class: 'w-20' }) },
  { accessorKey: 'updated', header: 'Updated' },
  { id: 'actions', header: '', cell: ({ row }) => row.original.canCancel && !['COMPLETED', 'FAILED', 'CANCELLED'].includes(row.original.status) ? h(UButtonComponent, { 'color': 'warning', 'variant': 'ghost', 'size': 'xs', 'icon': 'i-lucide-ban', 'loading': cancelling.value === row.original.id, 'aria-label': row.original.status === 'CANCEL_REQUESTED' ? 'Force cancel evaluation' : 'Cancel evaluation', 'title': row.original.status === 'CANCEL_REQUESTED' ? 'Force cancel evaluation' : 'Cancel evaluation', 'onClick': (event: MouseEvent) => { event.stopPropagation(); void cancelRun(row.original) } }) : null }
])

async function loadRuns() {
  if (!workspaceId.value) return
  loading.value = true
  try {
    const all = await $fetch<ActionRun[]>(`/api/workspaces/${workspaceId.value}/actions/runs`)
    runs.value = all.filter(run => run.kind === 'EVALUATION')
  } catch (error: unknown) {
    toast.add({ title: 'Could not load evaluation runs', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally { loading.value = false }
}
async function cancelRun(run: ActionRun) {
  cancelling.value = run.id
  try {
    const updated = await $fetch<ActionRun>(`/api/workspaces/${workspaceId.value}/actions/runs/${run.id}/cancel${run.status === 'CANCEL_REQUESTED' ? '?force=true' : ''}`, { method: 'POST' })
    runs.value = runs.value.map(item => item.id === updated.id ? updated : item)
  } catch (error: unknown) {
    toast.add({ title: 'Could not cancel evaluation', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally { cancelling.value = null }
}
function statusColor(value: ActionRunStatus): 'success' | 'error' | 'neutral' | 'warning' | 'primary' {
  if (value === 'COMPLETED') return 'success'
  if (value === 'FAILED') return 'error'
  if (value === 'CANCELLED') return 'neutral'
  if (value === 'QUEUED' || value === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}
watch(workspaceId, () => { void loadRuns() }, { immediate: true })
onMounted(() => {
  unsubscribe = realtime.subscribe((message) => {
    if (message.type !== 'ACTION_RUN_UPDATED' || timer) return
    const payload = message.payload as { workspaceId?: string } | null
    if (payload?.workspaceId !== workspaceId.value) return
    timer = setTimeout(() => { timer = null; void loadRuns() }, 100)
  })
})
onBeforeUnmount(() => { unsubscribe?.(); if (timer) clearTimeout(timer) })
</script>

<template>
  <UDashboardPanel id="evaluation-runs">
    <template #header>
      <UDashboardNavbar title="Evaluation">
        <template #right>
          <UButton to="/evaluation/new" icon="i-lucide-plus">
            New Evaluation
          </UButton>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="search"
            icon="i-lucide-search"
            placeholder="Search dataset or Action…"
            class="w-full sm:w-64"
          /><USelectMenu
            v-model="status"
            :items="statusOptions"
            value-key="value"
            class="w-full sm:w-48"
          />
        </template>
        <template #right>
          <UButton
            color="neutral"
            variant="ghost"
            icon="i-lucide-refresh-cw"
            size="sm"
            square
            aria-label="Refresh evaluation runs"
            title="Refresh"
            :loading="loading"
            @click="loadRuns"
          />
        </template>
      </UDashboardToolbar>
    </template>
    <template #body>
      <UEmpty
        v-if="!runs.length"
        variant="naked"
        icon="i-lucide-chart-no-axes-combined"
        title="No evaluation runs yet"
        description="Evaluate a model against a frozen dataset to see results here"
      >
        <template #actions>
          <UButton to="/evaluation/new" icon="i-lucide-plus">
            New Evaluation
          </UButton>
        </template>
      </UEmpty>
      <div v-else class="flex h-full min-h-0 flex-col">
        <UEmpty
          v-if="!filtered.length"
          variant="naked"
          icon="i-lucide-filter-x"
          title="No matching evaluation runs"
          description="Adjust the current filters."
        /><AppTable
          v-else
          table-id="evaluation-runs"
          :columns="columns"
          :data="paginated"
          :default-visible-column-ids="['dataset', 'action', 'status', 'inputs', 'train', 'val', 'test', 'progress', 'updated', 'actions']"
          class="flex-1 px-4 pb-4"
          @select="(row: any) => navigateTo(`/evaluation/${row.original.id}`)"
        /><div v-if="filtered.length" class="flex items-center justify-between border-t border-default px-4 py-4">
          <span class="text-sm text-muted">{{ filtered.length }} runs</span><div class="flex items-center gap-4">
            <USelect
              v-model="pageSize"
              :items="[10, 25, 50, 100]"
              class="w-24"
              size="sm"
            /><UPagination
              v-model:page="page"
              :total="filtered.length"
              :items-per-page="pageSize"
              :disabled="totalPages <= 1"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
