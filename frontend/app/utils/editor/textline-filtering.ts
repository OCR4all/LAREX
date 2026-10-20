export type PresenceFilter = 'all' | 'with' | 'without'

export interface TextlineFilters {
  gt: PresenceFilter
  comments: PresenceFilter
  diff: PresenceFilter
}

interface FilterableTextline {
  hasGtVariant: boolean
  comments?: string
  textContentVariants: Array<{ index?: number, text: string }>
}

function matchesPresence(value: boolean, filter: PresenceFilter): boolean {
  return filter === 'all' || value === (filter === 'with')
}

export function matchesTextlineFilters(line: FilterableTextline, filters: TextlineFilters, gtIndex: number): boolean {
  if (!matchesPresence(line.hasGtVariant, filters.gt)) return false
  if (!matchesPresence(Boolean(line.comments?.trim()), filters.comments)) return false

  if (filters.diff !== 'all') {
    const gtText = line.textContentVariants.find(variant => variant.index === gtIndex)?.text ?? ''
    const hasMismatch = line.textContentVariants.some(variant => variant.index !== gtIndex && variant.text !== gtText)
    if (!matchesPresence(hasMismatch, filters.diff)) return false
  }

  return true
}
