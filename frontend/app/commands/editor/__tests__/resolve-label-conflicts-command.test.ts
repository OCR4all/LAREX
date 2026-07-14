import { describe, expect, it } from 'vitest'
import { Commander } from '../commander'
import { LabelDefinition, LabelSet } from '@/models/editor/labels'
import type { TextRegion } from '@/models/editor/region'
import { findRegionLabelConflicts } from '@/utils/editor/region-label-conflicts'
import { createRegionLabelConflictResolutionPlan } from '@/utils/editor/region-label-conflict-resolution'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion, findRegionById } from './test-utils'

function headingLabel(): LabelDefinition {
  return new LabelDefinition(
    'heading',
    'Heading',
    'region',
    '#cc0000',
    '',
    true,
    false,
    null,
    {
      pageXml: {
        regionType: 'TextRegion',
        textType: 'heading',
        customSubType: '',
        customKey: 'structure',
        customData: ''
      }
    }
  )
}

function imageLabel(): LabelDefinition {
  return new LabelDefinition(
    'image',
    'Image',
    'region',
    '#cc0000',
    '',
    false,
    false,
    null,
    {
      pageXml: {
        regionType: 'ImageRegion',
        textType: '',
        customSubType: 'photo',
        customKey: 'structure',
        customData: ''
      }
    }
  )
}

describe('region label conflict resolution command', () => {
  it('updates all grouped regions as one undoable history entry', () => {
    const doc = createTestDocument({
      regions: [
        createTestTextRegion({ id: 'r1', type: 'paragraph' }),
        createTestTextRegion({ id: 'r2', type: 'paragraph' })
      ]
    })
    const summary = findRegionLabelConflicts(doc.page.regions, new LabelSet('empty', 'Empty', []))
    const replacement = headingLabel()
    const plan = createRegionLabelConflictResolutionPlan(doc.page.regions, summary.groups, {
      [summary.groups[0]!.key]: replacement
    })
    const commander = new Commander()
    const { session, getDocument } = createMockSession(doc)
    const context = createTestContext(session)

    commander.execute(plan.command, context)

    expect(plan.affectedRegionCount).toBe(2)
    expect(commander.getState().totalCount).toBe(1)
    expect(findRegionById(getDocument()!.page.regions, 'r1')?.type).toBe('heading')
    expect(findRegionById(getDocument()!.page.regions, 'r2')?.type).toBe('heading')

    commander.undo(context)

    expect(findRegionById(getDocument()!.page.regions, 'r1')?.type).toBe('paragraph')
    expect(findRegionById(getDocument()!.page.regions, 'r2')?.type).toBe('paragraph')
  })

  it('counts destructive text-line loss before execution and restores it on undo', () => {
    const textLines = [
      { id: 'l1' },
      { id: 'l2' }
    ] as TextRegion['textLines']
    const doc = createTestDocument({
      regions: [createTestTextRegion({ id: 'r1', type: 'paragraph', textLines })]
    })
    const summary = findRegionLabelConflicts(doc.page.regions, new LabelSet('empty', 'Empty', []))
    const plan = createRegionLabelConflictResolutionPlan(doc.page.regions, summary.groups, {
      [summary.groups[0]!.key]: imageLabel()
    })
    const commander = new Commander()
    const { session, getDocument } = createMockSession(doc)
    const context = createTestContext(session)

    expect(plan.textLinesToRemove).toBe(2)

    commander.execute(plan.command, context)

    expect(findRegionById(getDocument()!.page.regions, 'r1')?.kind).toBe('ImageRegion')
    expect((findRegionById(getDocument()!.page.regions, 'r1') as TextRegion).textLines).toBeUndefined()

    commander.undo(context)

    expect(findRegionById(getDocument()!.page.regions, 'r1')?.kind).toBe('TextRegion')
    expect((findRegionById(getDocument()!.page.regions, 'r1') as TextRegion).textLines).toHaveLength(2)
  })
})
