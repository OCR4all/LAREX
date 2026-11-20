import { describe, expect, it } from 'vitest'
import { Polygon } from '@/models/editor/geometry'
import type { Region, TextLine } from '@/models/editor'
import { linesCrossingCells } from '../table-checks'

function line(id: string, points: [number, number][]): TextLine {
  return { id, coords: new Polygon(points) } as TextLine
}

describe('linesCrossingCells', () => {
  it('lists lines that leave their cell, keeping lines on the border', () => {
    const table = {
      id: 't',
      kind: 'TableRegion',
      coords: new Polygon([[0, 10], [20, 10], [20, 0], [0, 0]]),
      regions: [{
        id: 'c',
        kind: 'TextRegion',
        roles: { tableCellRole: { rowIndex: 0, columnIndex: 0 } },
        coords: new Polygon([[0, 10], [10, 10], [10, 0], [0, 0]]),
        textLines: [
          line('inside', [[0, 8], [10, 8], [10, 6], [0, 6]]),
          line('across', [[2, 4], [14, 4], [14, 2], [2, 2]])
        ]
      }]
    } as unknown as Region
    expect(linesCrossingCells(table)).toEqual(new Map([['c', ['across']]]))
  })
})
