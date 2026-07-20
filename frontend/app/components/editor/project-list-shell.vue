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
  projects: SidebarProject[]
  projectAccordionPanels: string[]
  projectAccordionItems: Array<{ label: string, value: string, slot: string }>
  pageNameFilter: string
  onlyWithOpenSubtasks: boolean
  hasBackendFilters: boolean
  backendFilteredPageIdsByProjectId: Record<string, string[]>
  visible: boolean
  getProjectContextMenuItems: (projectId: string) => DropdownMenuItem[][]
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
        :visible="visible"
        @select-page="(pageId, variantId, projectId) => emit('select-page', pageId, variantId, projectId)"
        @unload-page="(pageId, projectId) => emit('unload-page', pageId, projectId)"
      />
    </template>
  </UAccordion>
</template>
