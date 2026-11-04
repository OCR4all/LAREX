<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { ReviewQueue, ReviewQueuePage } from '~/types'
import type { PageWorkflowAction } from '@/types/project-page'
import type { ReviewQueueFilters } from '@/stores/editor/editor.session.store'
import { filterReviewPages } from '@/utils/page-workflow-state'

const props = defineProps<{
  queue: ReviewQueue | null
  loading: boolean
}>()

const filters = defineModel<ReviewQueueFilters>('filters', { required: true })

const emit = defineEmits<{ updated: [] }>()

const UBadge = resolveComponent('UBadge')
const UCheckbox = resolveComponent('UCheckbox')

const toast = useToast()
const pageWorkflow = usePageWorkflow()

const ALL = 'all'
const selectedPageIds = ref<Set<string>>(new Set())
const isApplying = ref(false)

function displayName(page: ReviewQueuePage) {
  const submitter = page.submittedBy
  if (submitter?.firstName && submitter.lastName) return `${submitter.firstName} ${submitter.lastName}`
  return submitter?.username ?? 'Unknown'
}

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
  page.submittedByUserId ? [{ value: page.submittedByUserId, label: displayName(page) }] : []
)))

const filteredPages = computed(() => filterReviewPages(queuePages.value, filters.value))
const selectedPages = computed(() => filteredPages.value.filter(page => selectedPageIds.value.has(page.pageId) && !page.blocked))
const allSelected = computed(() => {
  const selectable = filteredPages.value.filter(page => !page.blocked)
  return selectable.length > 0 && selectable.every(page => selectedPageIds.value.has(page.pageId))
})
const hiddenPageCount = computed(() => (props.queue?.totalPageCount ?? 0) - queuePages.value.length)

function setSelected(pageId: string, selected: boolean) {
  const next = new Set(selectedPageIds.value)
  if (selected) next.add(pageId)
  else next.delete(pageId)
  selectedPageIds.value = next
}

function toggleAll(selected: boolean) {
  selectedPageIds.value = selected
    ? new Set(filteredPages.value.filter(page => !page.blocked).map(page => page.pageId))
    : new Set()
}

const columns = computed<TableColumn<ReviewQueuePage>[]>(() => [
  {
    id: 'select',
    header: () => h(UCheckbox, {
      'modelValue': allSelected.value,
      'aria-label': 'Select all pages',
      'onUpdate:modelValue': (value: boolean | 'indeterminate') => toggleAll(value === true)
    }),
    cell: ({ row }) => h(UCheckbox, {
      'modelValue': selectedPageIds.value.has(row.original.pageId),
      'disabled': row.original.blocked,
      'aria-label': `Select ${row.original.pageName}`,
      'onUpdate:modelValue': (value: boolean | 'indeterminate') => setSelected(row.original.pageId, value === true)
    })
  },
  { accessorKey: 'pageName', header: 'Page' },
  { accessorKey: 'projectName', header: 'Project' },
  {
    id: 'assignments',
    header: 'Assignment',
    cell: ({ row }) => row.original.assignments?.map(assignment => assignment.title).join(', ') || '—'
  },
  { id: 'submittedBy', header: 'Submitted by', cell: ({ row }) => displayName(row.original) },
  {
    id: 'submittedAt',
    header: 'Submitted',
    cell: ({ row }) => row.original.submittedAt ? new Date(row.original.submittedAt).toLocaleString() : '—'
  },
  {
    id: 'status',
    header: '',
    cell: ({ row }) => row.original.blocked
      ? h(UBadge, { color: 'warning', variant: 'subtle', size: 'sm', title: row.original.blockedReason ?? undefined }, () => row.original.blockedReason ?? 'Blocked')
      : null
  }
])

async function applyToSelection(action: Extract<PageWorkflowAction, 'APPROVE' | 'REQUEST_CHANGES'>) {
  const pages = selectedPages.value
  if (pages.length === 0) return
  let comment: string | undefined
  if (action === 'REQUEST_CHANGES') {
    const result = await pageWorkflow.promptForChanges(pages.length)
    if (!result) return
    comment = result.comment
  }

  const pageIdsByProject = new Map<string, string[]>()
  for (const page of pages) {
    pageIdsByProject.set(page.projectId, [...(pageIdsByProject.get(page.projectId) ?? []), page.pageId])
  }

  isApplying.value = true
  try {
    for (const [projectId, pageIds] of pageIdsByProject) {
      await transitionPages(projectId, pageIds, action, comment)
    }
    toggleAll(false)
  } catch (cause) {
    toast.add({
      title: 'Failed to update the selected pages',
      description: extractApiErrorMessage(cause, 'Some pages may not have been updated.'),
      color: 'error'
    })
  } finally {
    isApplying.value = false
    emit('updated')
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center gap-2">
      <USelectMenu
        v-model="projectFilter"
        :items="projectOptions"
        value-key="value"
        class="w-52"
      />
      <USelectMenu
        v-model="assignmentFilter"
        :items="assignmentOptions"
        value-key="value"
        class="w-52"
      />
      <USelectMenu
        v-model="submitterFilter"
        :items="submitterOptions"
        value-key="value"
        class="w-52"
      />

      <div v-if="selectedPages.length" class="ml-auto flex items-center gap-2">
        <span class="text-sm text-muted">{{ selectedPages.length }} selected</span>
        <UButton
          color="success"
          variant="soft"
          icon="i-lucide-circle-check"
          label="Approve"
          :loading="isApplying"
          @click="applyToSelection('APPROVE')"
        />
        <UButton
          color="warning"
          variant="soft"
          icon="i-lucide-message-square-warning"
          label="Request changes"
          :disabled="isApplying"
          @click="applyToSelection('REQUEST_CHANGES')"
        />
      </div>
    </div>

    <UEmpty
      v-if="!loading && filteredPages.length === 0"
      variant="naked"
      icon="i-lucide-eye"
      title="No pages to review"
      description="Pages appear here when editors submit them or their Assignment finishes."
    />

    <UTable
      v-else
      :data="filteredPages"
      :columns="columns"
      :loading="loading"
      :get-row-id="(page: ReviewQueuePage) => page.pageId"
    />

    <p v-if="hiddenPageCount > 0" class="text-sm text-muted">
      Showing the first {{ queuePages.length }} of {{ queue?.totalPageCount }} pages.
    </p>
  </div>
</template>
