import type { ComputedRef, Ref } from 'vue'
import type { Page, ProjectActionScope, ProjectData } from '@/types/project-page'

type ActionRunPageOption = {
  id: string
  name: string
  imageCount: number
  xmlFileCount: number
  imageVariants: NonNullable<Page['imageVariants']>
}

type ActionRunSlideover = {
  open: (props: {
    workspaceId: string
    projectId: string
    projectName: string
    pageIds: string[]
    pages: ActionRunPageOption[]
  }) => { result: Promise<unknown> }
}

type ProjectActionsOptions = {
  projectId: string
  selectedWorkspace: Ref<string | null | undefined>
  project: Ref<ProjectData | null | undefined>
  pagesSafe: ComputedRef<Page[]>
  actionRunSlideover: unknown
  getScopedPageIds: (scope: ProjectActionScope) => string[]
  refreshProjectPagesData: () => Promise<void>
  refreshProjectPageData: (pageId: string) => Promise<void>
}

export function useProjectActions(options: ProjectActionsOptions) {
  const actionRunsStore = useActionRunsStore()

  async function openActionRunSlideover(scope: ProjectActionScope = 'all') {
    if (!options.project.value || !options.selectedWorkspace.value) return

    const actionRunSlideover = options.actionRunSlideover as ActionRunSlideover
    const instance = actionRunSlideover.open({
      workspaceId: options.selectedWorkspace.value,
      projectId: options.project.value.id,
      projectName: options.project.value.name,
      pageIds: options.getScopedPageIds(scope),
      pages: options.pagesSafe.value.map(page => ({
        id: page.id,
        name: page.name,
        imageCount: page.imageCount ?? 0,
        xmlFileCount: page.xmlFileCount ?? 0,
        imageVariants: page.imageVariants ?? []
      }))
    })
    const changed = await instance.result
    if (changed) {
      await options.refreshProjectPagesData()
    }
  }

  const activeActionRunCount = computed(() => actionRunsStore.runsArray.filter(run =>
    run.projectId === options.projectId && ['QUEUED', 'PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS'].includes(run.status)
  ).length)

  let lastHandledPageResultSequence = 0
  watch(() => actionRunsStore.pageResultEvents.at(-1)?.sequence, () => {
    for (const event of actionRunsStore.pageResultEvents) {
      if (event.sequence <= lastHandledPageResultSequence) continue
      lastHandledPageResultSequence = event.sequence
      if (event.projectId === options.projectId) {
        void options.refreshProjectPageData(event.pageId)
      }
    }
  }, { immediate: true })

  return {
    activeActionRunCount,
    openActionRunSlideover
  }
}
