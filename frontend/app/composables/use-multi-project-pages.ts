import type { MaybeRefOrGetter } from 'vue'
import type { Page } from '@/types/project-page'

export type MultiProjectPage = Page & {
  projectId: string
  projectName: string
  searchText: string
}

export function useMultiProjectPages(
  projectIds: MaybeRefOrGetter<string[]>,
  getProjectName: (projectId: string) => string
) {
  const pagesByProject = shallowRef(new Map<string, Page[]>())
  const loadingProjectIds = ref(new Set<string>())
  const failedProjectIds = ref(new Set<string>())

  function withProject(set: Set<string>, projectId: string, present: boolean) {
    const next = new Set(set)
    if (present) next.add(projectId)
    else next.delete(projectId)
    return next
  }

  async function loadProject(projectId: string) {
    if (loadingProjectIds.value.has(projectId)) return
    loadingProjectIds.value = withProject(loadingProjectIds.value, projectId, true)
    failedProjectIds.value = withProject(failedProjectIds.value, projectId, false)
    try {
      const pages = await $fetch<Page[]>(`/api/projects/${projectId}/pages`)
      const next = new Map(pagesByProject.value)
      next.set(projectId, pages ?? [])
      pagesByProject.value = next
    } catch {
      failedProjectIds.value = withProject(failedProjectIds.value, projectId, true)
    } finally {
      loadingProjectIds.value = withProject(loadingProjectIds.value, projectId, false)
    }
  }

  watch(() => toValue(projectIds), (ids) => {
    for (const projectId of ids) {
      if (!pagesByProject.value.has(projectId) && !failedProjectIds.value.has(projectId)) {
        void loadProject(projectId)
      }
    }
  }, { immediate: true })

  const rows = computed<MultiProjectPage[]>(() => toValue(projectIds).flatMap((projectId) => {
    const projectName = getProjectName(projectId)
    return (pagesByProject.value.get(projectId) ?? []).map(page => ({
      ...page,
      description: getPageDisplayDescription(page),
      projectId,
      projectName,
      searchText: projectName
    }))
  }))

  const isLoading = computed(() => toValue(projectIds).some(projectId => loadingProjectIds.value.has(projectId)))
  const pages = computed(() => toValue(projectIds).some(projectId => pagesByProject.value.has(projectId)) ? rows.value : null)

  return {
    rows,
    pages,
    isLoading,
    loadingProjectIds,
    failedProjectIds,
    retry: loadProject
  }
}
