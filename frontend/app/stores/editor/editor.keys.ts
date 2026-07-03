const PROJECT_PANEL_PREFIX = 'project'
const PAGE_PANEL_PREFIX = 'page'
const CANVAS_PREFIX = 'editor'
const COMPARE_CANVAS_PREFIX = 'compare-editor'

type ParsedCompositeKey = {
  projectId: string
  pageId: string
}

function splitCompositeKey(value: string, expectedPrefix: string): ParsedCompositeKey | null {
  const prefix = `${expectedPrefix}:`
  if (!value.startsWith(prefix)) return null

  const raw = value.slice(prefix.length)
  const idx = raw.indexOf(':')
  if (idx === -1) return null

  const projectId = raw.slice(0, idx)
  const pageId = raw.slice(idx + 1)
  if (!projectId || !pageId) return null

  return { projectId, pageId }
}

export function getProjectPanelId(projectId: string): string {
  return `${PROJECT_PANEL_PREFIX}:${projectId}`
}

export function getPagePanelId(projectId: string, pageId: string): string {
  return `${PAGE_PANEL_PREFIX}:${projectId}:${pageId}`
}

export function getCanvasId(projectId: string, pageId: string): string {
  return `${CANVAS_PREFIX}:${projectId}:${pageId}`
}

export function getCompareCanvasId(projectId: string, pageId: string, comparisonId: string, side: 'current' | 'version'): string {
  return `${COMPARE_CANVAS_PREFIX}:${projectId}:${pageId}:${comparisonId}:${side}`
}

export function parseProjectPanelId(panelId: string): string | null {
  const prefix = `${PROJECT_PANEL_PREFIX}:`
  if (!panelId.startsWith(prefix)) return null
  return panelId.slice(prefix.length) || null
}

export function parsePagePanelId(panelId: string): ParsedCompositeKey | null {
  return splitCompositeKey(panelId, PAGE_PANEL_PREFIX)
}

export function parseCanvasId(canvasId: string): ParsedCompositeKey | null {
  return splitCompositeKey(canvasId, CANVAS_PREFIX)
}
