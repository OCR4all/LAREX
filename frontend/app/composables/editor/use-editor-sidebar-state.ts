import { computed, onBeforeUnmount, onMounted, ref, watch, type ComputedRef } from 'vue'

type EditorSidebarStateOptions = {
  openedProjectIds: ComputedRef<string[]>
  isLeftCollapsed: ComputedRef<boolean>
  isRightCollapsed: ComputedRef<boolean>
  expandLeftSidebar: () => void
  expandRightSidebar: () => void
}

export function useEditorSidebarState(options: EditorSidebarStateOptions) {
  const editorFilterPopoverOpen = ref(false)
  const projectAccordionPanels = ref<string[]>([])
  const accordionPanels = ref<string[]>(['structure'])

  const openedProjectIdsSignature = computed(() => options.openedProjectIds.value.join('|'))

  watch(openedProjectIdsSignature, () => {
    const ids = options.openedProjectIds.value
    projectAccordionPanels.value = projectAccordionPanels.value.filter(id => ids.includes(id))
    if (projectAccordionPanels.value.length === 0 && ids.length > 0) {
      projectAccordionPanels.value = [...ids]
    }
  }, { immediate: true })

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
    accordionPanels
  }
}
