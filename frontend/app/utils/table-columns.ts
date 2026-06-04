import type { VisibilityState } from '@tanstack/vue-table'

export type NormalizedTableColumn = {
  id: string
  label: string
  canHide: boolean
}

type TableColumnLike = {
  id?: unknown
  accessorKey?: unknown
  cell?: unknown
  header?: unknown
  columns?: unknown
  enableHiding?: unknown
  meta?: unknown
}

export const FIXED_VISIBLE_COLUMN_IDS = new Set(['select', 'actions'])

export function isTableColumnLike(value: unknown): value is TableColumnLike {
  return !!value && typeof value === 'object'
}

export function getTableColumnId(column: TableColumnLike): string {
  if (typeof column.id === 'string') return column.id
  if (typeof column.accessorKey === 'string') return column.accessorKey
  return ''
}

function humanizeColumnLabel(id: string): string {
  if (id === 'select') return 'Select'
  if (id === 'actions') return 'Actions'

  return id
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, char => char.toUpperCase())
}

export function normalizeTableColumns(columns: unknown[] | undefined): NormalizedTableColumn[] {
  if (!columns?.length) return []

  const normalized: NormalizedTableColumn[] = []
  const seen = new Set<string>()

  const walkColumns = (input: unknown[]) => {
    for (const value of input) {
      if (!isTableColumnLike(value)) continue

      if (Array.isArray(value.columns) && value.columns.length > 0) {
        walkColumns(value.columns)
        continue
      }

      const id = getTableColumnId(value)
      if (!id || seen.has(id)) continue
      seen.add(id)

      const meta = value.meta && typeof value.meta === 'object'
        ? value.meta as { label?: unknown }
        : null
      let label = humanizeColumnLabel(id)
      if (typeof value.header === 'string' && value.header.trim().length > 0) {
        label = value.header.trim()
      }
      if (typeof meta?.label === 'string' && meta.label.trim().length > 0) {
        label = meta.label.trim()
      }

      normalized.push({
        id,
        label,
        canHide: !FIXED_VISIBLE_COLUMN_IDS.has(id) && value.enableHiding !== false
      })
    }
  }

  walkColumns(columns)
  return normalized
}

export function filterVisibleTableColumns(
  columns: unknown[] | undefined,
  columnVisibility: VisibilityState
): unknown[] | undefined {
  if (!columns?.length) return columns

  return columns.flatMap((value) => {
    if (!isTableColumnLike(value)) return [value]

    if (Array.isArray(value.columns) && value.columns.length > 0) {
      const visibleChildColumns = filterVisibleTableColumns(value.columns, columnVisibility)
      if (!visibleChildColumns?.length) return []

      return [{
        ...value,
        columns: visibleChildColumns
      }]
    }

    const id = getTableColumnId(value)
    if (!id || FIXED_VISIBLE_COLUMN_IDS.has(id) || columnVisibility[id] !== false) {
      return [value]
    }

    return []
  })
}
