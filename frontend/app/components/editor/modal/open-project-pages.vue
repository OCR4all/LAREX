<script setup lang="ts">
type WorkspaceProject = {
  id: string
  name: string
  pageCount?: number
}

type ProjectPage = {
  id: string
  name: string
}

export type OpenProjectPagesSelection = Array<{
  projectId: string
  projectName: string
  pageIds: string[] | null
}>

const props = defineProps<{
  workspaceId: string
}>()

const emit = defineEmits<{
  close: [result: OpenProjectPagesSelection | null]
}>()

const toast = useToast()
const isOpen = ref(true)

const { data: projects, pending: isProjectsLoading } = await useFetch<WorkspaceProject[]>(
  () => `/api/workspaces/${props.workspaceId}/projects`,
  { default: () => [] }
)

const pagesByProjectId = ref<Record<string, ProjectPage[]>>({})
const pagesLoadingByProjectId = ref<Record<string, boolean>>({})
const expandedProjectIds = ref<string[]>([])

const selectionByProjectId = ref<Record<string, { projectName: string, pageIds: string[] | null }>>({})

async function ensurePagesLoaded(projectId: string) {
  if (pagesByProjectId.value[projectId]) return
  if (pagesLoadingByProjectId.value[projectId]) return

  pagesLoadingByProjectId.value = {
    ...pagesLoadingByProjectId.value,
    [projectId]: true
  }
  try {
    const pages = await $fetch<ProjectPage[]>(`/api/projects/${projectId}/pages`)
    pagesByProjectId.value = {
      ...pagesByProjectId.value,
      [projectId]: pages
    }
  } catch (err: any) {
    toast.add({
      title: 'Failed to load project pages',
      description: err?.data?.message ?? err?.message,
      color: 'error'
    })
    pagesByProjectId.value = {
      ...pagesByProjectId.value,
      [projectId]: []
    }
  } finally {
    pagesLoadingByProjectId.value = {
      ...pagesLoadingByProjectId.value,
      [projectId]: false
    }
  }
}

function setProjectSelected(project: WorkspaceProject, selected: boolean) {
  if (selected) {
    selectionByProjectId.value = {
      ...selectionByProjectId.value,
      [project.id]: {
        projectName: project.name,
        pageIds: null
      }
    }
    return
  }

  const { [project.id]: _removed, ...rest } = selectionByProjectId.value
  selectionByProjectId.value = rest
}

function isProjectSelected(projectId: string): boolean {
  return Boolean(selectionByProjectId.value[projectId])
}

async function setPageSelected(project: WorkspaceProject, pageId: string, selected: boolean) {
  await ensurePagesLoaded(project.id)

  const pages = pagesByProjectId.value[project.id] ?? []
  const allPageIds = pages.map(p => p.id)
  const existing = selectionByProjectId.value[project.id]

  if (!existing && selected) {
    selectionByProjectId.value = {
      ...selectionByProjectId.value,
      [project.id]: {
        projectName: project.name,
        pageIds: [pageId]
      }
    }
    return
  }

  if (!existing) return

  if (existing.pageIds === null) {
    if (selected) return
    const next = allPageIds.filter(id => id !== pageId)
    if (next.length === 0) {
      const { [project.id]: _removed, ...rest } = selectionByProjectId.value
      selectionByProjectId.value = rest
      return
    }
    selectionByProjectId.value = {
      ...selectionByProjectId.value,
      [project.id]: {
        projectName: project.name,
        pageIds: next
      }
    }
    return
  }

  const nextSet = new Set(existing.pageIds)
  if (selected) nextSet.add(pageId)
  else nextSet.delete(pageId)
  const next = Array.from(nextSet)

  if (next.length === 0) {
    const { [project.id]: _removed, ...rest } = selectionByProjectId.value
    selectionByProjectId.value = rest
    return
  }

  selectionByProjectId.value = {
    ...selectionByProjectId.value,
    [project.id]: {
      projectName: project.name,
      pageIds: next.length === allPageIds.length ? null : next
    }
  }
}

function isPageSelected(projectId: string, pageId: string): boolean {
  const selected = selectionByProjectId.value[projectId]
  if (!selected) return false
  if (selected.pageIds === null) return true
  return selected.pageIds.includes(pageId)
}

async function toggleProjectExpanded(projectId: string) {
  if (!expandedProjectIds.value.includes(projectId)) {
    expandedProjectIds.value = [...expandedProjectIds.value, projectId]
    await ensurePagesLoaded(projectId)
    return
  }
  expandedProjectIds.value = expandedProjectIds.value.filter(id => id !== projectId)
}

const selectedCount = computed(() => Object.keys(selectionByProjectId.value).length)

async function submit() {
  const result: OpenProjectPagesSelection = []
  for (const [projectId, state] of Object.entries(selectionByProjectId.value)) {
    if (state.pageIds !== null) {
      await ensurePagesLoaded(projectId)
      const allPageIds = (pagesByProjectId.value[projectId] ?? []).map(p => p.id)
      if (state.pageIds.length === allPageIds.length) {
        result.push({
          projectId,
          projectName: state.projectName,
          pageIds: null
        })
        continue
      }
    }

    result.push({
      projectId,
      projectName: state.projectName,
      pageIds: state.pageIds ? [...state.pageIds] : null
    })
  }

  emit('close', result)
  isOpen.value = false
}

function close() {
  emit('close', null)
  isOpen.value = false
}
</script>

<template>
  <UModal v-model:open="isOpen" :title="'Open Projects & Pages'" :ui="{ content: 'max-w-3xl' }" @close="close">
    <template #body>
      <div class="space-y-3 max-h-[70vh] overflow-auto">
        <div class="text-xs text-muted">
          Select one or more projects. Projects default to all pages, and you can narrow to specific pages.
        </div>

        <div v-if="isProjectsLoading" class="text-sm text-muted py-4">
          Loading projects...
        </div>

        <div v-else class="space-y-2">
          <div
            v-for="project in projects"
            :key="project.id"
            class="rounded-sm border border-default p-2"
          >
            <div class="flex items-center gap-2">
              <UCheckbox
                :model-value="isProjectSelected(project.id)"
                @update:model-value="(value) => setProjectSelected(project, !!value)"
              />
              <UButton
                color="neutral"
                variant="ghost"
                size="xs"
                :icon="expandedProjectIds.includes(project.id) ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
                @click="toggleProjectExpanded(project.id)"
              />
              <div class="flex-1 min-w-0">
                <div class="font-medium truncate">{{ project.name }}</div>
                <div class="text-xs text-muted">{{ project.pageCount ?? 0 }} pages</div>
              </div>
            </div>

            <div v-if="expandedProjectIds.includes(project.id)" class="mt-2 pl-7 space-y-1">
              <div v-if="pagesLoadingByProjectId[project.id]" class="text-xs text-muted py-1">
                Loading pages...
              </div>
              <div
                v-for="page in (pagesByProjectId[project.id] ?? [])"
                :key="page.id"
                class="flex items-center gap-2"
              >
                <UCheckbox
                  :model-value="isPageSelected(project.id, page.id)"
                  @update:model-value="(value) => setPageSelected(project, page.id, !!value)"
                />
                <span class="text-sm truncate">{{ page.name }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-between items-center w-full">
        <div class="text-xs text-muted">
          {{ selectedCount }} project{{ selectedCount === 1 ? '' : 's' }} selected
        </div>
        <div class="flex items-center gap-2">
          <UButton color="neutral" variant="ghost" @click="close">
            Cancel
          </UButton>
          <UButton color="primary" :disabled="selectedCount === 0" @click="submit">
            Open in Editor
          </UButton>
        </div>
      </div>
    </template>
  </UModal>
</template>

