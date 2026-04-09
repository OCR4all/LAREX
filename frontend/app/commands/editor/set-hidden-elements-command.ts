import type { Command } from './types'
import { usePageVisibilityState, type PageVisibilityState } from '@/session/editor/editor-session'

export type HiddenAction = 'hide' | 'show'

export interface SetHiddenElementsCommandData {
  pageId: string
  action: HiddenAction
  polygonIds?: string[]
  polylineIds?: string[]
}

function unique(ids: string[] | undefined): string[] {
  if (!ids?.length) return []
  return Array.from(new Set(ids.filter(Boolean)))
}

function removeAll(source: string[], toRemove: Set<string>): string[] {
  if (!source.length || !toRemove.size) return source
  return source.filter(id => !toRemove.has(id))
}

export class SetHiddenElementsCommand implements Command {
  private readonly pageId: string
  private readonly action: HiddenAction
  private readonly polygonIds: string[]
  private readonly polylineIds: string[]

  private before: PageVisibilityState | null = null
  private after: PageVisibilityState | null = null

  constructor(data: SetHiddenElementsCommandData) {
    this.pageId = data.pageId
    this.action = data.action
    this.polygonIds = unique(data.polygonIds)
    this.polylineIds = unique(data.polylineIds)
  }

  execute(): void {
    const stateRef = usePageVisibilityState(this.pageId)

    if (!this.before) {
      const current = stateRef.value ?? { hiddenPolygonIds: [], hiddenPolylineIds: [] }
      this.before = {
        hiddenPolygonIds: [...(current.hiddenPolygonIds ?? [])],
        hiddenPolylineIds: [...(current.hiddenPolylineIds ?? [])]
      }

      const polygonSet = new Set(this.before.hiddenPolygonIds)
      const polylineSet = new Set(this.before.hiddenPolylineIds)

      if (this.action === 'hide') {
        for (const id of this.polygonIds) polygonSet.add(id)
        for (const id of this.polylineIds) polylineSet.add(id)
      } else {
        const toRemovePolygons = new Set(this.polygonIds)
        const toRemovePolylines = new Set(this.polylineIds)
        this.after = {
          hiddenPolygonIds: removeAll(Array.from(polygonSet), toRemovePolygons),
          hiddenPolylineIds: removeAll(Array.from(polylineSet), toRemovePolylines)
        }
      }

      if (this.action === 'hide') {
        this.after = {
          hiddenPolygonIds: Array.from(polygonSet),
          hiddenPolylineIds: Array.from(polylineSet)
        }
      }
    }

    stateRef.value = { ...(this.after ?? { hiddenPolygonIds: [], hiddenPolylineIds: [] }) }
  }

  undo(): void {
    if (!this.before) return
    const stateRef = usePageVisibilityState(this.pageId)
    stateRef.value = { ...this.before }
  }

  getDescription(): string {
    const count = this.polygonIds.length + this.polylineIds.length
    const actionLabel = this.action === 'hide' ? 'Hide' : 'Show'
    return `${actionLabel} ${count} selected element${count === 1 ? '' : 's'}`
  }
}
