import { describe, expect, it } from 'vitest'
import type { LabelSetCreateOrUpdateRequest, PageTextType } from '@/types/label-set'
import { buildLabelSetImportPreview } from '@/utils/label-set-import-preview'

const label = (id: string, name = id) => ({
  id,
  scope: 'region' as const,
  name,
  description: null,
  color: '#123456',
  hasText: true,
  isContainer: false,
  group: null,
  mapping: {
    pageXml: {
      regionType: 'TextRegion' as const,
      textType: ({ one: 'paragraph', two: 'heading', three: 'caption' }[id] ?? 'other') as PageTextType,
      customSubType: '',
      customKey: 'structure',
      customData: ''
    }
  }
})

const current: LabelSetCreateOrUpdateRequest = {
  meta: { name: 'Layout', description: '', tags: [] },
  labels: [label('one'), label('two')]
}

describe('buildLabelSetImportPreview', () => {
  it('previews legacy label sets and compares ordering', () => {
    const preview = buildLabelSetImportPreview(JSON.stringify({
      meta: current.meta,
      labels: [label('two'), label('one'), label('three')]
    }), { fileName: 'layout.json', current, existingNames: ['Layout'] })

    expect(preview.canImport).toBe(true)
    expect(preview.labelSets[0]).toMatchObject({
      name: 'Layout',
      labelCount: 3,
      nameConflict: true,
      comparison: { added: 1, removed: 0, changed: 0, orderChanged: true, metadataChanged: false }
    })
  })

  it('blocks invalid and duplicate label mappings', () => {
    const duplicate = label('two')
    duplicate.mapping.pageXml.textType = 'paragraph'
    const preview = buildLabelSetImportPreview(JSON.stringify({
      meta: { name: 'Broken' },
      labels: [label('one'), duplicate]
    }), { fileName: 'broken.json' })

    expect(preview.canImport).toBe(false)
    expect(preview.labelSets[0]?.issues.some(issue => issue.message.includes('duplicates the PAGE mapping'))).toBe(true)
  })

  it('blocks a default label reference that is not present', () => {
    const preview = buildLabelSetImportPreview(JSON.stringify({
      meta: { name: 'Broken default', defaultLabelId: 'missing' },
      labels: [label('one')]
    }), { fileName: 'broken-default.json' })

    expect(preview.canImport).toBe(false)
    expect(preview.labelSets[0]?.issues).toContainEqual(expect.objectContaining({
      level: 'error',
      message: expect.stringContaining('does not reference')
    }))
  })

  it('reports additional resources in toolkit packages', () => {
    const preview = buildLabelSetImportPreview(JSON.stringify({
      resources: [
        { type: 'LABEL_SET', name: 'Layout', payload: { meta: current.meta, labels: current.labels } },
        { type: 'CODEC', name: 'OCR', payload: { name: 'OCR', codec: [] } }
      ]
    }), { fileName: 'toolkit.json' })

    expect(preview.canImport).toBe(true)
    expect(preview.otherResources).toEqual([{ type: 'CODEC', name: 'OCR' }])
    expect(preview.issues).toContainEqual(expect.objectContaining({ level: 'warning' }))
  })

  it('warns when imported custom mapping fields will be normalized', () => {
    const imported = label('one')
    imported.mapping.pageXml.customKey = 'layout'
    imported.mapping.pageXml.customData = 'subclass:lead;'

    const preview = buildLabelSetImportPreview(JSON.stringify({
      meta: { name: 'Legacy custom mapping' },
      labels: [imported]
    }), { fileName: 'legacy.json' })

    expect(preview.canImport).toBe(true)
    expect(preview.labelSets[0]?.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ level: 'warning', message: expect.stringContaining('normalized to "structure"') }),
      expect.objectContaining({ level: 'warning', message: expect.stringContaining('will be discarded') })
    ]))
  })

  it('rejects malformed and unsupported JSON', () => {
    expect(buildLabelSetImportPreview('{', { fileName: 'bad.json' }).canImport).toBe(false)
    expect(buildLabelSetImportPreview('{}', { fileName: 'unknown.json' }).issues[0]?.message).toContain('not a supported')
  })
})
