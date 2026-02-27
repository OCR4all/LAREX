<script setup lang="ts">
import { globalKey, wsKey } from '@/utils/fetch-keys'

interface Props {
  collapsed?: boolean
}

defineProps<Props>()

const workspace = useWorkspaceStore()
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId)

const starredKey = computed(() => {
  if (!selectedWorkspace.value) return globalKey('pending', 'projects', 'starred')
  return wsKey(selectedWorkspace.value, 'projects', 'starred')
})

const { data: starredProjects } = useFetch<any[]>(() =>
  selectedWorkspace.value ? `/api/stars/workspace/${selectedWorkspace.value}` : null,
{
  key: starredKey,
  watch: [selectedWorkspace],
  default: () => []
}
)

const displayedProjects = computed(() => {
  if (!starredProjects.value) return []
  return starredProjects.value.slice(0, 5)
})

const hasMoreProjects = computed(() => {
  return starredProjects.value && starredProjects.value.length > 5
})

const isPopoverOpen = ref(false)

function showAllStarred() {
  navigateTo('/library?starred=true')
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
                class="flex items-center gap-3 px-2 py-2 rounded-sm text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors w-full text-left"
                @click="navigateToProject(project.projectId)"
              >
                <UAvatar
                  :text="project.projectName.substring(0, 2).toUpperCase()"
                  size="xs"
                  class="ring-1 ring-gray-200 dark:ring-gray-700 flex-shrink-0"
                />
                <span class="flex-1 min-w-0 truncate font-medium">{{ project.projectName }}</span>
              </button>
            </div>

            <div class="mt-3 pt-3 border-t border-gray-200 dark:border-gray-700">
              <button
                class="flex items-center gap-2 px-2 py-1 rounded-sm text-sm text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 hover:text-gray-700 dark:hover:text-gray-300 transition-colors w-full text-left"
                @click="showAllStarred"
              >
                <UIcon name="i-lucide-external-link" class="w-4 h-4 flex-shrink-0" />
                <span class="font-medium">View all in Library</span>
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
          class="flex items-center gap-3 px-2 py-2 rounded-sm text-xs transition-colors group"
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
