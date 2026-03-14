<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { PageData } from '@/stores/editor/types'

type SidebarProject = {
  id: string
  name: string
  pages: PageData[]
  openSubtaskCountByPage: Record<string, number>
}

const props = defineProps<{
  collapsed: boolean
  projects: SidebarProject[]
  projectAccordionPanels: string[]
  projectAccordionItems: Array<{ label: string, value: string, slot: string }>
  pageNameFilter: string
  onlyWithOpenSubtasks: boolean
  hasBackendFilters: boolean
  backendFilteredPageIdsByProjectId: Record<string, string[]>
  getProjectContextMenuItems: (projectId: string) => DropdownMenuItem[][]
  isCollapsedProjectOpen: (projectId: string) => boolean
  toggleCollapsedProjectPanel: (projectId: string) => void
}>()

const emit = defineEmits<{
  'update:projectAccordionPanels': [value: string[]]
  'select-page': [pageId: string, variantId?: string, projectId?: string]
  'unload-page': [pageId: string, projectId?: string]
}>()

const projectAccordionPanelsModel = computed({
  get: () => props.projectAccordionPanels,
  set: (value: string[]) => emit('update:projectAccordionPanels', value)
})
</script>

<template>
  <UAccordion
    v-if="!collapsed"
    v-model="projectAccordionPanelsModel"
    type="multiple"
    :items="projectAccordionItems"
  >
    <template #default="{ item }">
      <UContextMenu :items="getProjectContextMenuItems(item.value)">
        <span class="block w-full truncate">
          {{ item.label }}
        </span>
      </UContextMenu>
    </template>
    <template
      v-for="project in projects"
      #[`project-${project.id}`]
      :key="project.id"
    >
      <EditorSidebarImageList
        :project-id="project.id"
        :pages="project.pages"
        :filter="pageNameFilter"
        :only-with-open-subtasks="onlyWithOpenSubtasks"
        :open-subtask-count-by-page="project.openSubtaskCountByPage"
        :filtered-page-ids="hasBackendFilters ? (backendFilteredPageIdsByProjectId[project.id] ?? null) : null"
        @select-page="(pageId, variantId, projectId) => emit('select-page', pageId, variantId, projectId)"
        @unload-page="(pageId, projectId) => emit('unload-page', pageId, projectId)"
      />
    </template>
  </UAccordion>

  <div v-else class="space-y-2">
    <div
      v-for="project in projects"
      :key="project.id"
      class="space-y-2"
    >
      <UContextMenu :items="getProjectContextMenuItems(project.id)">
        <UTooltip
          :text="`${project.name} (${project.pages.length})`"
          :content="{ side: 'right' }"
        >
          <button
            type="button"
            class="w-full h-7 rounded-sm border border-default/70 hover:bg-accented/40 flex items-center justify-center transition-colors"
            :aria-label="`${isCollapsedProjectOpen(project.id) ? 'Collapse' : 'Expand'} ${project.name}`"
            @click="toggleCollapsedProjectPanel(project.id)"
          >
            <Icon
              :name="isCollapsedProjectOpen(project.id) ? 'i-lucide-chevron-down' : 'i-lucide-chevron-up'"
              class="h-4 w-4 text-muted"
            />
          </button>
        </UTooltip>
      </UContextMenu>

      <div v-if="isCollapsedProjectOpen(project.id)" class="space-y-2">
        <EditorSidebarImageList
          :project-id="project.id"
          :pages="project.pages"
          :filter="pageNameFilter"
          :only-with-open-subtasks="onlyWithOpenSubtasks"
          :open-subtask-count-by-page="project.openSubtaskCountByPage"
          :filtered-page-ids="hasBackendFilters ? (backendFilteredPageIdsByProjectId[project.id] ?? null) : null"
          @select-page="(pageId, variantId, projectId) => emit('select-page', pageId, variantId, projectId)"
          @unload-page="(pageId, projectId) => emit('unload-page', pageId, projectId)"
        />
      </div>
    </div>
  </div>
</template>
