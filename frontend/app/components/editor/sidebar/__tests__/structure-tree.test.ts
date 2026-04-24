import { describe, expect, it } from 'vitest'
import {
  buildChildrenByParentId,
  flattenStructureRows,
  type TreeItemData
} from '@/components/editor/sidebar/structure-tree'

describe('structure-tree', () => {
  it('builds and sorts children by parent ID', () => {
    const polygons: TreeItemData[] = [
      { id: 'region-root', type: 'REGION' },
      { id: 'line-b', type: 'TEXTLINE', parentId: 'region-root', label: 'b-line' },
      { id: 'line-a', type: 'TEXTLINE', parentId: 'region-root', label: 'a-line' },
      { id: 'region-child', type: 'REGION', parentId: 'region-root', label: 'child-region' }
    ]
    const polylines: TreeItemData[] = [
      { id: 'baseline-line-a', type: 'BASELINE', parentId: 'line-a' }
    ]

    const childrenByParentId = buildChildrenByParentId(polygons, polylines)

    expect((childrenByParentId.get('region-root') ?? []).map(item => item.id)).toEqual([
      'region-child',
      'line-a',
      'line-b'
    ])
    expect((childrenByParentId.get('line-a') ?? []).map(item => item.id)).toEqual([
      'baseline-line-a'
    ])
  })

  it('flattens only expanded branches', () => {
    const polygons: TreeItemData[] = [
      { id: 'region-root', type: 'REGION' },
      { id: 'line-a', type: 'TEXTLINE', parentId: 'region-root', label: 'a-line' }
    ]
    const polylines: TreeItemData[] = [
      { id: 'baseline-line-a', type: 'BASELINE', parentId: 'line-a' }
    ]
    const roots = [polygons[0]!]
    const childrenByParentId = buildChildrenByParentId(polygons, polylines)

    const collapsedRows = flattenStructureRows(roots, childrenByParentId, new Set())
    expect(collapsedRows.map(row => row.item.id)).toEqual(['region-root'])

    const rootExpandedRows = flattenStructureRows(roots, childrenByParentId, new Set(['region-root']))
    expect(rootExpandedRows.map(row => row.item.id)).toEqual(['region-root', 'line-a'])

    const fullExpandedRows = flattenStructureRows(roots, childrenByParentId, new Set(['region-root', 'line-a']))
    expect(fullExpandedRows.map(row => row.item.id)).toEqual(['region-root', 'line-a', 'baseline-line-a'])
    expect(fullExpandedRows.map(row => row.level)).toEqual([0, 1, 2])
  })

  it('guards against cyclic references', () => {
    const nodeA: TreeItemData = { id: 'a', type: 'REGION', parentId: 'b' }
    const nodeB: TreeItemData = { id: 'b', type: 'REGION', parentId: 'a' }
    const roots = [nodeA]
    const childrenByParentId = buildChildrenByParentId([nodeA, nodeB], [])

    const rows = flattenStructureRows(roots, childrenByParentId, new Set(['a', 'b']))
    expect(rows.map(row => row.item.id)).toEqual(['a', 'b'])
  })
})
