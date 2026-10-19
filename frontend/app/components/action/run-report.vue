<script setup lang="ts">
import type { ActionRunReport, ActionPageOutcome } from '@/types/action'
import { actionRunCountsLabel } from '@/utils/action-run-status'

const props = defineProps<{ report: ActionRunReport, runId: string }>()
const search = ref('')
const outcome = ref('ALL')
const page = ref(1)
const pageSize = 25
const outcomeOptions = [
  { label: 'All outcomes', value: 'ALL' },
  ...['SUCCEEDED', 'FAILED', 'SKIPPED', 'UNFINISHED', 'UNKNOWN'].map(value => ({
    label: value.toLowerCase().replace(/^./, character => character.toUpperCase()), value
  }))
]
const filteredPages = computed(() => {
  const query = search.value.trim().toLowerCase()
  return props.report.pageOutcomes.filter(item => (outcome.value === 'ALL' || item.status === outcome.value)
    && (!query || [item.pageName, item.pageId, item.message, ...item.warnings].some(value => value?.toLowerCase().includes(query))))
})
const { rows: visiblePages, infiniteScroll } = useTablePagination(filteredPages, page, pageSize)
watch([search, outcome, () => props.report], () => {
  page.value = 1
})

function outcomeColor(item: ActionPageOutcome) {
  if (item.status === 'FAILED') return 'error'
  if (item.warnings.length) return 'warning'
  if (item.status === 'SUCCEEDED') return 'success'
  return 'neutral'
}

function download() {
  const url = URL.createObjectURL(new Blob([JSON.stringify(props.report, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `action-run-${props.runId}-report.json`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<template>
  <section class="space-y-3" aria-label="Run report">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h3 class="text-sm font-medium">
          Run report
        </h3>
        <p v-if="report.counts.total" class="text-xs text-muted">
          {{ actionRunCountsLabel(report.counts) }} · {{ report.counts.total }} total
        </p>
      </div>
      <UButton
        icon="i-lucide-download"
        color="neutral"
        variant="ghost"
        size="sm"
        @click="download"
      >
        Download JSON
      </UButton>
    </div>
    <UAlert
      v-for="(issue, index) in report.issues"
      :key="index"
      :color="issue.severity === 'ERROR' ? 'error' : 'warning'"
      :description="issue.message"
      icon="i-lucide-triangle-alert"
    />
    <template v-if="report.pageOutcomes.length">
      <div class="flex flex-wrap gap-2">
        <UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Search pages or reasons"
          aria-label="Search run report"
          class="min-w-48 flex-1"
        />
        <USelect
          v-model="outcome"
          :items="outcomeOptions"
          aria-label="Filter page outcomes"
          class="w-44"
        />
      </div>
      <div class="max-h-96 overflow-auto rounded-sm border border-default">
        <table class="w-full text-left text-xs">
          <thead class="sticky top-0 bg-elevated">
            <tr>
              <th class="p-2 font-medium">
                Page
              </th>
              <th class="p-2 font-medium">
                Outcome
              </th>
              <th class="p-2 font-medium">
                Details
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-default">
            <tr v-for="item in visiblePages" :key="item.pageId" class="align-top">
              <td class="p-2">
                {{ item.pageName || item.pageId }}
              </td>
              <td class="p-2">
                <UBadge :color="outcomeColor(item)" variant="soft" size="sm">
                  {{ item.status.toLowerCase() }}
                </UBadge>
              </td>
              <td class="max-w-lg whitespace-pre-wrap break-words p-2">
                <p v-if="item.message">
                  {{ item.message }}
                </p>
                <p v-for="(warning, index) in item.warnings" :key="index" class="text-warning">
                  {{ warning }}
                </p>
                <span v-if="!item.message && !item.warnings.length" class="text-muted">—</span>
              </td>
            </tr>
            <tr v-if="!visiblePages.length">
              <td colspan="3" class="p-4 text-center text-muted">
                No matching pages.
              </td>
            </tr>
            <tr v-if="infiniteScroll.enabled">
              <td colspan="3">
                <AppTableLoadMore :state="infiniteScroll" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <UPagination
        v-if="filteredPages.length > pageSize"
        v-model:page="page"
        class="hidden sm:flex"
        :total="filteredPages.length"
        :items-per-page="pageSize"
        size="sm"
      />
    </template>
    <p v-else-if="!report.issues.length" class="text-xs text-muted">
      No run issues reported.
    </p>
  </section>
</template>
