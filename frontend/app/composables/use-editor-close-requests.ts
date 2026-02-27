import { useEventBus } from '@vueuse/core'
import type { DockviewPanelApi } from 'dockview-core'

export type EditorCloseRequest = {
  panelApi: DockviewPanelApi
  projectId?: string | null
  pageId?: string | null
}

export function useEditorCloseRequests() {
  return useEventBus<EditorCloseRequest>('editor:close-request')
}
