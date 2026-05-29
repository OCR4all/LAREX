<script setup lang="ts">
interface Props {
  collapsed?: boolean
}

interface StarredProject {
  projectId: string
  projectName: string
}

defineProps<Props>()

const workspace = useWorkspaceStore()
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId)

const starredKey = computed(() => {
  if (!selectedWorkspace.value) return globalKey('pending', 'projects', 'starred')
  return wsKey(selectedWorkspace.value, 'projects', 'starred')
})

const { data: starredProjects, refresh: refreshStarredProjects } = await useFetch<StarredProject[]>(() =>
  `/api/stars/workspace/${selectedWorkspace.value as string}`,
{
  key: starredKey,
  watch: [selectedWorkspace],
  default: () => [],
  immediate: false
})

watch(selectedWorkspace, (workspaceId) => {
  if (workspaceId) {
    void refreshStarredProjects()
  } else {
    starredProjects.value = []
  }
}, { immediate: true })

const displayedProjects = computed(() => {
  if (!starredProjects.value) return []
  return starredProjects.value.slice(0, 5)
})

const hasMoreProjects = computed(() => {
  return starredProjects.value && starredProjects.value.length > 5
})

const isPopoverOpen = ref(false)

function showAllStarred() {
  navigateTo('/?starred=true')
  isPopoverOpen.value = false
}

function navigateToProject(projectId: string) {
  navigateTo(`/project/${projectId}`)
  isPopoverOpen.value = false
}
</script>

<template>
  <div v-if="starredProjects && starredProjects.length > 0">
    <div v-if="collapsed">
      <UPopover v-model:open="isPopoverOpen" :content="{ side: 'right' }">
        <UButton
          icon="i-lucide-star"
          color="neutral"
          variant="ghost"
          size="sm"
          class="w-full justify-center"
        />

        <template #content>
          <div class="p-4 w-64">
            <div class="flex items-center justify-between mb-3">
              <h3 class="text-sm font-semibold">
                Favorites
              </h3>
              <UButton
                icon="i-lucide-x"
                color="neutral"
                variant="ghost"
                size="xs"
                @click="isPopoverOpen = false"
              />
            </div>

            <div class="space-y-1 max-h-80 overflow-y-auto">
              <button
                v-for="project in starredProjects"
                :key="project.projectId"
                class="flex items-center gap-3 px-2 py-2 rounded-sm text-sm text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors w-full text-left"
                @click="navigateToProject(project.projectId)"
              >
                <UAvatar
                  :text="project.projectName.substring(0, 2).toUpperCase()"
                  size="xs"
                  class="ring-1 ring-neutral-200 dark:ring-neutral-700 flex-shrink-0"
                />
                <span class="flex-1 min-w-0 truncate font-medium">{{ project.projectName }}</span>
              </button>
            </div>

            <div class="mt-3 pt-3 border-t border-neutral-200 dark:border-neutral-700">
              <button
                class="flex items-center gap-2 px-2 py-1 rounded-sm text-sm text-neutral-500 dark:text-neutral-400 hover:bg-neutral-100 dark:hover:bg-neutral-800 hover:text-neutral-700 dark:hover:text-neutral-300 transition-colors w-full text-left"
                @click="showAllStarred"
              >
                <UIcon name="i-lucide-external-link" class="w-4 h-4 shrink-0" />
                <span class="font-medium">View all in Projects</span>
              </button>
            </div>
          </div>
        </template>
      </UPopover>
    </div>

    <div v-else>
      <div class="px-2 mb-3">
        <h3 class="text-sm font-medium">
          Favorites
        </h3>
      </div>

      <div>
        <NuxtLink
          v-for="project in displayedProjects"
          :key="project.projectId"
          :to="`/project/${project.projectId}`"
          class="flex items-center gap-3 px-2 py-2 rounded-sm text-xs transition-colors group hover:bg-neutral-100 dark:hover:bg-neutral-800"
          active-class="bg-neutral-100 dark:bg-neutral-800 text-primary-900 dark:text-primary-100"
        >
          <div class="shrink-0">
            <UAvatar
              :text="project.projectName.substring(0, 2).toUpperCase()"
              size="xs"
              class="ring-1 ring-neutral-200 dark:ring-neutral-700"
            />
          </div>

          <div class="flex-1 min-w-0">
            <p class="truncate font-medium">{{ project.projectName }}</p>
          </div>
        </NuxtLink>

        <button
          v-if="hasMoreProjects"
          class="flex items-center py-2 px-3 gap-x-4 rounded-sm text-xs transition-colors w-full"
          @click="showAllStarred"
        >
          <UIcon name="i-lucide-more-horizontal" class="flex-shrink-0 w-4 h-4" />
          <span class="flex-1 text-left font-medium">More</span>
        </button>
      </div>
    </div>
  </div>
</template>
