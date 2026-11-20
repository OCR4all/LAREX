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
