import { computed, onBeforeUnmount, onMounted, ref, watch, type ComputedRef } from 'vue'

type EditorSidebarStateOptions = {
  openedProjectIds: ComputedRef<string[]>
  currentProjectId: ComputedRef<string | null>
  isLeftCollapsed: ComputedRef<boolean>
  isRightCollapsed: ComputedRef<boolean>
  expandLeftSidebar: () => void
  expandRightSidebar: () => void
}

export function useEditorSidebarState(options: EditorSidebarStateOptions) {
  const editorFilterPopoverOpen = ref(false)
  const projectAccordionPanels = ref<string[]>([])
  const collapsedProjectPanels = ref<string[]>([])
  const accordionPanels = ref<string[]>(['structure'])

  const openedProjectIdsSignature = computed(() => options.openedProjectIds.value.join('|'))

  watch(openedProjectIdsSignature, () => {
    const ids = options.openedProjectIds.value
    projectAccordionPanels.value = projectAccordionPanels.value.filter(id => ids.includes(id))
    if (projectAccordionPanels.value.length === 0 && ids.length > 0) {
      projectAccordionPanels.value = [...ids]
    }

    collapsedProjectPanels.value = collapsedProjectPanels.value.filter(id => ids.includes(id))
    if (collapsedProjectPanels.value.length === 0 && ids.length > 0) {
      const preferredProjectId = options.currentProjectId.value && ids.includes(options.currentProjectId.value)
        ? options.currentProjectId.value
        : ids[0]
      collapsedProjectPanels.value = preferredProjectId ? [preferredProjectId] : []
    }
  }, { immediate: true })

  function isCollapsedProjectOpen(projectId: string): boolean {
    return collapsedProjectPanels.value.includes(projectId)
  }

  function toggleCollapsedProjectPanel(projectId: string) {
    if (isCollapsedProjectOpen(projectId)) {
      collapsedProjectPanels.value = collapsedProjectPanels.value.filter(id => id !== projectId)
      return
    }

    collapsedProjectPanels.value = [...collapsedProjectPanels.value, projectId]
  }

  const handleExpandLayoutPanels = () => {
    accordionPanels.value = ['structure', 'reading-order', 'metadata', 'tasks', 'settings']
  }

  const handlePrepareRightSidebarForOnboarding = () => {
    if (options.isRightCollapsed.value) {
      options.expandRightSidebar()
    }
  }

  const handleOpenEditorFilterPopover = () => {
    if (options.isLeftCollapsed.value) {
      options.expandLeftSidebar()
    }
    editorFilterPopoverOpen.value = true
  }

  const handleCloseEditorFilterPopover = () => {
    editorFilterPopoverOpen.value = false
  }

  onMounted(() => {
    window.addEventListener('larex:onboarding:expand-layout-panels', handleExpandLayoutPanels)
    window.addEventListener('larex:onboarding:prepare-editor-right-sidebar', handlePrepareRightSidebarForOnboarding)
    window.addEventListener('larex:onboarding:open-editor-filter-popover', handleOpenEditorFilterPopover)
    window.addEventListener('larex:onboarding:close-editor-filter-popover', handleCloseEditorFilterPopover)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('larex:onboarding:expand-layout-panels', handleExpandLayoutPanels)
    window.removeEventListener('larex:onboarding:prepare-editor-right-sidebar', handlePrepareRightSidebarForOnboarding)
    window.removeEventListener('larex:onboarding:open-editor-filter-popover', handleOpenEditorFilterPopover)
    window.removeEventListener('larex:onboarding:close-editor-filter-popover', handleCloseEditorFilterPopover)
  })

  return {
    editorFilterPopoverOpen,
    projectAccordionPanels,
    collapsedProjectPanels,
    accordionPanels,
    isCollapsedProjectOpen,
    toggleCollapsedProjectPanel
  }
}
