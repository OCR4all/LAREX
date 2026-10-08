import { describe, expect, it } from 'vitest'
import { retainedImpactPages, reviewedActionConfiguration } from '../action-impact'
import type { ActionRunConfiguration, ActionRunImpact } from '@/types/action'

const configuration: ActionRunConfiguration = {
  processorDefinitionId: 'processor', pageIds: [], parameters: { model: 'test' }, targetSelection: null,
  imageVariantSelection: { mode: 'PER_PAGE', fallbackImage: false, pageVariants: { affected: 'a', safe: 'b', skipped: 'c' } }
}
const impact: ActionRunImpact = {
  target: 'REGION', skippedPages: [{ pageId: 'skipped', name: 'Skipped', reason: 'Missing input' }],
  pages: [
    { pageId: 'affected', name: 'Affected', affected: true, warningPrecision: 'DECLARED', affectedLevels: ['TEXT'],
      targetSelection: { pageId: 'affected', regionIds: ['r1'], textLineIds: [] } },
    { pageId: 'safe', name: 'Safe', affected: false, warningPrecision: 'DECLARED', affectedLevels: [],
      targetSelection: { pageId: 'safe', regionIds: ['r2'], textLineIds: [] } }
  ]
}

describe('reviewed Action scope', () => {
  it('keeps unaffected pages when every affected page is excluded', () => {
    const reviewed = reviewedActionConfiguration(configuration, impact, ['affected'])
    expect(reviewed.pageIds).toEqual(['safe'])
    expect(reviewed.targetSelection).toEqual({ type: 'REGION', pages: [impact.pages[1]!.targetSelection] })
    expect(reviewed.imageVariantSelection?.pageVariants).toEqual({ safe: 'b' })
    expect(configuration.pageIds).toEqual([])
    expect(configuration.imageVariantSelection?.pageVariants).toHaveProperty('affected')
  })

  it('never submits empty IDs interpreted as all pages', () => {
    expect(() => reviewedActionConfiguration(configuration, impact, ['affected', 'safe'])).toThrow('Select at least one')
    expect(() => reviewedActionConfiguration(configuration, { ...impact, pages: [] }, [])).toThrow()
  })

  it('captures explicit page scopes and clones targets for scheduling', () => {
    const reviewed = reviewedActionConfiguration(configuration, impact, [])
    expect(reviewed.pageIds).toEqual(['affected', 'safe'])
    expect(reviewed.targetSelection?.pages[0]?.regionIds).toEqual(['r1'])
    expect(reviewed.targetSelection?.pages[0]?.regionIds).not.toBe(impact.pages[0]?.targetSelection.regionIds)
    expect(reviewed.parameters).not.toBe(configuration.parameters)
    expect(reviewed.imageVariantSelection?.pageVariants).not.toHaveProperty('skipped')
  })

  it('preserves textline targets and global image settings', () => {
    const preview: ActionRunImpact = { ...impact, target: 'TEXT_LINE', pages: impact.pages.map(page => ({
      ...page, targetSelection: { ...page.targetSelection, regionIds: [], textLineIds: ['l1', 'l2'] }
    })) }
    const reviewed = reviewedActionConfiguration({ ...configuration, imageVariantSelection: { mode: 'GLOBAL', variant: 'original', fallbackImage: true } }, preview, ['safe'])
    expect(reviewed.targetSelection?.pages[0]?.textLineIds).toEqual(['l1', 'l2'])
    expect(reviewed.imageVariantSelection).toEqual({ mode: 'GLOBAL', variant: 'original', fallbackImage: true })
    expect(retainedImpactPages(preview, ['safe']).map(page => page.pageId)).toEqual(['affected'])
  })
})
