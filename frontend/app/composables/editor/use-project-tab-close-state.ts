const autoClosedProjectIds = shallowRef<Set<string>>(new Set())
const explicitClosedProjectIds = shallowRef<Set<string>>(new Set())
const pageReplacementProjectIds = shallowRef<Set<string>>(new Set())

export function useProjectTabCloseState() {
  function markAutoClosed(projectId: string) {
    autoClosedProjectIds.value.add(projectId)
  }

  function consumeAutoClosed(projectId: string): boolean {
    const has = autoClosedProjectIds.value.has(projectId)
    if (has) autoClosedProjectIds.value.delete(projectId)
    return has
  }

  function markExplicitClose(projectId: string) {
    explicitClosedProjectIds.value.add(projectId)
  }

  function consumeExplicitClose(projectId: string): boolean {
    const has = explicitClosedProjectIds.value.has(projectId)
    if (has) explicitClosedProjectIds.value.delete(projectId)
    return has
  }

  function isExplicitClose(projectId: string): boolean {
    return explicitClosedProjectIds.value.has(projectId)
  }

  function beginPageReplacement(projectId: string) {
    pageReplacementProjectIds.value.add(projectId)
  }

  function endPageReplacement(projectId: string) {
    pageReplacementProjectIds.value.delete(projectId)
  }

  function isPageReplacementActive(projectId: string): boolean {
    return pageReplacementProjectIds.value.has(projectId)
  }

  return {
    markAutoClosed,
    consumeAutoClosed,
    markExplicitClose,
    consumeExplicitClose,
    isExplicitClose,
    beginPageReplacement,
    endPageReplacement,
    isPageReplacementActive
  }
}
