import type { AddPanelOptions, IDockviewPanel } from 'dockview-vue'

type DockviewPanelParams = Record<string, unknown>

export type ProjectDockviewApi = {
  getPanel: (panelId: string) => IDockviewPanel | undefined
  addPanel: (options: AddPanelOptions<DockviewPanelParams>) => IDockviewPanel
}

const projectDockviewApis = shallowRef<Record<string, ProjectDockviewApi>>({})

const waiters = new Map<string, Array<(api: ProjectDockviewApi) => void>>()

export function useProjectDockviewRegistry() {
  function register(projectId: string, api: ProjectDockviewApi) {
    projectDockviewApis.value = {
      ...projectDockviewApis.value,
      [projectId]: api
    }

    const callbacks = waiters.get(projectId) ?? []
    for (const callback of callbacks) callback(api)
    waiters.delete(projectId)
  }

  function unregister(projectId: string) {
    const { [projectId]: _removed, ...rest } = projectDockviewApis.value
    projectDockviewApis.value = rest
    waiters.delete(projectId)
  }

  function get(projectId: string): ProjectDockviewApi | null {
    return projectDockviewApis.value[projectId] ?? null
  }

  async function waitFor(projectId: string, timeoutMs = 2500): Promise<ProjectDockviewApi | null> {
    const existing = get(projectId)
    if (existing) return existing

    return await new Promise((resolve) => {
      const timer = setTimeout(() => {
        const callbacks = waiters.get(projectId) ?? []
        waiters.set(projectId, callbacks.filter(cb => cb !== onReady))
        resolve(null)
      }, timeoutMs)

      const onReady = (api: ProjectDockviewApi) => {
        clearTimeout(timer)
        resolve(api)
      }

      const callbacks = waiters.get(projectId) ?? []
      callbacks.push(onReady)
      waiters.set(projectId, callbacks)
    })
  }

  return {
    apis: readonly(projectDockviewApis),
    register,
    unregister,
    get,
    waitFor
  }
}
