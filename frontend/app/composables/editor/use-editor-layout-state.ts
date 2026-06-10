import type { ComputedRef } from 'vue'

type ToolbarLayout = 'docked-top' | 'docked-bottom' | 'docked-left' | 'docked-right' | 'floating'

export function useEditorLayoutState(toolbarLayout: ComputedRef<ToolbarLayout>) {
  const rootLayoutClass = computed(() => {
    switch (toolbarLayout.value) {
      case 'docked-top':
        return 'grid grid-rows-[auto_1fr_auto] grid-cols-1 h-full'

      case 'docked-bottom':
        return 'grid grid-rows-[1fr_auto_auto] grid-cols-1 h-full'

      case 'docked-left':
        return 'grid grid-cols-[auto_1fr] grid-rows-[1fr_auto] h-full'

      case 'docked-right':
        return 'grid grid-cols-[1fr_auto] grid-rows-[1fr_auto] h-full'

      case 'floating':
      default:
        return 'grid grid-rows-[1fr_auto] grid-cols-1 h-full'
    }
  })

  const toolbarClass = computed(() => {
    switch (toolbarLayout.value) {
      case 'floating':
        return 'z-50'

      case 'docked-top':
        return 'row-start-1 col-span-full'

      case 'docked-bottom':
        return 'row-start-2 col-span-full'

      case 'docked-left':
        return 'col-start-1 row-span-full'

      case 'docked-right':
        return 'col-start-2 row-span-full'

      default:
        return 'row-start-1 col-span-full'
    }
  })

  const contentClass = computed(() => {
    switch (toolbarLayout.value) {
      case 'docked-top':
        return 'row-start-2 col-span-full'
      case 'docked-bottom':
        return 'row-start-1 col-span-full'
      case 'docked-left':
        return 'row-start-1 col-start-2'
      case 'docked-right':
        return 'row-start-1 col-start-1'
      case 'floating':
      default:
        return 'row-start-1 col-span-full'
    }
  })

  const statusBarClass = computed(() => {
    switch (toolbarLayout.value) {
      case 'docked-top':
        return 'row-start-3 col-span-full'
      case 'docked-bottom':
        return 'row-start-3 col-span-full'
      case 'docked-left':
        return 'row-start-2 col-start-2'
      case 'docked-right':
        return 'row-start-2 col-start-1'
      case 'floating':
      default:
        return 'row-start-2 col-span-full'
    }
  })

  return {
    rootLayoutClass,
    toolbarClass,
    contentClass,
    statusBarClass
  }
}
