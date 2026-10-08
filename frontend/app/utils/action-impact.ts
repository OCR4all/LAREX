import type { ActionRunConfiguration, ActionRunImpact, AnnotationLevel } from '@/types/action'

export const annotationLevelLabels: Record<AnnotationLevel, string> = {
  REGIONS: 'Regions', TEXT_LINES: 'Textlines', BASELINES: 'Baselines', TEXT: 'Text',
  WORDS: 'Words', GLYPHS: 'Glyphs', READING_ORDER: 'Reading order'
}

export function retainedImpactPages(impact: ActionRunImpact, excludedPageIds: string[]) {
  const excluded = new Set(excludedPageIds)
  return impact.pages.filter(page => !excluded.has(page.pageId))
}

/** Always submit explicit IDs: the API interprets an empty pageIds array as all pages. */
export function reviewedActionConfiguration(
  configuration: ActionRunConfiguration,
  impact: ActionRunImpact,
  excludedPageIds: string[]
): ActionRunConfiguration {
  const pages = retainedImpactPages(impact, excludedPageIds)
  if (!pages.length) throw new Error('Select at least one eligible page.')
  const pageIds = pages.map(page => page.pageId)
  const included = new Set(pageIds)
  const images = configuration.imageVariantSelection
  return {
    ...configuration,
    parameters: { ...configuration.parameters },
    pageIds,
    targetSelection: {
      type: impact.target,
      pages: pages.map(page => ({
        pageId: page.pageId,
        regionIds: [...page.targetSelection.regionIds],
        textLineIds: [...page.targetSelection.textLineIds]
      }))
    },
    imageVariantSelection: images
      ? {
          ...images,
          ...(images.pageVariants
            ? { pageVariants: Object.fromEntries(Object.entries(images.pageVariants).filter(([id]) => included.has(id))) }
            : {})
        }
      : null
  }
}
