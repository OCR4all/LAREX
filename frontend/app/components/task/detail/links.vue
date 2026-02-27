<script setup lang="ts">
import type { TaskLinks, TaskPageLink } from '~/types/index'
import { LazyTaskSlideoverLinkItems, LazyTaskModalConvertToSubtasks } from '#components'

const props = defineProps<{
  taskId: string
  workspaceId: string
  links: TaskLinks
  taskDescription?: string | null
}>()

const emit = defineEmits<{
  refresh: []
  refreshSubtasks: []
}>()

const toast = useToast()
const overlay = useOverlay()

const linkItemsSlideover = overlay.create(LazyTaskSlideoverLinkItems)
const convertToSubtasksModal = overlay.create(LazyTaskModalConvertToSubtasks)

const linkedPageIds = computed(() => props.links.pageLinks.map(l => l.pageId))

const pagesByProject = computed(() => {
  const groups = new Map<string, { projectId: string; projectName: string; pages: TaskPageLink[] }>()

  for (const link of props.links.pageLinks) {
    const projectId = link.projectId || 'unknown'
    const projectName = link.projectName || 'Unknown Project'

    if (!groups.has(projectId)) {
      groups.set(projectId, { projectId, projectName, pages: [] })
    }
    groups.get(projectId)!.pages.push(link)
  }

  return Array.from(groups.values())
    .sort((a, b) => a.projectName.localeCompare(b.projectName))
    .map(group => ({
      ...group,
      pages: group.pages.slice().sort((a, b) => a.pageName.localeCompare(b.pageName))
    }))
})

const openProjects = ref<string[]>([])

watch(pagesByProject, (groups) => {
  openProjects.value = groups.map(g => g.projectId)
}, { immediate: true })

async function openLinkItemsSlideover() {
  const instance = linkItemsSlideover.open({
    taskId: props.taskId,
    workspaceId: props.workspaceId,
    linkedPageIds: linkedPageIds.value,
    onLinked: async (linkedPages: { pageId: string; pageName: string; projectId: string; projectName: string }[]) => {
      emit('refresh')

      if (linkedPages.length > 0) {
        const convertInstance = convertToSubtasksModal.open({
          taskId: props.taskId,
          pages: linkedPages.map(p => ({ pageId: p.pageId, pageName: p.pageName })),
          taskDescription: props.taskDescription,
          onConverted: () => emit('refreshSubtasks')
        })
        await convertInstance.result
      }
    }
  })
  await instance.result
}

async function unlinkPage(pageId: string) {
  try {
    await $fetch(`/api/tasks/${props.taskId}/links/pages/${pageId}`, {
      method: 'DELETE'
    })
    emit('refresh')
    toast.add({ title: 'Page unlinked', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to unlink page', description: err?.data?.message, color: 'error' })
  }
}

async function unlinkAllPagesFromProject(projectId: string) {
  const group = pagesByProject.value.find(g => g.projectId === projectId)
  if (!group) return

  try {
    await Promise.all(
      group.pages.map(page =>
        $fetch(`/api/tasks/${props.taskId}/links/pages/${page.pageId}`, {
          method: 'DELETE'
        })
      )
    )
    emit('refresh')
    toast.add({ title: `Unlinked ${group.pages.length} pages from ${group.projectName}`, color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to unlink pages', description: err?.data?.message, color: 'error' })
  }
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <UButton
        icon="i-lucide-link"
        color="neutral"
        variant="outline"
        @click="openLinkItemsSlideover"
      >
        Link Pages
      </UButton>
    </div>

    <div v-if="pagesByProject.length > 0" class="space-y-3">
      <h4 class="text-sm font-semibold flex items-center gap-2">
        <UIcon name="i-lucide-files" class="size-4" />
        Linked Pages
        <UBadge size="xs" color="neutral" variant="subtle">{{ links.pageLinks.length }}</UBadge>
      </h4>

      <UAccordion
        v-model="openProjects"
        type="multiple"
        :items="pagesByProject.map(group => ({
          value: group.projectId,
          label: group.projectName,
          icon: 'i-lucide-folder',
          content: group
        }))"
        :ui="{
          item: 'border border-default rounded-sm mb-2 last:mb-0 overflow-hidden'
        }"
      >
        <template #leading="{ item }">
          <div class="flex items-center gap-2">
            <UIcon name="i-lucide-folder" class="size-4 text-muted" />
          </div>
        </template>

        <template #default="{ item }">
          <div class="flex items-center justify-between flex-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">{{ item.label }}</span>
              <UBadge size="xs" color="neutral" variant="subtle">
                {{ item.content.pages.length }} page{{ item.content.pages.length !== 1 ? 's' : '' }}
              </UBadge>
            </div>
            <UButton
              icon="i-lucide-unlink"
              size="xs"
              color="neutral"
              variant="ghost"
              title="Unlink all pages from this project"
              @click.stop="unlinkAllPagesFromProject(item.content.projectId)"
            />
          </div>
        </template>

        <template #body="{ item }">
          <div class="space-y-1 p-2 bg-elevated/30">
            <div
              v-for="page in item.content.pages"
              :key="page.id"
              class="flex items-center justify-between gap-2 p-2 rounded-sm hover:bg-default transition-colors"
            >
              <NuxtLink
                :to="`/project/${page.projectId}`"
                class="flex items-center gap-2 min-w-0 hover:text-primary transition-colors flex-1"
              >
                <UIcon name="i-lucide-file" class="size-4 text-muted shrink-0" />
                <span class="text-sm truncate">{{ page.pageName }}</span>
              </NuxtLink>
              <div class="flex items-center gap-1 shrink-0">
                <UBadge v-if="page.linkType === 'BY_TAG'" size="xs" color="info" variant="subtle">
                  {{ page.tagFilter }}
                </UBadge>
                <UButton
                  icon="i-lucide-x"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  title="Unlink page"
                  @click="unlinkPage(page.pageId)"
                />
              </div>
            </div>
          </div>
        </template>
      </UAccordion>
    </div>

    <div v-else class="text-center py-8">
      <div class="p-4 bg-elevated/30 rounded-full w-fit mx-auto mb-4">
        <UIcon name="i-lucide-link" class="size-8 text-muted" />
      </div>
      <p class="text-muted font-medium">No linked pages</p>
      <p class="text-sm text-muted mt-1">Link pages from projects to associate them with this task</p>
    </div>
  </div>
</template>
