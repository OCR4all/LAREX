<script setup lang="ts">
import type { ReviewQueue } from '~/types'
import type { ReviewQueueFilters } from '@/stores/editor/editor.session.store'

const props = defineProps<{
  queue: ReviewQueue | null
}>()

const filters = defineModel<ReviewQueueFilters>('filters', { required: true })

const ALL = 'all'

function uniqueOptions(allLabel: string, entries: Array<{ value: string, label: string }>) {
  const unique = new Map(entries.map(entry => [entry.value, entry]))
  return [{ value: ALL, label: allLabel }, ...unique.values()]
}

function filterModel(key: keyof ReviewQueueFilters) {
  return computed({
    get: () => filters.value[key] ?? ALL,
    set: (value: string) => {
      filters.value = { ...filters.value, [key]: value === ALL ? undefined : value }
    }
  })
}

const projectFilter = filterModel('projectId')
const assignmentFilter = filterModel('taskId')
const submitterFilter = filterModel('submittedBy')

const queuePages = computed(() => props.queue?.pages ?? [])
const projectOptions = computed(() => uniqueOptions('All projects', queuePages.value.map(page => ({
  value: page.projectId,
  label: page.projectName
}))))
const assignmentOptions = computed(() => uniqueOptions('All Assignments', queuePages.value.flatMap(page =>
  (page.assignments ?? []).map(assignment => ({ value: assignment.id, label: assignment.title }))
)))
const submitterOptions = computed(() => uniqueOptions('All editors', queuePages.value.flatMap(page =>
  page.submittedByUserId ? [{ value: page.submittedByUserId, label: reviewSubmitterName(page) }] : []
)))

const hasActiveFilters = computed(() => Boolean(filters.value.projectId || filters.value.taskId || filters.value.submittedBy))
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
    <div class="flex items-center gap-2">
      <USelectMenu
        v-model="projectFilter"
        :items="projectOptions"
        value-key="value"
        icon="i-lucide-folder"
        class="w-52"
      />
      <USelectMenu
        v-model="assignmentFilter"
        :items="assignmentOptions"
        value-key="value"
        icon="i-lucide-clipboard-check"
        class="w-52"
      />
      <USelectMenu
        v-model="submitterFilter"
        :items="submitterOptions"
        value-key="value"
        icon="i-lucide-user"
        class="w-52"
      />
    </div>
    <AppTableClearFiltersButton
      :active="hasActiveFilters"
      @clear="filters = {}"
    />
  </div>
</template>
