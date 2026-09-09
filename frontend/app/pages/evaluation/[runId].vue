<!-- eslint-disable @stylistic/max-statements-per-line -->
<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { ActionRun, ActionRunDetail, EvaluationMetric, EvaluationReport } from '@/types/action'

await useWorkspaceBootstrap()
const route = useRoute()
const workspaceStore = useWorkspaceStore()
const toast = useToast()
const workspaceId = computed(() => workspaceStore.selectedWorkspaceId)
const runId = computed(() => String(route.params.runId))
const detail = ref<ActionRunDetail | null>(null)
const baselines = ref<ActionRun[]>([])
const baselineId = ref<string>()
const comparison = ref<{ metrics: Array<{ key: string, currentValue: unknown, baselineValue: unknown, delta: number, direction: string, state: string }>, currentReport: EvaluationReport, baselineReport: EvaluationReport } | null>(null)
const loading = ref(true)
const compareLoading = ref(false)
const tableSearch = ref<Record<string, string>>({})
const tablePages = ref<Record<string, number>>({})
const tablePageSize = 25
const sampleSearch = ref('')
const samplePage = ref(1)
const samplePageSize = 25

const report = computed<EvaluationReport | null>(() => (detail.value?.evaluationReport || detail.value?.resultSummary || null) as EvaluationReport | null)
const baselineOptions = computed(() => baselines.value.map(run => ({ label: `${run.processorName} · ${new Date(run.created).toLocaleString()}`, value: run.id })))
const reportTables = computed(() => report.value?.tables || [])
const filteredSamples = computed(() => {
  const samples = report.value?.samples || []
  const needle = sampleSearch.value.trim().toLowerCase()
  if (!needle) return samples
  return samples.filter(sample => [sample.id, sample.inputId, sample.targetId, sample.label, sample.status, ...Object.values(sample.fields).map(String)]
    .some(value => value?.toLowerCase().includes(needle)))
})
const pagedSamples = computed(() => filteredSamples.value.slice(
  (samplePage.value - 1) * samplePageSize,
  samplePage.value * samplePageSize
))
const filteredRows = (table: EvaluationReport['tables'][number]) => {
  const needle = (tableSearch.value[table.key] || '').toLowerCase().trim()
  if (!needle) return table.rows
  return table.rows.filter(row => Object.values(rowValues(row)).some(value => String(value).toLowerCase().includes(needle)))
}
const pagedRows = (table: EvaluationReport['tables'][number]) => {
  const rows = filteredRows(table)
  const currentPage = Math.min(tablePages.value[table.key] || 1, Math.max(1, Math.ceil(rows.length / tablePageSize)))
  return rows.slice((currentPage - 1) * tablePageSize, currentPage * tablePageSize)
}
const reportTableColumns = (table: EvaluationReport['tables'][number]): TableColumn<Record<string, unknown>>[] => table.columns.map(column => ({
  id: column.key,
  header: column.label,
  accessorFn: row => row[column.key],
  cell: ({ getValue }) => printValue(getValue())
}))
const reportTableRows = (table: EvaluationReport['tables'][number]) => pagedRows(table).map(row => rowValues(row))
const setTablePage = (key: string, page: number) => { tablePages.value[key] = page }
watch(tableSearch, () => { tablePages.value = {} }, { deep: true })
watch(sampleSearch, () => { samplePage.value = 1 })
watch(() => filteredSamples.value.length, (length) => {
  const lastPage = Math.max(1, Math.ceil(length / samplePageSize))
  if (samplePage.value > lastPage) samplePage.value = lastPage
})
function printValue(value: unknown) {
  return value !== null && typeof value === 'object' ? JSON.stringify(value) : String(value ?? '—')
}
function rowValues(row: EvaluationReport['tables'][number]['rows'][number]) {
  return row.values || (row as unknown as { [key: string]: unknown })
}
type EvaluationSampleRow = EvaluationReport['samples'][number] & { sample: string }
const pagedSampleRows = computed<EvaluationSampleRow[]>(() => pagedSamples.value.map(sample => ({
  ...sample,
  sample: sample.label || sample.id
})))
const sampleColumns: TableColumn<EvaluationSampleRow>[] = [
  { accessorKey: 'sample', header: 'Sample' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'fields', header: 'Fields', accessorFn: row => row.fields }
]
function metricValue(metric: EvaluationMetric) {
  if (metric.format === 'PERCENT') return `${(metric.value * 100).toFixed(2)}%`
  if (metric.format === 'INTEGER') return Math.round(metric.value).toLocaleString()
  return metric.value.toLocaleString(undefined, { maximumFractionDigits: 4 })
}
function metricDelta(metric: EvaluationMetric) {
  const match = comparison.value?.metrics.find(item => item.key === metric.key)
  if (!match) return null
  const value = Math.abs(match.delta) < 1e-12 ? 'Neutral' : `${match.delta > 0 ? '+' : ''}${metric.format === 'PERCENT' ? (match.delta * 100).toFixed(2) + ' pp' : match.delta.toFixed(4)}`
  return { value, state: match.state }
}
async function load() {
  if (!workspaceId.value) return
  loading.value = true
  try {
    detail.value = await $fetch<ActionRunDetail>(`/api/workspaces/${workspaceId.value}/actions/runs/${runId.value}`)
    baselines.value = await $fetch<ActionRun[]>(`/api/workspaces/${workspaceId.value}/actions/evaluation/runs/${runId.value}/baselines`)
  } catch (error: unknown) { toast.add({ title: 'Could not load evaluation report', description: error instanceof Error ? error.message : undefined, color: 'error' }) } finally { loading.value = false }
}
async function compare() {
  if (!baselineId.value) { comparison.value = null; return }
  compareLoading.value = true
  try { comparison.value = await $fetch(`/api/workspaces/${workspaceId.value}/actions/evaluation/runs/${runId.value}/compare`, { query: { baselineRunId: baselineId.value } }) as typeof comparison.value } catch (error: unknown) { toast.add({ title: 'Could not compare evaluations', description: error instanceof Error ? error.message : undefined, color: 'error' }) } finally { compareLoading.value = false }
}
watch([workspaceId, runId], load, { immediate: true })
</script>

<template>
  <UDashboardPanel id="evaluation-detail">
    <template #header>
      <UDashboardNavbar :title="detail?.run.processorName || 'Evaluation report'">
        <template #right>
          <UButton
            to="/evaluation"
            color="neutral"
            variant="outline"
            icon="i-lucide-arrow-left"
          >
            Evaluation runs
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <div v-if="loading" class="mx-auto w-full max-w-7xl space-y-4 p-6">
        <USkeleton class="h-24 w-full" /><USkeleton class="h-64 w-full" />
      </div><UEmpty v-else-if="!detail" icon="i-lucide-circle-alert" title="Evaluation not found" /><div v-else class="mx-auto flex w-full max-w-7xl flex-col gap-6 p-6">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="text-sm text-muted">
              {{ detail.run.datasetLabel || 'Dataset' }} · {{ detail.run.status }}
            </p><h1 class="text-2xl font-semibold">
              {{ report?.title || 'Evaluation report' }}
            </h1><p class="font-mono text-xs text-muted">
              {{ report?.profile }} v{{ report?.profileVersion }}
            </p>
          </div><div v-if="baselines.length" class="flex items-center gap-2">
            <USelect
              v-model="baselineId"
              :items="baselineOptions"
              placeholder="Compare with baseline"
              class="w-64"
              @update:model-value="compare"
            /><UIcon v-if="compareLoading" name="i-lucide-loader-circle" class="size-4 animate-spin" />
          </div>
        </div>
        <UAlert
          v-if="report?.warnings?.length"
          color="warning"
          icon="i-lucide-triangle-alert"
          title="Evaluation warnings"
        >
          <ul class="list-disc pl-5">
            <li v-for="warning in report.warnings" :key="warning">
              {{ warning }}
            </li>
          </ul>
        </UAlert>
        <UEmpty
          v-if="!report"
          icon="i-lucide-file-warning"
          title="No evaluation report"
          description="This run did not return a completed report."
        />
        <template v-else>
          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <UCard v-for="metric in report.summary" :key="metric.key" variant="subtle">
              <p class="text-sm text-muted">
                {{ metric.label }}
              </p><p class="mt-1 text-2xl font-semibold">
                {{ metricValue(metric) }}
              </p><p v-if="metricDelta(metric)" class="mt-1 text-xs" :class="metricDelta(metric)?.state === 'IMPROVED' ? 'text-success' : metricDelta(metric)?.state === 'REGRESSED' ? 'text-error' : 'text-muted'">
                {{ metricDelta(metric)?.value }} · {{ metricDelta(metric)?.state.toLowerCase() }}
              </p><p v-if="metric.unit" class="text-xs text-muted">
                {{ metric.unit }}
              </p>
            </UCard>
          </div>
          <UCard v-for="table in reportTables" :key="table.key" variant="outline">
            <template #header>
              <div class="flex items-center justify-between gap-3">
                <div>
                  <h2 class="font-semibold">
                    {{ table.title }}
                  </h2><p class="text-xs text-muted">
                    {{ table.totalRows.toLocaleString() }} rows<span v-if="table.truncated"> · truncated</span>
                  </p>
                </div><UInput
                  v-model="tableSearch[table.key]"
                  icon="i-lucide-search"
                  placeholder="Search table…"
                  class="w-56"
                />
              </div>
            </template><div class="overflow-x-auto">
              <AppTable
                :table-id="`evaluation-${table.key}`"
                :columns="reportTableColumns(table)"
                :data="reportTableRows(table)"
                class="text-sm"
              />
            </div>
            <div v-if="filteredRows(table).length > tablePageSize" class="flex items-center justify-between border-t border-default px-3 py-2">
              <span class="text-xs text-muted">Showing {{ Math.min(((tablePages[table.key] || 1) - 1) * tablePageSize + 1, filteredRows(table).length) }}–{{ Math.min((tablePages[table.key] || 1) * tablePageSize, filteredRows(table).length) }}</span>
              <UPagination
                :page="tablePages[table.key] || 1"
                :total="filteredRows(table).length"
                :items-per-page="tablePageSize"
                @update:page="setTablePage(table.key, $event)"
              />
            </div>
          </UCard>
          <UCard variant="outline">
            <template #header>
              <div class="flex items-center justify-between">
                <h2 class="font-semibold">
                  Samples
                </h2><div class="flex items-center gap-3">
                  <UInput
                    v-model="sampleSearch"
                    icon="i-lucide-search"
                    placeholder="Search samples…"
                    class="w-56"
                  /><span class="text-xs text-muted">{{ filteredSamples.length }} samples</span>
                </div>
              </div>
            </template><div class="overflow-x-auto">
              <AppTable
                table-id="evaluation-samples"
                :columns="sampleColumns"
                :data="pagedSampleRows"
                class="text-sm"
              >
                <template #fields-cell="{ row }">
                  <dl class="flex flex-wrap gap-x-4 gap-y-1">
                    <template v-for="(value, key) in row.original.fields" :key="key">
                      <div>
                        <dt class="inline text-muted">
                          {{ key }}:
                        </dt> <dd class="inline">
                          {{ printValue(value) }}
                        </dd>
                      </div>
                    </template>
                  </dl>
                </template>
              </AppTable>
            </div>
            <div v-if="filteredSamples.length > samplePageSize" class="flex items-center justify-between border-t border-default px-3 py-2">
              <span class="text-xs text-muted">Showing {{ Math.min((samplePage - 1) * samplePageSize + 1, filteredSamples.length) }}–{{ Math.min(samplePage * samplePageSize, filteredSamples.length) }}</span>
              <UPagination
                v-model:page="samplePage"
                :total="filteredSamples.length"
                :items-per-page="samplePageSize"
              />
            </div>
          </UCard>
          <UCard v-if="comparison" variant="subtle">
            <template #header>
              <h2 class="font-semibold">
                Baseline data
              </h2>
            </template>
            <div class="grid gap-4 md:grid-cols-2">
              <div>
                <p class="text-xs font-medium uppercase text-muted">
                  Current · {{ comparison.currentReport.samples.length }} samples
                </p>
                <ul class="mt-2 space-y-1 text-sm">
                  <li v-for="table in comparison.currentReport.tables" :key="`current-${table.key}`">
                    {{ table.title }} · {{ table.totalRows.toLocaleString() }} rows
                  </li>
                </ul>
              </div>
              <div>
                <p class="text-xs font-medium uppercase text-muted">
                  Baseline · {{ comparison.baselineReport.samples.length }} samples
                </p>
                <ul class="mt-2 space-y-1 text-sm">
                  <li v-for="table in comparison.baselineReport.tables" :key="`baseline-${table.key}`">
                    {{ table.title }} · {{ table.totalRows.toLocaleString() }} rows
                  </li>
                </ul>
              </div>
            </div>
          </UCard>
          <UCard v-if="report.metadata && Object.keys(report.metadata).length" variant="subtle">
            <details>
              <summary class="cursor-pointer text-sm font-medium">
                Provenance and metadata
              </summary><pre class="mt-3 overflow-auto text-xs text-muted">{{ JSON.stringify(report.metadata, null, 2) }}</pre>
            </details>
          </UCard>
        </template>
      </div>
    </template>
  </UDashboardPanel>
</template>
