import { describe, expect, it } from 'vitest'
import { computed } from 'vue'
import { useEditorMetadataApply } from '../use-editor-metadata-apply'
import { createTextRegionMetadataFormState, textRegionMetadataSchema } from '@/utils/editor/metadata-schema'
import type { TextRegionMetadataFormState } from '@/utils/editor/metadata-schema'
import type { PcGts } from '@/models/editor/document'
import type { Page } from '@/models/editor/page'
import type { TextRegion } from '@/models/editor/region'

function setup(region: Partial<TextRegion>) {
  const textRegion = { id: 'r1', kind: 'TextRegion', ...region } as TextRegion
  const page = { regions: [textRegion] } as unknown as Page
  const { handleApplyMetadata } = useEditorMetadataApply({
    activeCanvasId: computed(() => null),
    activeDocument: computed(() => ({ metadata: {} }) as unknown as PcGts),
    activePage: computed(() => page),
    onReadingOrderUpdated: () => {}
  })
  const form = createTextRegionMetadataFormState(textRegion)
  function save(edit: (state: TextRegionMetadataFormState) => void) {
    edit(form)
    handleApplyMetadata({ target: 'textRegion', elementId: 'r1', data: textRegionMetadataSchema.parse(form) })
  }
  return { textRegion, save }
}

describe('saving the metadata form of a text region', () => {
  it('keeps empty and padded free-text values exactly as entered', () => {
    const { textRegion, save } = setup({})

    save((form) => {
      form.comments = ''
      form.custom = '  padded  '
    })

    expect(textRegion.comments).toBe('')
    expect(textRegion.custom).toBe('  padded  ')
  })

  it('removes a free-text attribute only when its field is unset', () => {
    const { textRegion, save } = setup({ comments: 'note', custom: '' })

    save((form) => {
      form.comments = undefined
    })

    expect(textRegion.comments).toBeUndefined()
    expect(textRegion.custom).toBe('')
  })

  it('leaves absent free-text attributes absent when another field is saved', () => {
    const { textRegion, save } = setup({ type: 'paragraph' })

    save((form) => {
      form.type = 'heading'
    })

    expect(textRegion.type).toBe('heading')
    expect(textRegion).not.toHaveProperty('comments', '')
    expect(textRegion.comments).toBeUndefined()
    expect(textRegion.custom).toBeUndefined()
  })

  it('drops label and user attribute rows that were added but never filled in', () => {
    const { textRegion, save } = setup({})

    save((form) => {
      form.labels = [{ externalModel: undefined, externalId: undefined, prefix: undefined, comments: undefined, labels: [
        { value: undefined, type: undefined, comments: undefined },
        { value: '', type: undefined, comments: undefined }
      ] }]
      form.userDefinedAttributes = [
        { name: undefined, description: undefined, type: undefined, value: undefined },
        { name: '  ', description: undefined, type: undefined, value: 'x' },
        { name: 'kept', description: undefined, type: 'xsd:string', value: '' }
      ]
    })

    expect(textRegion.labels).toEqual([{
      externalModel: undefined,
      externalId: undefined,
      prefix: undefined,
      comments: undefined,
      labels: [{ value: '', type: undefined, comments: undefined }]
    }])
    expect(textRegion.userDefined?.attributes).toEqual([
      { name: 'kept', description: undefined, type: 'xsd:string', value: '' }
    ])
  })
})
