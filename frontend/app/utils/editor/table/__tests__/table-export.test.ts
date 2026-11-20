import { describe, expect, it } from 'vitest'
import type { TableModel } from '../table-model'
import { tableToCsv, tableToHtml } from '../table-export'

const model: TableModel = {
  tableId: 't',
  rows: 2,
  columns: 2,
  cells: [
    { regionId: 'h', row: 0, col: 0, rowSpan: 1, colSpan: 2, header: true },
    { regionId: 'a', row: 1, col: 0, rowSpan: 1, colSpan: 1, header: false }
  ]
}
const texts = new Map([['h', 'Name, "full"'], ['a', 'first\nsecond <b>']])

describe('tableToCsv', () => {
  it('puts each text at its cell origin and quotes where needed', () => {
    expect(tableToCsv(model, texts)).toBe('"Name, ""full""",\r\n"first\nsecond <b>",\r\n')
  })
})

describe('tableToHtml', () => {
  it('writes header cells, spans, escaped text and line breaks', () => {
    const html = tableToHtml(model, texts, 'Table <t>')
    expect(html).toContain('<title>Table &lt;t&gt;</title>')
    expect(html).toContain('<tr><th colspan="2">Name, &quot;full&quot;</th></tr>')
    expect(html).toContain('<tr><td>first<br>second &lt;b&gt;</td><td></td></tr>')
  })
})
