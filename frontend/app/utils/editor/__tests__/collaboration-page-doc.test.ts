import { describe, expect, it, vi } from 'vitest'
import { Polygon } from '@/models/editor'
import type { Region, TextRegion } from '@/models/editor'
import { CreateTableGridCommand } from '@/commands/editor/create-table-grid-command'
import { TableStructureCommand } from '@/commands/editor/table-structure-command'
import { createMockSession, createTestContext, createTestDocument, findRegionById } from '@/commands/editor/__tests__/test-utils'
import { convertPageDtoToPcGts, convertPcGtsToPageDto } from '@/services/editor/page-conversion.service'
import { buildTableModel } from '@/utils/editor/table/table-model'
import { snapshotFromPageDto, snapshotToPageDto } from '../collaboration-page-doc'

vi.mock('@/stores/editor/editor.ui.store', () => ({ useEditorUiStore: () => ({ bumpReadingOrderVersion: vi.fn() }) }))

describe('collaboration page snapshots', () => {
  it('carry a restructured table to viewers unchanged', () => {
    const table: TextRegion = {
      id: 't',
      kind: 'TextRegion',
      coords: new Polygon([[0, 20], [30, 20], [30, 0], [0, 0]]),
      regions: [],
      textLines: [],
      textContentVariants: []
    }
    const mock = createMockSession(createTestDocument({ regions: [table] }))
    const ctx = createTestContext(mock.session)
    const { cellIds } = new CreateTableGridCommand({ regionId: 't', rows: 2, columns: 3, headerRows: 1 }).execute(ctx)!
    new TableStructureCommand({ tableId: 't', operation: { kind: 'merge-cells', cellIds: [cellIds[0]!, cellIds[1]!] } }).execute(ctx)
    new TableStructureCommand({ tableId: 't', operation: { kind: 'split-row', row: 1 } }).execute(ctx)

    const editorTable = findRegionById(mock.getDocument()!.page.regions, 't') as Region
    const wire = JSON.parse(JSON.stringify(snapshotFromPageDto(convertPcGtsToPageDto(mock.getDocument()!))))
    const viewer = convertPageDtoToPcGts(snapshotToPageDto(wire))
    const viewerTable = findRegionById(viewer.page.regions, 't') as Region

    expect(buildTableModel(viewerTable)).toEqual(buildTableModel(editorTable))
    expect(viewerTable.regions?.map(cell => cell.id)).toEqual(editorTable.regions?.map(cell => cell.id))
  })
})
