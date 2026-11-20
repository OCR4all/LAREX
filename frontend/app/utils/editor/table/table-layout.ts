import type { TableCell, TableModel } from './table-model'

export type TableLayoutSlot
  = | { kind: 'cell', cell: TableCell }
    | { kind: 'empty', row: number, col: number }

export function tableLayoutRows(model: TableModel): TableLayoutSlot[][] {
  const owner = new Map<string, TableCell>()
  for (const cell of model.cells) {
    for (let row = cell.row; row < cell.row + cell.rowSpan; row++) {
      for (let col = cell.col; col < cell.col + cell.colSpan; col++) owner.set(`${row}:${col}`, cell)
    }
  }
  const rows: TableLayoutSlot[][] = []
  for (let row = 0; row < model.rows; row++) {
    const slots: TableLayoutSlot[] = []
    for (let col = 0; col < model.columns; col++) {
      const cell = owner.get(`${row}:${col}`)
      if (!cell) slots.push({ kind: 'empty', row, col })
      else if (cell.row === row && cell.col === col) slots.push({ kind: 'cell', cell })
    }
    rows.push(slots)
  }
  return rows
}

export function cellsInRowOrder(model: TableModel): TableCell[] {
  return [...model.cells].sort((a, b) => a.row - b.row || a.col - b.col)
}

export type TableDirection = 'up' | 'down' | 'left' | 'right'

export function neighbourCell(model: TableModel, cellId: string, direction: TableDirection): TableCell | undefined {
  const cell = model.cells.find(candidate => candidate.regionId === cellId)
  if (!cell) return undefined
  const [row, col] = {
    up: [cell.row - 1, cell.col],
    down: [cell.row + cell.rowSpan, cell.col],
    left: [cell.row, cell.col - 1],
    right: [cell.row, cell.col + cell.colSpan]
  }[direction]
  return model.cells.find(candidate => candidate.row <= row! && row! < candidate.row + candidate.rowSpan
    && candidate.col <= col! && col! < candidate.col + candidate.colSpan)
}
