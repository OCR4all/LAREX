<script setup lang="ts">
import { useVirtualizer } from '@tanstack/vue-virtual'
import type { Subtask, UserProfile } from '~/types/index'

const props = defineProps<{
  taskId: string
  pages: { pageId: string, pageName: string, projectId?: string, projectName?: string }[]
  taskAssignees?: UserProfile[]
  taskDescription?: string | null
  onConverted?: (created: Subtask[]) => void | Promise<void>
}>()

const emit = defineEmits<{
  close: [result: boolean]
}>()

const toast = useToast()
const isConverting = ref(false)
const selectedAssigneeId = ref<string>('__none__')
const useTaskDescription = ref(!!props.taskDescription)
const bulkDescription = ref('')

const assigneeOptions = computed(() => {
  const options = [{ label: 'No assignee', value: '__none__' }]
  if (props.taskAssignees) {
    for (const user of props.taskAssignees) {
      const displayName = user.firstName && user.lastName
        ? `${user.firstName} ${user.lastName}`
        : user.username
      options.push({ label: displayName, value: user.id })
    }
  }
  return options
})

const effectiveAssigneeId = computed(() => {
  return selectedAssigneeId.value === '__none__' ? null : selectedAssigneeId.value
})

const effectiveDescription = computed(() => {
  if (useTaskDescription.value && props.taskDescription) {
    return props.taskDescription
  }
  const value = bulkDescription.value.trim()
  return value.length > 0 ? value : null
})

type PreviewRow = { kind: 'project', projectName: string } | { kind: 'page', page: typeof props.pages[0] }

const pagesByProject = computed(() => {
  const groups = new Map<string, { projectName: string, pages: typeof props.pages }>()

  for (const page of props.pages) {
    const projectId = page.projectId || 'unknown'
    const projectName = page.projectName || 'Unknown Project'

    if (!groups.has(projectId)) {
      groups.set(projectId, { projectName, pages: [] })
    }
    groups.get(projectId)!.pages.push(page)
  }

  return Array.from(groups.values())
    .sort((a, b) => a.projectName.localeCompare(b.projectName))
    .map(group => ({
      ...group,
      pages: group.pages.slice().sort((a, b) => a.pageName.localeCompare(b.pageName))
    }))
})

const hasMultipleProjects = computed(() => pagesByProject.value.length > 1)

const previewScrollerRef = ref<HTMLElement | null>(null)
const previewRows = computed<PreviewRow[]>(() => pagesByProject.value.flatMap(group => [
  ...(hasMultipleProjects.value ? [{ kind: 'project' as const, projectName: group.projectName }] : []),
  ...group.pages.map(page => ({ kind: 'page' as const, page }))
]))
const previewVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: previewRows.value.length,
  getScrollElement: () => previewScrollerRef.value,
  estimateSize: () => 28,
  overscan: 4,
  getItemKey: index => previewRows.value[index]?.kind === 'project'
    ? `project-${previewRows.value[index]?.projectName}`
    : previewRows.value[index]?.page.pageId ?? index
})))
const virtualPreviewRows = computed(() => previewVirtualizer.value.getVirtualItems().flatMap((item) => {
  const row = previewRows.value[item.index]
  return row ? [{ item, row }] : []
}))
const totalPreviewSize = computed(() => previewVirtualizer.value.getTotalSize())

function getSubtaskTitle(page: typeof props.pages[0]) {
  if (page.projectName) {
    return `[${page.projectName}] ${page.pageName}`
  }
  return page.pageName
}

async function convertToSubtasks() {
  if (props.pages.length === 0) {
    emit('close', false)
    return
  }

  isConverting.value = true
  let created: Subtask[] = []
  try {
    created = await $fetch<Subtask[]>(`/api/tasks/${props.taskId}/subtasks/with-pages`, {
      method: 'POST',
      body: props.pages.map(page => ({
        title: getSubtaskTitle(page),
        pageId: page.pageId,
        assignedUserId: effectiveAssigneeId.value,
        description: effectiveDescription.value
      }))
    })
  } catch {
    toast.add({
      title: `Failed to create subtasks`,
      color: 'error'
    })
  }

  if (created.length > 0) {
    toast.add({
      title: `Created ${created.length} subtask${created.length !== 1 ? 's' : ''}`,
      color: 'success'
    })
    await props.onConverted?.(created)
  }

  isConverting.value = false
  emit('close', created.length > 0)
}

function skip() {
  emit('close', false)
}
</script>

<template>
  <UModal
    title="Create Subtasks from Pages?"
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #body>
      <div class="space-y-4">
        <p class="text-sm text-muted">
          You just linked {{ pages.length }} page{{ pages.length !== 1 ? 's' : '' }} to this task.
          Would you like to create subtasks for each page to track progress?
        </p>

        <div
          ref="previewScrollerRef"
          class="p-3 bg-elevated/50 border border-default rounded-sm max-h-48 overflow-auto"
        >
          <p class="text-xs text-muted mb-2 font-medium">
            Subtasks to create:
          </p>
          <div class="relative" :style="{ height: `${totalPreviewSize}px` }">
            <div
              v-for="{ item, row } in virtualPreviewRows"
              :key="String(item.key)"
              :data-index="item.index"
              class="absolute left-0 top-0 w-full"
              :style="{ transform: `translateY(${item.start}px)` }"
            >
              <p v-if="row.kind === 'project'" class="text-xs font-medium text-muted mb-1 flex items-center gap-1">
                <UIcon name="i-lucide-folder" class="size-3" />
                {{ row.projectName }}
              </p>
              <div v-else class="text-sm flex items-center gap-2" :class="{ 'pl-4': hasMultipleProjects }">
                <UIcon name="i-lucide-file" class="size-4 text-muted shrink-0" />
                <span class="truncate">{{ getSubtaskTitle(row.page) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="taskAssignees && taskAssignees.length > 0" class="space-y-2">
          <label class="text-sm font-medium">Assign all subtasks to</label>
          <USelectMenu
            v-model="selectedAssigneeId"
            :items="assigneeOptions"
            value-key="value"
            placeholder="Select assignee (optional)"
            class="w-full"
          />
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium">Subtask description</label>
          <UCheckbox
            v-if="taskDescription"
            v-model="useTaskDescription"
            label="Use task description"
          />
          <UTextarea
            v-model="bulkDescription"
            :disabled="useTaskDescription"
            placeholder="Optional description applied to all subtasks"
            :rows="3"
          />
        </div>

        <div class="flex items-start gap-2 p-3 bg-info/10 border border-info/20 rounded-sm">
          <UIcon name="i-lucide-info" class="size-5 text-info shrink-0 mt-0.5" />
          <p class="text-sm text-muted">
            Each page will become a subtask linked to that page.
            You can mark them as complete as you work through each page.
          </p>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2 w-full">
        <UButton
          color="neutral"
          variant="outline"
          :disabled="isConverting"
          @click="skip"
        >
          Skip
        </UButton>
        <UButton
          :loading="isConverting"
          :disabled="isConverting"
          @click="convertToSubtasks"
        >
          Create {{ pages.length }} Subtask{{ pages.length !== 1 ? 's' : '' }}
        </UButton>
      </div>
    </template>
  </UModal>
</template>
