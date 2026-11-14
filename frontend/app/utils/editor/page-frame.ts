import { PolygonType } from '@/models/editor/types'
import type { Border, Page, PrintSpace } from '@/models/editor/page'

export type PageFrameKind = typeof PolygonType.BORDER | typeof PolygonType.PRINT_SPACE

export const PAGE_FRAME_KINDS: readonly PageFrameKind[] = [PolygonType.BORDER, PolygonType.PRINT_SPACE]

export const BORDER_POLYGON_ID = 'page:border'
export const PRINT_SPACE_POLYGON_ID = 'page:printSpace'

const ID_BY_KIND: Record<PageFrameKind, string> = {
  [PolygonType.BORDER]: BORDER_POLYGON_ID,
  [PolygonType.PRINT_SPACE]: PRINT_SPACE_POLYGON_ID
}

export const PAGE_FRAME_LABELS: Record<PageFrameKind, string> = {
  [PolygonType.BORDER]: 'Border',
  [PolygonType.PRINT_SPACE]: 'Print space'
}

export function isPageFrameType(type: string | undefined | null): type is PageFrameKind {
  return type === PolygonType.BORDER || type === PolygonType.PRINT_SPACE
}

export function pageFrameIdForKind(kind: PageFrameKind): string {
  return ID_BY_KIND[kind]
}

export function pageFrameKindForId(id: string | undefined | null): PageFrameKind | null {
  if (id === BORDER_POLYGON_ID) return PolygonType.BORDER
  if (id === PRINT_SPACE_POLYGON_ID) return PolygonType.PRINT_SPACE
  return null
}

export function isPageFrameId(id: string | undefined | null): boolean {
  return pageFrameKindForId(id) !== null
}

export function getPageFrame(page: Page, kind: PageFrameKind): Border | PrintSpace | undefined {
  return kind === PolygonType.BORDER ? page.border : page.printSpace
}

export function setPageFrame(page: Page, kind: PageFrameKind, frame: Border | PrintSpace | undefined): void {
  if (kind === PolygonType.BORDER) {
    page.border = frame
  } else {
    page.printSpace = frame
  }
}
