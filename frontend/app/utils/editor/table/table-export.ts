import type { TableModel } from './table-model'
import { tableLayoutRows } from './table-layout'

export type TableExportFormat = 'csv' | 'html'

function csvField(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value
}

export function tableToCsv(model: TableModel, texts: Map<string, string>): string {
  const grid = Array.from({ length: model.rows }, () => Array.from({ length: model.columns }, () => ''))
  for (const cell of model.cells) {
    if (grid[cell.row]?.[cell.col] !== undefined) grid[cell.row]![cell.col] = texts.get(cell.regionId) ?? ''
  }
  return grid.map(row => row.map(csvField).join(',')).join('\r\n') + '\r\n'
}

function escapeHtml(value: string): string {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;')
}

export function tableToHtml(model: TableModel, texts: Map<string, string>, title: string): string {
  const body = tableLayoutRows(model).map((slots) => {
    const cells = slots.map((slot) => {
      if (slot.kind === 'empty') return '<td></td>'
      const { cell } = slot
      const tag = cell.header ? 'th' : 'td'
      const spans = `${cell.rowSpan > 1 ? ` rowspan="${cell.rowSpan}"` : ''}${cell.colSpan > 1 ? ` colspan="${cell.colSpan}"` : ''}`
      const content = escapeHtml(texts.get(cell.regionId) ?? '').split('\n').join('<br>')
      return `<${tag}${spans}>${content}</${tag}>`
    })
    return `      <tr>${cells.join('')}</tr>`
  })
  return [
    '<!doctype html>',
    '<html>',
    '<head>',
    '  <meta charset="utf-8">',
    `  <title>${escapeHtml(title)}</title>`,
    '  <style>table{border-collapse:collapse}td,th{border:1px solid #888;padding:4px 8px;vertical-align:top;text-align:left}</style>',
    '</head>',
    '<body>',
    '  <table>',
    '    <tbody>',
    ...body,
    '    </tbody>',
    '  </table>',
    '</body>',
    '</html>',
    ''
  ].join('\n')
}
